package io.github.wishday.birthday

import io.github.wishday.data.Person
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

object Birthdays {

    /** The date a birthday is celebrated in [year]; Feb 29 falls back to Feb 28 in non-leap years. */
    fun celebrationDate(month: Int, day: Int, year: Int): LocalDate =
        if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28)
        } else {
            LocalDate.of(year, month, day)
        }

    /** Next celebration on or after [today]. */
    fun next(person: Person, today: LocalDate): LocalDate {
        val thisYear = celebrationDate(person.month, person.day, today.year)
        return if (thisYear.isBefore(today)) {
            celebrationDate(person.month, person.day, today.year + 1)
        } else {
            thisYear
        }
    }

    fun daysUntil(person: Person, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, next(person, today))

    fun isToday(person: Person, today: LocalDate): Boolean = daysUntil(person, today) == 0L

    /** Age the person turns at their next birthday, or null when the birth year is unknown. */
    fun ageTurning(person: Person, today: LocalDate): Int? =
        person.year?.let { next(person, today).year - it }

    /**
     * Birthdays that fell after the run on [lastRunEpochDay] and before [today], with nothing sent:
     * the days the daily check never ran. Looks back at most [maxDays]; nothing before the first run.
     */
    fun missed(people: List<Person>, lastRunEpochDay: Long, today: LocalDate, maxDays: Long = 31): List<Pair<Person, LocalDate>> {
        if (lastRunEpochDay < 0) return emptyList()
        val first = maxOf(lastRunEpochDay + 1, today.toEpochDay() - maxDays)
        return (first until today.toEpochDay()).flatMap { epochDay ->
            val day = LocalDate.ofEpochDay(epochDay)
            people.filter { isToday(it, day) && it.lastGreetedYear != day.year }.map { it to day }
        }
    }

    /** People sorted by how soon their birthday is. */
    fun upcoming(people: List<Person>, today: LocalDate): List<Person> =
        people.sortedWith(compareBy({ daysUntil(it, today) }, { !it.favorite }, { it.name.lowercase() }))

    /**
     * Fills a message template. Supported placeholders:
     * `{name}` first name, `{fullname}` full name, `{age}` age being turned (blank if unknown).
     */
    fun render(template: String, person: Person, today: LocalDate): String {
        val fullName = person.name.trim()
        val firstName = fullName.substringBefore(' ').ifBlank { fullName }
        return template
            .replace("{name}", firstName)
            .replace("{fullname}", fullName)
            .replace("{age}", ageTurning(person, today)?.toString().orEmpty())
    }
}
