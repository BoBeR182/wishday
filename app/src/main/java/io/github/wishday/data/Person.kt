package io.github.wishday.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** How a birthday greeting is delivered. */
enum class Channel {
    /** Only remind me; I'll reach out myself. */
    NONE,

    /** Sent automatically by the app over SMS. */
    SMS,

    /** Signal opens with the message prefilled; one tap to send. */
    SIGNAL,

    /** WhatsApp opens with the message prefilled; one tap to send. */
    WHATSAPP,
}

@Entity(
    tableName = "people",
    indices = [Index(value = ["contactLookupKey"], unique = true)],
)
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val month: Int,
    val day: Int,
    /** Birth year, if known. Used only to show the age being turned. */
    val year: Int? = null,
    val channel: Channel = Channel.NONE,
    /** Per-person template; falls back to the default template when null. */
    val customMessage: String? = null,
    /** Set when imported from the device's contacts, to avoid duplicates on re-import. */
    val contactLookupKey: String? = null,
    /** Last year a greeting was sent (or prompted), so we never double-send. */
    val lastGreetedYear: Int? = null,
    /** Starred as important; shown first in ties and marked in the list. */
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** An extra, earlier reminder this many days ahead (e.g. 14, to get a gift); null for none. */
    val giftReminderDays: Int? = null,
    /** Gift ideas and other notes, shown in the reminders. */
    val notes: String? = null,
)
