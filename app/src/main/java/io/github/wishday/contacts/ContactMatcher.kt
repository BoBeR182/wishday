package io.github.wishday.contacts

import io.github.wishday.data.Person
import java.text.Normalizer

/** A device contact with a phone number. */
data class PhoneContact(val lookupKey: String, val name: String, val phone: String)

data class ContactMatch(val person: Person, val contact: PhoneContact)

/**
 * Pairs people who have no phone number with a contact of the same name. Only unambiguous
 * matches are returned: an exact name match first, then first-and-last name (so middle
 * names and initials don't get in the way). Two candidates means no match.
 */
object ContactMatcher {

    fun match(people: List<Person>, contacts: List<PhoneContact>): List<ContactMatch> {
        val taken = people.mapNotNullTo(mutableSetOf()) { it.contactLookupKey }
        val available = contacts.filter { it.lookupKey !in taken }.distinctBy { it.lookupKey }
        val byName = available.groupBy { normalize(it.name) }
        val byEnds = available.groupBy { ends(normalize(it.name)) }

        val matches = mutableListOf<ContactMatch>()
        val used = mutableSetOf<String>()
        for (person in people) {
            if (!person.phone.isNullOrBlank()) continue
            val name = normalize(person.name).ifEmpty { continue }
            val candidates = byName[name]
                ?: ends(name)?.let { byEnds[it] }
                ?: continue
            val contact = candidates.singleOrNull() ?: continue
            if (used.add(contact.lookupKey)) matches += ContactMatch(person, contact)
        }
        return matches
    }

    /** "  José  O'Brien " -> "jose obrien": case, accents, punctuation and spacing ignored. */
    fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

    /** First and last word, for names with two or more words. */
    private fun ends(normalized: String): Pair<String, String>? {
        val words = normalized.split(' ')
        return if (words.size >= 2) words.first() to words.last() else null
    }
}
