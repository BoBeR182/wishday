package io.github.wishday.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import io.github.wishday.data.Person

/** Adds birthdays to the device's contacts. Requires READ_CONTACTS and WRITE_CONTACTS. */
class ContactWriter(context: Context) {

    enum class Outcome { ADDED, ALREADY_HAS_BIRTHDAY, NOT_FOUND, READ_ONLY }

    private val resolver: ContentResolver = context.contentResolver

    /** Account types whose contacts are synced down only (Signal, WhatsApp, ...); edits there get wiped. */
    private val readOnlyAccountTypes: Set<String> by lazy {
        ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && !it.supportsUploading() }
            .mapTo(mutableSetOf()) { it.accountType }
    }

    /** Adds [person]'s birthday to the contact with [lookupKey], unless it already has one. */
    fun addBirthday(lookupKey: String, person: Person): Outcome {
        val contactUri = Contacts.lookupContact(resolver, Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey))
            ?: return Outcome.NOT_FOUND
        val contactId = ContentUris.parseId(contactUri)
        if (hasBirthday(contactId)) return Outcome.ALREADY_HAS_BIRTHDAY
        val rawId = writableRawContact(contactId) ?: return Outcome.READ_ONLY

        val values = ContentValues().apply {
            put(Data.RAW_CONTACT_ID, rawId)
            put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
            put(Event.TYPE, Event.TYPE_BIRTHDAY)
            put(Event.START_DATE, startDate(person))
        }
        return if (resolver.insert(Data.CONTENT_URI, values) != null) Outcome.ADDED else Outcome.NOT_FOUND
    }

    /** The one contact that has [number], or null when none or several do. */
    fun findByPhone(number: String): String? =
        resolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(PhoneLookup.LOOKUP_KEY),
            null,
            null,
            null,
        )?.use { c ->
            val keys = mutableSetOf<String>()
            while (c.moveToNext()) c.getString(0)?.let { keys += it }
            keys.singleOrNull()
        }

    /** Creates a phone contact with [person]'s name, number and birthday; returns its lookup key. */
    fun createContact(person: Person): String? {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build(),
            dataRow(StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, person.name).build(),
            dataRow(Event.CONTENT_ITEM_TYPE)
                .withValue(Event.TYPE, Event.TYPE_BIRTHDAY)
                .withValue(Event.START_DATE, startDate(person))
                .build(),
        )
        person.phone?.takeIf { it.isNotBlank() }?.let {
            ops += dataRow(Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, it).withValue(Phone.TYPE, Phone.TYPE_MOBILE).build()
        }
        val rawUri = resolver.applyBatch(ContactsContract.AUTHORITY, ops).firstOrNull()?.uri ?: return null
        return resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.LOOKUP_KEY),
            "${Data.RAW_CONTACT_ID} = ?",
            arrayOf(ContentUris.parseId(rawUri).toString()),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun dataRow(mimeType: String) =
        ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, mimeType)

    private fun hasBirthday(contactId: Long): Boolean =
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID),
            "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
            arrayOf(contactId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
            null,
        )?.use { it.count > 0 } ?: false

    /** A raw contact we can safely edit: a synced account that uploads changes, or a phone-only contact. */
    private fun writableRawContact(contactId: Long): Long? =
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            var found: Long? = null
            while (found == null && c.moveToNext()) {
                if (c.getString(1) !in readOnlyAccountTypes) found = c.getLong(0)
            }
            found
        }

    companion object {
        /** The format contacts apps use: `1990-05-17`, or `--05-17` without a year. */
        fun startDate(person: Person): String {
            val monthDay = "%02d-%02d".format(person.month, person.day)
            return person.year?.let { "%04d-%s".format(it, monthDay) } ?: "--$monthDay"
        }
    }
}
