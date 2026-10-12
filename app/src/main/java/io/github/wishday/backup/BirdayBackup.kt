package io.github.wishday.backup

import android.database.sqlite.SQLiteDatabase
import io.github.wishday.data.Person
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Reads backups made by Birday (https://github.com/m-i-n-a-r/birday): its Room database file,
 * exported as e.g. `BirdayBackup_2026-10-12`. Only birthdays are imported; Birday's other
 * event types (anniversaries, name days, ...) are counted as skipped.
 */
object BirdayBackup {

    /** One row of Birday's `Event` table, reduced to the columns Wishday uses. */
    data class Event(
        val type: String?,
        val name: String,
        val surname: String?,
        /** Birday stores a placeholder year when this is false. */
        val yearMatters: Boolean,
        /** `yyyy-MM-dd`. */
        val originalDate: String,
        val favorite: Boolean = false,
        val notes: String? = null,
    )

    /** True when [header] (the first bytes of a file) is a SQLite database rather than text. */
    fun looksLikeBackup(header: ByteArray): Boolean =
        header.size >= SQLITE_MAGIC.size && SQLITE_MAGIC.indices.all { header[it] == SQLITE_MAGIC[it] }

    fun toPerson(event: Event): Person? {
        if (event.type != null && event.type != "BIRTHDAY") return null
        val date = try {
            LocalDate.parse(event.originalDate.trim())
        } catch (_: DateTimeParseException) {
            return null
        }
        val name = listOf(event.name, event.surname.orEmpty())
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            .ifEmpty { return null }
        return Person(
            name = name,
            month = date.monthValue,
            day = date.dayOfMonth,
            year = date.year.takeIf { event.yearMatters },
            favorite = event.favorite,
            notes = event.notes?.trim()?.ifEmpty { null },
        )
    }

    /** Copies [input] to [scratch] (SQLite needs a real file) and reads its birthdays. */
    fun read(input: InputStream, scratch: File): BirthdayCsv.Result {
        scratch.outputStream().use { input.copyTo(it) }
        try {
            val events = SQLiteDatabase.openDatabase(scratch.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // favorite and notes only exist in newer Birday versions.
                val columns = db.rawQuery("SELECT * FROM Event LIMIT 0", null).use { it.columnNames.toSet() }
                val extra = listOf("favorite", "notes").joinToString("") { if (it in columns) ", $it" else ", NULL" }
                db.rawQuery("SELECT type, name, surname, yearMatter, originalDate$extra FROM Event", null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(
                                Event(
                                    type = c.getString(0),
                                    name = c.getString(1).orEmpty(),
                                    surname = c.getString(2),
                                    yearMatters = !c.isNull(3) && c.getInt(3) != 0,
                                    originalDate = c.getString(4).orEmpty(),
                                    favorite = !c.isNull(5) && c.getInt(5) != 0,
                                    notes = c.getString(6),
                                ),
                            )
                        }
                    }
                }
            }
            val people = events.mapNotNull(::toPerson)
            return BirthdayCsv.Result(people, skipped = events.size - people.size)
        } finally {
            scratch.delete()
        }
    }

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
}
