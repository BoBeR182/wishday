package io.github.wishday.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import io.github.wishday.data.Person
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A formatter for [skeleton] (e.g. "MMMd") in the order and wording of the device's locale. */
@Composable
fun localDateFormatter(skeleton: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, skeleton) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

/** Someone's birthday as a short month and day, e.g. "Oct 14" or "14. Okt.". */
@Composable
fun shortDate(person: Person): String =
    // A leap year, so Feb 29 formats too; the year isn't shown.
    LocalDate.of(2000, person.month, person.day).format(localDateFormatter("MMMd"))
