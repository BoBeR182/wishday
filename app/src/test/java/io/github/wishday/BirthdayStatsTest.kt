package io.github.wishday

import io.github.wishday.birthday.BirthdayStats
import io.github.wishday.birthday.Zodiac
import io.github.wishday.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BirthdayStatsTest {
    private val today = LocalDate.of(2026, 10, 12)
    private fun p(name: String, month: Int, day: Int, year: Int? = null) = Person(name = name, month = month, day = day, year = year)

    @Test
    fun zodiacBoundaries() {
        assertEquals(Zodiac.CAPRICORN, Zodiac.of(1, 19))
        assertEquals(Zodiac.AQUARIUS, Zodiac.of(1, 20))
        assertEquals(Zodiac.PISCES, Zodiac.of(3, 20))
        assertEquals(Zodiac.ARIES, Zodiac.of(3, 21))
        assertEquals(Zodiac.LIBRA, Zodiac.of(10, 12))
        assertEquals(Zodiac.SAGITTARIUS, Zodiac.of(12, 21))
        assertEquals(Zodiac.CAPRICORN, Zodiac.of(12, 22))
        assertEquals(Zodiac.CAPRICORN, Zodiac.of(12, 31))
    }

    @Test
    fun monthsAndBusiest() {
        val stats = BirthdayStats.of(listOf(p("A", 3, 1), p("B", 3, 9), p("C", 10, 20), p("D", 10, 30), p("E", 1, 5)), today)
        assertEquals(2, stats.byMonth[2])
        assertEquals(listOf(3, 10), stats.busiestMonths)
        assertEquals(2, stats.thisMonth)
        assertEquals(2, stats.next30Days)
    }

    @Test
    fun agesMilestonesAndExtremes() {
        val milly = p("Milly", 10, 12, 2001) // turns 25 today
        val dad = p("Dad", 11, 1, 1976) // turns 50
        val kid = p("Kid", 10, 20, 2008) // turns 18
        val stats = BirthdayStats.of(listOf(milly, dad, kid, p("No year", 5, 5)), today)
        assertEquals((25 + 50 + 18) / 3.0, stats.averageAge!!, 0.001)
        assertEquals(kid, stats.youngest)
        assertEquals(dad, stats.oldest)
        assertEquals(listOf(kid to 18, dad to 50), stats.milestones)
    }

    @Test
    fun twinsAndEmpty() {
        val stats = BirthdayStats.of(listOf(p("A", 6, 1), p("B", 6, 1), p("C", 6, 2)), today)
        assertEquals(listOf(listOf("A", "B")), stats.twins.map { g -> g.map { it.name } })

        val empty = BirthdayStats.of(emptyList(), today)
        assertEquals(emptyList<Int>(), empty.busiestMonths)
        assertNull(empty.averageAge)
    }
}
