package io.github.wishday.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wishday.contacts.ContactMatcher
import io.github.wishday.contacts.PhoneContact

/**
 * Wishday's own contact picker, with search by name or number. Contacts whose name
 * matches [nameHint] are listed first. Requires READ_CONTACTS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerSheet(
    vm: WishdayViewModel,
    nameHint: String,
    onDismiss: () -> Unit,
    onPick: (PhoneContact) -> Unit,
) {
    val contacts by produceState<List<PhoneContact>?>(null) { value = vm.loadPhoneContacts() }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val shown = remember(contacts, query, nameHint) {
        val all = contacts.orEmpty()
        val q = ContactMatcher.normalize(query)
        val digits = query.filter(Char::isDigit)
        val filtered = if (query.isBlank()) all else all.filter { c ->
            val name = ContactMatcher.normalize(c.name)
            (q.isNotEmpty() && q.split(' ').all { word -> name.split(' ').any { it.startsWith(word) } }) ||
                (digits.length >= 3 && c.phone.filter(Char::isDigit).contains(digits))
        }
        // People named like this person float to the top.
        val hintWords = ContactMatcher.normalize(nameHint).split(' ').filter { it.isNotEmpty() }.toSet()
        filtered.sortedWith(
            compareByDescending<PhoneContact> { c ->
                ContactMatcher.normalize(c.name).split(' ').count { it in hintWords }
            }.thenBy { it.name.lowercase() },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).imePadding()) {
            Text(
                "Choose a contact",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by name or number") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            when {
                contacts == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                shown.isEmpty() -> Text(
                    if (query.isBlank()) "No contacts with phone numbers" else "No contacts match \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(shown, key = { it.lookupKey }) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.name) },
                            supportingContent = { Text(contact.phone) },
                            modifier = Modifier.clickable { onPick(contact) },
                        )
                    }
                }
            }
        }
    }
}
