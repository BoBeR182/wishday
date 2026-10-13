package io.github.wishday

import io.github.wishday.birthday.Birthdays
import io.github.wishday.contacts.ContactDate
import io.github.wishday.contacts.parseContactDate
import io.github.wishday.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BirthdaysTest {
    private fun person(month: Int, day: Int, year: Int? = null, name: String = "Ada Lovelace") =
        Person(name = name, month = month, day = day, year = year)

    @Test
    fun birthdayLaterThisYear() {
        val today = LocalDate.of(2026, 3, 1)
        assertEquals(LocalDate.of(2026, 5, 17), Birthdays.next(person(5, 17), today))
        assertEquals(77, Birthdays.daysUntil(person(5, 17), today))
    }

    @Test
    fun birthdayAlreadyPassedRollsToNextYear() {
        val today = LocalDate.of(2026, 10, 12)
        assertEquals(LocalDate.of(2027, 5, 17), Birthdays.next(person(5, 17), today))
    }

    @Test
    fun birthdayTodayIsZeroDays() {
        val today = LocalDate.of(2026, 10, 12)
        assertTrue(Birthdays.isToday(person(10, 12), today))
    }

    @Test
    fun leapDayCelebratedOnFeb28InCommonYears() {
        assertTrue(Birthdays.isToday(person(2, 29), LocalDate.of(2027, 2, 28)))
        assertEquals(LocalDate.of(2028, 2, 29), Birthdays.next(person(2, 29), LocalDate.of(2028, 2, 1)))
    }

    @Test
    fun ageUsesYearOfNextBirthday() {
        val today = LocalDate.of(2026, 12, 1)
        assertEquals(37, Birthdays.ageTurning(person(1, 5, year = 1990), today))
        assertNull(Birthdays.ageTurning(person(1, 5), today))
    }

    @Test
    fun upcomingSortsSoonestFirst() {
        val today = LocalDate.of(2026, 10, 12)
        val a = person(10, 11, name = "Last")
        val b = person(10, 12, name = "Today")
        val c = person(11, 1, name = "Soon")
        assertEquals(listOf("Today", "Soon", "Last"), Birthdays.upcoming(listOf(a, b, c), today).map { it.name })
    }

    @Test
    fun renderFillsPlaceholders() {
        val text = Birthdays.render(
            "Happy {age}th, {name}! — to {fullname}",
            person(10, 12, year = 1990),
            LocalDate.of(2026, 10, 12),
        )
        assertEquals("Happy 36th, Ada! — to Ada Lovelace", text)
    }

    @Test
    fun parsesContactDateFormats() {
        assertEquals(ContactDate(5, 17, 1990), parseContactDate("1990-05-17"))
        assertEquals(ContactDate(5, 17, null), parseContactDate("--05-17"))
        assertEquals(ContactDate(5, 17, 1990), parseContactDate("19900517"))
        assertEquals(ContactDate(5, 17, 1990), parseContactDate("1990-05-17T00:00:00Z"))
        assertEquals(ContactDate(5, 17, null), parseContactDate("1604-05-17"))
        assertNull(parseContactDate("May 17"))
        assertNull(parseContactDate("1990-13-01"))
        assertNull(parseContactDate(null))
    }

    @Test
    fun missedFindsBirthdaysOnDaysWithoutARun() {
        val today = LocalDate.of(2026, 10, 13)
        val people = listOf(
            Person(id = 1, name = "Yesterday", month = 10, day = 12),
            Person(id = 2, name = "Greeted", month = 10, day = 11, lastGreetedYear = 2026),
            Person(id = 3, name = "Today", month = 10, day = 13),
            Person(id = 4, name = "Before last run", month = 10, day = 9),
        )
        val lastRun = LocalDate.of(2026, 10, 9).toEpochDay()
        assertEquals(listOf(1L to LocalDate.of(2026, 10, 12)), Birthdays.missed(people, lastRun, today).map { it.first.id to it.second })
    }

    @Test
    fun missedIsEmptyOnFirstRunOrAfterYesterdaysRun() {
        val today = LocalDate.of(2026, 10, 13)
        val people = listOf(Person(name = "A", month = 10, day = 12))
        assertEquals(emptyList<Any>(), Birthdays.missed(people, -1, today))
        assertEquals(emptyList<Any>(), Birthdays.missed(people, today.minusDays(1).toEpochDay(), today))
    }

    @Test
    fun missedHandlesNewYearLeapDaysAndTheLookBackLimit() {
        val nye = Person(id = 1, name = "NYE", month = 12, day = 31, lastGreetedYear = 2025)
        val leap = Person(id = 2, name = "Leap", month = 2, day = 29)
        // Dec 31 2026 was missed; greeted in 2025 doesn't count for 2026.
        assertEquals(listOf(LocalDate.of(2026, 12, 31)), Birthdays.missed(listOf(nye), LocalDate.of(2026, 12, 30).toEpochDay(), LocalDate.of(2027, 1, 1)).map { it.second })
        // Feb 29 is celebrated on Feb 28 in 2027.
        assertEquals(listOf(LocalDate.of(2027, 2, 28)), Birthdays.missed(listOf(leap), LocalDate.of(2027, 2, 27).toEpochDay(), LocalDate.of(2027, 3, 1)).map { it.second })
        // A phone off since January only looks back 31 days.
        assertEquals(emptyList<Any>(), Birthdays.missed(listOf(nye), LocalDate.of(2026, 1, 1).toEpochDay(), LocalDate.of(2027, 3, 1)))
    }
}
