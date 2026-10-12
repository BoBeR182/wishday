package io.github.wishday.backup

import io.github.wishday.contacts.parseContactDate
import io.github.wishday.data.Channel
import io.github.wishday.data.Person

/**
 * Plain CSV export/import, so birthdays can be backed up, moved to a new phone,
 * edited in a spreadsheet, or brought in from another app.
 *
 * Columns: name, birthday (`2001-10-12`, or `--10-12` without a year), phone, channel, message,
 * important (`yes`/blank), gift_reminder_days, notes.
 * Only name and birthday are required on import; columns are matched by header, in any order.
 */
object BirthdayCsv {

    private val HEADER = listOf("name", "birthday", "phone", "channel", "message", "important", "gift_reminder_days", "notes")

    private val NAME_HEADERS = setOf("name", "full name", "fullname", "display name")
    private val DATE_HEADERS = setOf("birthday", "birthdate", "date", "date of birth", "dob")
    private val PHONE_HEADERS = setOf("phone", "phone number", "number", "mobile")

    fun write(people: List<Person>): String = buildString {
        appendRow(HEADER)
        for (p in people.sortedWith(compareBy({ it.month }, { it.day }, { it.name.lowercase() }))) {
            val date = "%02d-%02d".format(p.month, p.day)
            appendRow(
                listOf(
                    p.name,
                    p.year?.let { "%04d-$date".format(it) } ?: "--$date",
                    p.phone.orEmpty(),
                    p.channel.name.lowercase(),
                    p.customMessage.orEmpty(),
                    if (p.favorite) "yes" else "",
                    p.giftReminderDays?.toString().orEmpty(),
                    p.notes.orEmpty(),
                ),
            )
        }
    }

    /** Parses [text]; rows without a usable name or date are counted in [Result.skipped]. */
    fun read(text: String): Result {
        val rows = parseRows(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return Result(emptyList(), 0)

        val header = rows.first().map { it.trim().lowercase() }
        fun column(names: Set<String>) = header.indexOfFirst { it in names }
        val nameCol = column(NAME_HEADERS)
        val dateCol = column(DATE_HEADERS)
        // No recognisable header: assume our own column order.
        val hasHeader = nameCol >= 0 && dateCol >= 0
        val cols = if (hasHeader) {
            Columns(
                nameCol, dateCol, column(PHONE_HEADERS), column(setOf("channel")), column(setOf("message")),
                column(setOf("important", "favorite", "starred")), column(setOf("gift_reminder_days")),
                column(setOf("notes", "note", "gift ideas")),
            )
        } else {
            Columns(0, 1, 2, 3, 4, 5, 6, 7)
        }

        val people = mutableListOf<Person>()
        var skipped = 0
        for (row in if (hasHeader) rows.drop(1) else rows) {
            val person = cols.toPerson(row)
            if (person == null) skipped++ else people += person
        }
        return Result(people, skipped)
    }

    data class Result(val people: List<Person>, val skipped: Int)

    private class Columns(
        val name: Int, val date: Int, val phone: Int, val channel: Int, val message: Int,
        val important: Int, val giftDays: Int, val notes: Int,
    ) {
        fun toPerson(row: List<String>): Person? {
            fun cell(i: Int) = row.getOrNull(i)?.trim().orEmpty()
            val name = cell(name).ifBlank { return null }
            val date = parseContactDate(cell(date)) ?: return null
            val phone = cell(phone).ifBlank { null }
            val channel = Channel.entries.firstOrNull { it.name.equals(cell(channel), ignoreCase = true) }
                // A channel that needs a number falls back to a reminder without one.
                ?.takeIf { it == Channel.NONE || phone != null }
                ?: Channel.NONE
            return Person(
                name = name,
                phone = phone,
                month = date.month,
                day = date.day,
                year = date.year,
                channel = channel,
                customMessage = cell(message).ifBlank { null },
                favorite = cell(important).lowercase() in setOf("yes", "true", "1", "y", "x"),
                giftReminderDays = cell(giftDays).toIntOrNull()?.takeIf { it in 1..365 },
                notes = cell(notes).ifBlank { null },
            )
        }
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, ",") { cell ->
            if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + cell.replace("\"", "\"\"") + "\""
            } else {
                cell
            }
        }
        append("\r\n")
    }

    /** RFC 4180 parsing: quoted cells may contain commas, quotes ("") and line breaks. */
    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                quoted -> cell.append(c)
                c == ',' -> { row += cell.toString(); cell.clear() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.clear()
                    rows += row; row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}
