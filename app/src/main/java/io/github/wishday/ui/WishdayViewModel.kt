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
import io.github.wishday.data.AppDatabase
import io.github.wishday.data.Person
import io.github.wishday.data.Settings
import io.github.wishday.messaging.SignalLauncher
import io.github.wishday.messaging.SmsSender
import io.github.wishday.work.BirthdayWorker
import kotlinx.coroutines.Dispatchers
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

    /** Everyone, soonest birthday first. */
    val people: StateFlow<List<Person>> = dao.observeAll()
        .map { Birthdays.upcoming(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): Person? = dao.get(id)

    fun save(person: Person) = viewModelScope.launch { saveNow(person) }

    /** Saves [person] and returns their id (new or existing). */
    suspend fun saveNow(person: Person): Long {
        val id = dao.upsert(person).takeIf { it > 0 } ?: person.id
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
     * Adds people from a CSV file or a Birday backup (detected from the file's contents),
     * skipping anyone already saved with the same name and birthday.
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
        val seen = dao.getAll().mapTo(mutableSetOf()) { it.identity() }
        val fresh = parsed.people.filter { seen.add(it.identity()) }
        // No catch-up run here: restoring a backup on a new phone shouldn't text someone already greeted today.
        fresh.forEach { dao.upsert(it) }
        ImportResult(added = fresh.size, duplicates = parsed.people.size - fresh.size, invalid = parsed.skipped)
    }

    data class ImportResult(val added: Int, val duplicates: Int, val invalid: Int)

    private fun Person.identity() = Triple(name.trim().lowercase(), month, day)

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

    data class ContactWriteResult(val added: Int, val alreadyHad: Int, val notWritten: Int)

    /** Adds birthdays to the linked contacts of [people] (everyone linked when null). Requires WRITE_CONTACTS. */
    suspend fun writeBirthdaysToContacts(people: List<Person>? = null): ContactWriteResult = withContext(Dispatchers.IO) {
        val writer = ContactWriter(getApplication())
        val outcomes = (people ?: dao.getAll()).mapNotNull { p -> p.contactLookupKey?.let { writer.addBirthday(it, p) } }
        ContactWriteResult(
            added = outcomes.count { it == ContactWriter.Outcome.ADDED },
            alreadyHad = outcomes.count { it == ContactWriter.Outcome.ALREADY_HAS_BIRTHDAY },
            notWritten = outcomes.count { it == ContactWriter.Outcome.NOT_FOUND || it == ContactWriter.Outcome.READ_ONLY },
        )
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
        settings.defaultMessage = message.ifBlank { Settings.DEFAULT_MESSAGE }
        settings.sendHour = hour
        settings.sendMinute = minute
        settings.remindDaysBefore = daysBefore
        BirthdayWorker.schedule(getApplication())
    }
}
