package io.github.wishday.contacts

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import io.github.wishday.data.Person

/** Reads birthdays and phone numbers from the device's contacts. Requires READ_CONTACTS. */
class ContactImporter(private val context: Context) {

    fun readBirthdays(): List<Person> {
        val resolver = context.contentResolver
        val byContact = linkedMapOf<Long, Person>()

        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.LOOKUP_KEY, Data.DISPLAY_NAME, Event.START_DATE),
            "${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
            arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getLong(0)
                // The same contact can have a birthday row per synced account; keep the first.
                if (contactId in byContact) continue
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val date = parseContactDate(c.getString(3)) ?: continue
                byContact[contactId] = Person(
                    name = name,
                    month = date.month,
                    day = date.day,
                    year = date.year,
                    contactLookupKey = c.getString(1),
                )
            }
        }

        if (byContact.isEmpty()) return emptyList()
        val phones = readPhones(byContact.keys)
        return byContact.map { (id, person) -> person.copy(phone = phones[id]) }
    }

    /** Every contact that has a phone number, with their best number. */
    fun readPhoneContacts(): List<PhoneContact> {
        val names = mutableMapOf<Long, Pair<String, String>>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(1) ?: continue
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                names.putIfAbsent(c.getLong(0), key to name)
            }
        }
        val phones = readPhones(names.keys)
        return names.mapNotNull { (id, keyAndName) ->
            phones[id]?.let { PhoneContact(keyAndName.first, keyAndName.second, it) }
        }
    }

    /** Best phone per contact: super-primary, then mobile, then anything. */
    private fun readPhones(contactIds: Set<Long>): Map<Long, String> {
        val best = mutableMapOf<Long, Pair<Int, String>>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, Phone.IS_SUPER_PRIMARY),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in contactIds) continue
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val rank = when {
                    c.getInt(3) != 0 -> 0
                    c.getInt(2) == Phone.TYPE_MOBILE -> 1
                    else -> 2
                }
                if (best[id]?.first?.let { rank < it } != false) best[id] = rank to number
            }
        }
        return best.mapValues { it.value.second }
    }
}

data class ContactDate(val month: Int, val day: Int, val year: Int?)

private val FULL_DATE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
private val NO_YEAR = Regex("""^--(\d{1,2})-(\d{1,2})$""")
private val COMPACT = Regex("""^(\d{4})(\d{2})(\d{2})$""")

/**
 * Parses the formats contact providers commonly store: `1990-05-17`, `--05-17` (no year),
 * `19900517`, and full timestamps like `1990-05-17T00:00:00Z`.
 */
fun parseContactDate(raw: String?): ContactDate? {
    val s = raw?.trim().orEmpty()
    val (y, m, d) = FULL_DATE.find(s)?.destructured?.let { (y, m, d) -> Triple(y.toInt(), m, d) }
        ?: COMPACT.matchEntire(s)?.destructured?.let { (y, m, d) -> Triple(y.toInt(), m, d) }
        ?: NO_YEAR.matchEntire(s)?.destructured?.let { (m, d) -> Triple(null, m, d) }
        ?: return null
    val month = m.toInt()
    val day = d.toInt()
    if (month !in 1..12 || day !in 1..31) return null
    // Some providers use a placeholder year (e.g. 1604) when the year is unknown.
    val year = y?.takeIf { it > 1800 }
    return ContactDate(month, day, year)
}
