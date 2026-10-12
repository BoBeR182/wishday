package io.github.wishday

import io.github.wishday.backup.BirdayBackup
import io.github.wishday.backup.BirdayBackup.Event
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BirdayBackupTest {

    @Test
    fun birthdayWithKnownYear() {
        val person = BirdayBackup.toPerson(Event("BIRTHDAY", "Grace", "Hopper", yearMatters = true, "1906-12-09"))
        assertEquals(Person(name = "Grace Hopper", month = 12, day = 9, year = 1906, channel = Channel.NONE), person)
    }

    @Test
    fun placeholderYearIsDropped() {
        val person = BirdayBackup.toPerson(Event("BIRTHDAY", "Ada", null, yearMatters = false, "2024-03-07"))!!
        assertEquals("Ada", person.name)
        assertNull(person.year)
    }

    @Test
    fun blankSurnameAndWhitespaceAreTidied() {
        assertEquals("Ada", BirdayBackup.toPerson(Event("BIRTHDAY", " Ada ", "  ", true, "1815-12-10"))!!.name)
    }

    @Test
    fun otherEventTypesAndBadRowsAreSkipped() {
        assertNull(BirdayBackup.toPerson(Event("ANNIVERSARY", "Us", null, true, "2015-06-01")))
        assertNull(BirdayBackup.toPerson(Event("BIRTHDAY", "Bad", null, true, "someday")))
        assertNull(BirdayBackup.toPerson(Event("BIRTHDAY", " ", null, true, "2000-01-01")))
    }

    @Test
    fun detectsSqliteFilesButNotCsv() {
        assertTrue(BirdayBackup.looksLikeBackup("SQLite format 3\u0000rest".toByteArray()))
        assertFalse(BirdayBackup.looksLikeBackup("name,birthday\nAda,--12-10".toByteArray()))
        assertFalse(BirdayBackup.looksLikeBackup(ByteArray(0)))
    }
}
