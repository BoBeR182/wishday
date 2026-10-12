package io.github.wishday.birthday

import io.github.wishday.R
import androidx.annotation.StringRes
import io.github.wishday.data.Person
import java.time.LocalDate

/** Numbers for the stats screen, worked out from everyone's birthdays. */
data class BirthdayStats(
    val total: Int,
    /** Index 0 is January. */
    val byMonth: List<Int>,
    /** 1-based months tied for the most birthdays; empty when there are none. */
    val busiestMonths: List<Int>,
    val thisMonth: Int,
    val next30Days: Int,
    /** Average age people turn at their next birthday, over those with a known year. */
    val averageAge: Double?,
    val youngest: Person?,
    val oldest: Person?,
    /** People turning a round number (18, 21, 30, 40, ...) at their next birthday, soonest first. */
    val milestones: List<Pair<Person, Int>>,
    /** Groups of people who share a birthday. */
    val twins: List<List<Person>>,
    /** Signs with at least one person, most common first. */
    val zodiac: List<Pair<Zodiac, Int>>,
    val important: Int,
    val withGiftReminder: Int,
) {
    companion object {
        fun of(people: List<Person>, today: LocalDate): BirthdayStats {
            val byMonth = List(12) { m -> people.count { it.month == m + 1 } }
            val most = byMonth.max().takeIf { it > 0 }
            val withYear = people.filter { it.year != null }
            val ages = withYear.associateWith { Birthdays.ageTurning(it, today)!! }
            return BirthdayStats(
                total = people.size,
                byMonth = byMonth,
                busiestMonths = if (most == null) emptyList() else byMonth.indices.filter { byMonth[it] == most }.map { it + 1 },
                thisMonth = byMonth[today.monthValue - 1],
                next30Days = people.count { Birthdays.daysUntil(it, today) <= 30 },
                averageAge = ages.values.takeIf { it.isNotEmpty() }?.average(),
                youngest = withYear.maxWithOrNull(compareBy({ it.year }, { it.month }, { it.day })),
                oldest = withYear.minWithOrNull(compareBy({ it.year }, { it.month }, { it.day })),
                milestones = ages.filterValues(::isMilestone).toList()
                    .sortedBy { (person, _) -> Birthdays.daysUntil(person, today) },
                twins = people.groupBy { it.month to it.day }.values.filter { it.size > 1 }
                    .sortedWith(compareBy({ it.first().month }, { it.first().day })),
                zodiac = people.groupingBy { Zodiac.of(it.month, it.day) }.eachCount().toList()
                    .sortedWith(compareByDescending<Pair<Zodiac, Int>> { it.second }.thenBy { it.first.ordinal }),
                important = people.count { it.favorite },
                withGiftReminder = people.count { it.giftReminderDays != null },
            )
        }

        fun isMilestone(age: Int) = age == 18 || age == 21 || (age >= 10 && age % 10 == 0)
    }
}

enum class Zodiac(val symbol: String, @StringRes val label: Int, private val startMonth: Int, private val startDay: Int) {
    CAPRICORN("♑", R.string.zodiac_capricorn, 12, 22),
    AQUARIUS("♒", R.string.zodiac_aquarius, 1, 20),
    PISCES("♓", R.string.zodiac_pisces, 2, 19),
    ARIES("♈", R.string.zodiac_aries, 3, 21),
    TAURUS("♉", R.string.zodiac_taurus, 4, 20),
    GEMINI("♊", R.string.zodiac_gemini, 5, 21),
    CANCER("♋", R.string.zodiac_cancer, 6, 21),
    LEO("♌", R.string.zodiac_leo, 7, 23),
    VIRGO("♍", R.string.zodiac_virgo, 8, 23),
    LIBRA("♎", R.string.zodiac_libra, 9, 23),
    SCORPIO("♏", R.string.zodiac_scorpio, 10, 23),
    SAGITTARIUS("♐", R.string.zodiac_sagittarius, 11, 22);

    companion object {
        fun of(month: Int, day: Int): Zodiac {
            val key = month * 100 + day
            // The last sign starting on or before this date; before Jan 20 wraps back to Capricorn.
            return entries.filter { it != CAPRICORN }.lastOrNull { key >= it.startMonth * 100 + it.startDay }
                ?.takeUnless { key >= CAPRICORN.startMonth * 100 + CAPRICORN.startDay }
                ?: CAPRICORN
        }
    }
}
