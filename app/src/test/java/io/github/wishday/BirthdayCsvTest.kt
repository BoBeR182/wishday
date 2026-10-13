package io.github.wishday

import io.github.wishday.backup.BirthdayCsv
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdayCsvTest {

    @Test
    fun roundTripKeepsEverything() {
        val people = listOf(
            Person(
                name = "Grace Hopper", phone = "+15555550123", month = 12, day = 9, year = 1906, channel = Channel.SMS,
                favorite = true, giftReminderDays = 14, notes = "Likes compilers, tea",
            ),
            Person(
                name = "Ada Lovelace", month = 12, day = 10, channel = Channel.NONE,
                customMessage = "Happy birthday, {name}!\nSee you soon, \"Ada\"",
            ),
        )
        val result = BirthdayCsv.read(BirthdayCsv.write(people))
        assertEquals(0, result.skipped)
        assertEquals(people.toSet(), result.people.toSet())
    }

    @Test
    fun yearlessBirthdaysAreWrittenWithDashes() {
        val csv = BirthdayCsv.write(listOf(Person(name = "Ada", month = 3, day = 7)))
        // Only digits and dashes, so no apostrophe: spreadsheets and contact apps see the date as is.
        assertEquals(
            "name,birthday,phone,channel,message,important,gift_reminder_days,notes,last_greeted_year\r\nAda,--03-07,,none,,,,,\r\n",
            csv,
        )
        assertEquals(Person(name = "Ada", month = 3, day = 7), BirthdayCsv.read(csv).people.single())
    }

    @Test
    fun formulaLikeCellsAreEscapedAndRestored() {
        val person = Person(name = "=HYPERLINK(\"x\")", phone = "+15555550123", month = 1, day = 2, notes = "@home -ish")
        val csv = BirthdayCsv.write(listOf(person))
        assertTrue(csv.lines()[1].startsWith("\"'=HYPERLINK"))
        assertTrue("'@home -ish" in csv)
        assertEquals(person, BirthdayCsv.read(csv).people.single())
    }

    @Test
    fun phoneNumbersAreNotEscapedButSignedFormulasAre() {
        val person = Person(name = "-2+3+cmd|' /C calc'!A0", phone = "+1 (519) 555-0123", month = 1, day = 2)
        val csv = BirthdayCsv.write(listOf(person))
        assertTrue(",+1 (519) 555-0123," in csv)
        assertTrue(csv.lines()[1].startsWith("'-2+3+cmd"))
        assertEquals(person, BirthdayCsv.read(csv).people.single())
    }

    @Test
    fun olderExportsWithEscapedNumbersStillImport() {
        val person = BirthdayCsv.read("name,birthday,phone\nAda,'--03-07,'+15555550123").people.single()
        assertEquals(Person(name = "Ada", phone = "+15555550123", month = 3, day = 7), person)
    }

    @Test
    fun lastGreetedYearSurvivesARoundTrip() {
        val person = Person(name = "Grace", month = 12, day = 9, lastGreetedYear = 2026)
        assertEquals(2026, BirthdayCsv.read(BirthdayCsv.write(listOf(person))).people.single().lastGreetedYear)
    }

    @Test
    fun readsOtherAppsColumnsInAnyOrder() {
        val csv = "﻿Phone Number,Date of Birth,Full Name\n555-1234,1990-02-29,Leap Person\n"
        val person = BirthdayCsv.read(csv).people.single()
        assertEquals("Leap Person", person.name)
        assertEquals("555-1234", person.phone)
        assertEquals(Triple(2, 29, 1990), Triple(person.month, person.day, person.year))
        assertEquals(Channel.NONE, person.channel)
    }

    @Test
    fun readsHeaderlessRowsInOurOrder() {
        val person = BirthdayCsv.read("Bob,--07-04,555,sms,Yo").people.single()
        assertEquals(Person(name = "Bob", phone = "555", month = 7, day = 4, channel = Channel.SMS, customMessage = "Yo"), person)
    }

    @Test
    fun badRowsAreSkippedAndCounted() {
        val csv = "name,birthday\nGood,2000-01-01\n,2000-01-01\nNo Date,soon\n\n"
        val result = BirthdayCsv.read(csv)
        assertEquals(listOf("Good"), result.people.map { it.name })
        assertEquals(2, result.skipped)
    }

    @Test
    fun smsWithoutPhoneFallsBackToReminder() {
        val person = BirthdayCsv.read("name,birthday,channel\nNo Phone,--05-05,sms").people.single()
        assertEquals(Channel.NONE, person.channel)
    }
}
