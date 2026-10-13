package io.github.wishday.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.wishday.backup.BirdayBackup
import io.github.wishday.backup.BirthdayCsv
import io.github.wishday.birthday.Birthdays
import io.github.wishday.contacts.ContactImporter
import io.github.wishday.contacts.ContactMatch
import io.github.wishday.contacts.ContactMatcher
import io.github.wishday.contacts.ContactWriter
import io.github.wishday.contacts.PhoneContact
import io.github.wishday.data.AppDatabase
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import io.github.wishday.data.Settings
import io.github.wishday.messaging.SignalLauncher
import io.github.wishday.messaging.SmsSender
import io.github.wishday.messaging.WhatsAppLauncher
import io.github.wishday.ui.theme.DarkMode
import io.github.wishday.work.BirthdayWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class WishdayViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).people()
    val settings = Settings(app)

    data class Appearance(val themeId: String, val darkMode: DarkMode)

    private val _appearance = MutableStateFlow(
        Appearance(settings.themeId, runCatching { DarkMode.valueOf(settings.darkMode) }.getOrDefault(DarkMode.SYSTEM)),
    )
    val appearance: StateFlow<Appearance> = _appearance

    fun setTheme(id: String) {
        settings.themeId = id
        _appearance.value = _appearance.value.copy(themeId = id)
    }

    fun setDarkMode(mode: DarkMode) {
        settings.darkMode = mode.name
        _appearance.value = _appearance.value.copy(darkMode = mode)
    }

    /** Everyone, soonest birthday first. */
    val people: StateFlow<List<Person>> = dao.observeAll()
        .map { Birthdays.upcoming(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): Person? = dao.get(id)

    fun save(person: Person) = viewModelScope.launch { saveNow(person) }

    /** Saves [person] and returns their id (new or existing). */
    suspend fun saveNow(person: Person): Long {
        // One contact, one person: drop a link someone else already holds.
        val linkTaken = person.contactLookupKey != null &&
            dao.getAll().any { it.contactLookupKey == person.contactLookupKey && it.id != person.id }
        val toSave = if (linkTaken) person.copy(contactLookupKey = null) else person
        val id = dao.upsert(toSave).takeIf { it > 0 } ?: person.id
        val today = LocalDate.now()
        if (Birthdays.isToday(person, today) && person.lastGreetedYear != today.year) {
            BirthdayWorker.catchUpIfDue(getApplication())
        }
        return id
    }

    fun delete(person: Person) = viewModelScope.launch { dao.delete(person) }

    /** Imports contacts with birthdays; returns how many were new. */
    suspend fun importContacts(): Int = withContext(Dispatchers.IO) {
        val found = ContactImporter(getApplication()).readBirthdays()
        dao.insertIgnoringExisting(found).count { it != -1L }
    }

    /** Writes everyone to [uri] as CSV; returns how many were exported. */
    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val people = dao.getAll()
        val out = checkNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")) {
            "Couldn't open the file"
        }
        out.bufferedWriter().use { it.write(BirthdayCsv.write(people)) }
        people.size
    }

    /**
     * Adds people from a CSV file or a Birday backup (detected from the file's contents). Anyone
     * already saved with the same name and birthday (e.g. just imported from contacts) gets the
     * backup's settings filled into whatever they're missing, rather than being skipped.
     */
    suspend fun importFile(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val bytes = checkNotNull(app.contentResolver.openInputStream(uri)) { "Couldn't open the file" }
            .use { it.readBytes() }
        val parsed = if (BirdayBackup.looksLikeBackup(bytes)) {
            BirdayBackup.read(bytes.inputStream(), File(app.cacheDir, "birday-import.db"))
        } else {
            BirthdayCsv.read(bytes.toString(Charsets.UTF_8))
        }
        val saved = dao.getAll().associateByTo(mutableMapOf()) { it.identity() }
        var added = 0
        var updated = 0
        val wantSms = mutableListOf<Person>()
        // No catch-up run here: restoring a backup on a new phone shouldn't text someone already greeted today.
        for (person in parsed.people) {
            val existing = saved[person.identity()]
            var merged = existing?.fillFrom(person) ?: person
            // A file can't switch on automatic texts by itself: they stay reminders until confirmed.
            val armsSms = merged.channel == Channel.SMS && existing?.channel != Channel.SMS
            if (armsSms) merged = merged.copy(channel = Channel.NONE)
            if (merged == existing) continue
            val id = dao.upsert(merged).takeIf { it > 0 } ?: merged.id
            saved[person.identity()] = merged.copy(id = id)
            if (armsSms) wantSms += merged.copy(id = id)
            if (existing == null) added++ else updated++
        }
        ImportResult(
            added = added,
            updated = updated,
            duplicates = parsed.people.size - added - updated,
            invalid = parsed.skipped,
            wantSms = wantSms,
        )
    }

    /** [wantSms]: people the file set to be texted automatically, saved as reminders until confirmed. */
    data class ImportResult(
        val added: Int,
        val updated: Int,
        val duplicates: Int,
        val invalid: Int,
        val wantSms: List<Person> = emptyList(),
    )

    /** Turns automatic SMS on for [people] (after the user confirmed an import's request). */
    suspend fun enableSms(people: List<Person>) = withContext(Dispatchers.IO) {
        people.forEach { p -> dao.get(p.id)?.let { dao.upsert(it.copy(channel = Channel.SMS)) } }
    }

    /** Keeps everything already set here, taking only what's missing from [backup]. */
    private fun Person.fillFrom(backup: Person) = copy(
        phone = phone ?: backup.phone,
        year = year ?: backup.year,
        channel = if (channel == Channel.NONE) backup.channel else channel,
        customMessage = customMessage ?: backup.customMessage,
        contactLookupKey = contactLookupKey ?: backup.contactLookupKey,
        lastGreetedYear = listOfNotNull(lastGreetedYear, backup.lastGreetedYear).maxOrNull(),
        favorite = favorite || backup.favorite,
        giftReminderDays = giftReminderDays ?: backup.giftReminderDays,
        notes = notes ?: backup.notes,
    )

    private fun Person.identity() = Triple(name.trim().lowercase(), month, day)

    /** Every contact with a phone number, for the contact picker. Requires READ_CONTACTS. */
    suspend fun loadPhoneContacts(): List<PhoneContact> = withContext(Dispatchers.IO) {
        ContactImporter(getApplication()).readPhoneContacts()
    }

    /** People without a number who have exactly one same-named contact. Requires READ_CONTACTS. */
    suspend fun findContactMatches(): List<ContactMatch> = withContext(Dispatchers.IO) {
        ContactMatcher.match(dao.getAll(), ContactImporter(getApplication()).readPhoneContacts())
    }

    /** Copies each matched contact's number onto the person and links them to the contact. */
    suspend fun applyContactMatches(matches: List<ContactMatch>): List<Person> = withContext(Dispatchers.IO) {
        matches.map { (person, contact) ->
            person.copy(phone = contact.phone, contactLookupKey = contact.lookupKey).also { dao.upsert(it) }
        }
    }

    data class ContactWriteResult(
        val added: Int,
        val alreadyHad: Int,
        val notWritten: Int,
        /** People with no contact to write to, for offering to create one. */
        val unlinked: List<UnlinkedPerson> = emptyList(),
    )

    /**
     * Someone with no contact. [duplicateOf]: another person here is linked to the contact with this
     * number, so they're probably the same person. [likelyContact]: a contact with a similar name.
     */
    data class UnlinkedPerson(val person: Person, val duplicateOf: Person? = null, val likelyContact: PhoneContact? = null)

    /**
     * Adds birthdays to the linked contacts of [people] (everyone when null). People with a number
     * but no link are first linked to the one contact with that number, if there is one.
     * Requires READ_CONTACTS and WRITE_CONTACTS.
     */
    suspend fun writeBirthdaysToContacts(people: List<Person>? = null): ContactWriteResult = withContext(Dispatchers.IO) {
        val writer = ContactWriter(getApplication())
        val all = dao.getAll()
        val taken = all.mapNotNullTo(mutableSetOf()) { it.contactLookupKey }
        val linked = (people ?: all).map { p ->
            if (p.contactLookupKey != null || p.phone.isNullOrBlank()) return@map p
            val key = writer.findByPhone(p.phone)?.takeIf { taken.add(it) } ?: return@map p
            p.copy(contactLookupKey = key).also { dao.upsert(it) }
        }
        val outcomes = linked.mapNotNull { p -> p.contactLookupKey?.let { writer.addBirthday(it, p) } }
        val unlinked = if (people != null) emptyList() else {
            val contacts = ContactImporter(getApplication()).readPhoneContacts().filter { it.lookupKey !in taken }
            val byKey = all.filter { it.contactLookupKey != null }.associateBy { it.contactLookupKey }
            linked.filter { it.contactLookupKey == null }.map { p ->
                val duplicate = p.phone?.takeIf { it.isNotBlank() }?.let(writer::findByPhone)?.let(byKey::get)
                UnlinkedPerson(p, duplicate, if (duplicate == null) ContactMatcher.likely(p.name, contacts) else null)
            }
        }
        ContactWriteResult(
            added = outcomes.count { it == ContactWriter.Outcome.ADDED },
            alreadyHad = outcomes.count { it == ContactWriter.Outcome.ALREADY_HAS_BIRTHDAY },
            notWritten = outcomes.count { it == ContactWriter.Outcome.NOT_FOUND || it == ContactWriter.Outcome.READ_ONLY },
            unlinked = unlinked,
        )
    }

    /** Links [person] to [contact] (taking its number if they have none) and adds the birthday to it. */
    suspend fun linkToContact(person: Person, contact: PhoneContact): Boolean = withContext(Dispatchers.IO) {
        if (dao.getAll().any { it.contactLookupKey == contact.lookupKey && it.id != person.id }) return@withContext false
        val linked = person.copy(contactLookupKey = contact.lookupKey, phone = person.phone ?: contact.phone)
        dao.upsert(linked)
        ContactWriter(getApplication()).addBirthday(contact.lookupKey, linked)
        true
    }

    /** Folds [duplicate]'s settings into [keep] (filling only what [keep] lacks) and deletes [duplicate]. */
    suspend fun merge(duplicate: Person, keep: Person) = withContext(Dispatchers.IO) {
        dao.upsert(keep.fillFrom(duplicate))
        dao.delete(duplicate)
    }

    /** Creates a contact for each of [people] and links them to it; returns how many were created. */
    suspend fun createContacts(people: List<Person>): Int = withContext(Dispatchers.IO) {
        val writer = ContactWriter(getApplication())
        people.count { p ->
            val key = runCatching { writer.createContact(p) }.getOrNull() ?: return@count false
            dao.upsert(p.copy(contactLookupKey = key))
            true
        }
    }

    fun messageFor(person: Person): String =
        Birthdays.render(person.customMessage ?: settings.defaultMessage, person, LocalDate.now())

    /** Queues the SMS; whether it went out is reported by notification. */
    fun sendSmsNow(person: Person): Result<Unit> =
        SmsSender(getApplication()).send(person, messageFor(person))
            .onSuccess { markGreetedIfToday(person) }

    /**
     * For the home screen's quick buttons: [intent] is built from today's message, and opening
     * a message counts as greeting them, like the Signal button.
     */
    fun quickMessage(person: Person, intent: (message: String) -> Intent): Intent {
        markGreetedIfToday(person)
        return intent(messageFor(person))
    }

    fun whatsAppIntent(person: Person): Intent {
        markGreetedIfToday(person)
        return WhatsAppLauncher.intentFor(getApplication(), person.phone, messageFor(person))
    }

    fun signalIntent(person: Person): Intent {
        markGreetedIfToday(person)
        return SignalLauncher.intentFor(getApplication(), person.phone, messageFor(person))
    }

    /** A manual send on the day counts, so the scheduled run won't greet them twice. */
    private fun markGreetedIfToday(person: Person) {
        val today = LocalDate.now()
        if (Birthdays.isToday(person, today)) {
            viewModelScope.launch { dao.markGreeted(person.id, today.year) }
        }
    }

    fun updateSettings(message: String, hour: Int, minute: Int, daysBefore: Int) {
        settings.defaultMessage = message
        settings.sendHour = hour
        settings.sendMinute = minute
        settings.remindDaysBefore = daysBefore
        BirthdayWorker.schedule(getApplication())
    }
}
