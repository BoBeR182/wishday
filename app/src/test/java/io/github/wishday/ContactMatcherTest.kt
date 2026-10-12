package io.github.wishday

import io.github.wishday.contacts.ContactMatcher
import io.github.wishday.contacts.PhoneContact
import io.github.wishday.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatcherTest {
    private fun person(name: String, phone: String? = null, key: String? = null) =
        Person(name = name, month = 1, day = 1, phone = phone, contactLookupKey = key)

    private fun contact(key: String, name: String) = PhoneContact(key, name, "+1555000$key")

    private fun matchedNames(people: List<Person>, contacts: List<PhoneContact>) =
        ContactMatcher.match(people, contacts).associate { it.person.name to it.contact.lookupKey }

    @Test
    fun exactNameIgnoringCaseAccentsAndSpacing() {
        val result = matchedNames(listOf(person("josé  o'brien")), listOf(contact("1", "Jose OBrien")))
        assertEquals(mapOf("josé  o'brien" to "1"), result)
    }

    @Test
    fun middleNamesAndInitialsStillMatch() {
        val result = matchedNames(listOf(person("Grace Hopper")), listOf(contact("1", "Grace B. Hopper")))
        assertEquals(mapOf("Grace Hopper" to "1"), result)
    }

    @Test
    fun ambiguousNamesAreLeftAlone() {
        val contacts = listOf(contact("1", "Sam Lee"), contact("2", "Sam J Lee"), contact("3", "Alex"), contact("4", "Alex"))
        assertTrue(matchedNames(listOf(person("Sam M Lee"), person("Alex")), contacts).isEmpty())
    }

    @Test
    fun exactMatchWinsOverFirstAndLast() {
        val contacts = listOf(contact("1", "Sam Lee"), contact("2", "Sam J Lee"))
        assertEquals(mapOf("Sam Lee" to "1"), matchedNames(listOf(person("Sam Lee")), contacts))
    }

    @Test
    fun peopleWithNumbersAndContactsAlreadyLinkedAreSkipped() {
        val people = listOf(person("Ada", phone = "+1555"), person("Ada Lovelace", key = "1"), person("Bob"))
        val contacts = listOf(contact("1", "Ada Lovelace"), contact("2", "Ada"), contact("3", "Bob"))
        assertEquals(mapOf("Bob" to "3"), matchedNames(people, contacts))
    }

    @Test
    fun oneContactIsNeverGivenToTwoPeople() {
        val result = matchedNames(listOf(person("Ada Lovelace"), person("ada lovelace")), listOf(contact("1", "Ada Lovelace")))
        assertEquals(1, result.size)
    }

    @Test
    fun likelyAcceptsShortFormsOfTheFirstName() {
        val likely = ContactMatcher.likely("Sab", listOf(contact("1", "Sabrina Kowalski"), contact("2", "Ben Ross")))
        assertEquals("1", likely?.lookupKey)
    }

    @Test
    fun likelyPrefersMoreSharedWords() {
        val contacts = listOf(contact("1", "Sam Brown"), contact("2", "Samantha Stone"))
        assertEquals("2", ContactMatcher.likely("Sam Stone", contacts)?.lookupKey)
    }

    @Test
    fun likelyGivesNothingOnATieOrDifferentFirstName() {
        val contacts = listOf(contact("1", "Dan Lee"), contact("2", "Daniel Park"), contact("3", "Ann Moss"))
        assertEquals(null, ContactMatcher.likely("Dan", contacts))
        assertEquals(null, ContactMatcher.likely("Moss", contacts))
        // Two-letter names are too short to count as an abbreviation.
        assertEquals(null, ContactMatcher.likely("Al", listOf(contact("4", "Alice Wong"))))
    }
}
