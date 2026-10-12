package io.github.wishday

import io.github.wishday.contacts.ContactWriter
import io.github.wishday.data.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactWriterTest {
    @Test
    fun birthdaysUseTheFormatContactsAppsExpect() {
        assertEquals("1906-12-09", ContactWriter.startDate(Person(name = "Grace", month = 12, day = 9, year = 1906)))
        assertEquals("--03-07", ContactWriter.startDate(Person(name = "Ada", month = 3, day = 7)))
    }
}
