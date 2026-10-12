package io.github.wishday.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wishday.R
import io.github.wishday.data.Person
import kotlinx.coroutines.launch

/**
 * Sorts out people who have no contact: Merge a duplicate into the entry that has their contact,
 * Link someone to a contact saved under another name, or tick them to create a new contact.
 * Only people with no likely contact or duplicate start ticked.
 */
@Composable
fun CreateContactsDialog(
    vm: WishdayViewModel,
    people: List<WishdayViewModel.UnlinkedPerson>,
    onDismiss: () -> Unit,
    onConfirm: (List<Person>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = remember(people) { mutableStateListOf<WishdayViewModel.UnlinkedPerson>().apply { addAll(people) } }
    val chosen = remember(people) {
        mutableStateListOf<Person>().apply {
            addAll(people.filter { it.duplicateOf == null && it.likelyContact == null }.map { it.person })
        }
    }
    var linking by remember { mutableStateOf<Person?>(null) }

    fun resolved(person: Person) {
        rows.removeAll { it.person.id == person.id }
        chosen.remove(person)
        if (rows.isEmpty()) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_contacts_title)) },
        text = {
            Column {
                Text(stringResource(R.string.create_contacts_text))
                LazyColumn(Modifier.heightIn(max = 400.dp).padding(top = 8.dp)) {
                    items(rows, key = { it.person.id }) { (person, duplicateOf, likely) ->
                        val ticked = person in chosen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (ticked) chosen.remove(person) else chosen.add(person) },
                        ) {
                            Checkbox(checked = ticked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    when {
                                        duplicateOf != null -> stringResource(R.string.duplicate_of, duplicateOf.name)
                                        likely != null -> stringResource(R.string.maybe_contact, likely.name)
                                        person.phone.isNullOrBlank() -> stringResource(R.string.birthday_only)
                                        else -> person.phone
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (duplicateOf != null || likely != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (duplicateOf != null) {
                                TextButton(onClick = {
                                    scope.launch { vm.merge(person, duplicateOf); resolved(person) }
                                }) { Text(stringResource(R.string.merge)) }
                            } else {
                                TextButton(onClick = { linking = person }) { Text(stringResource(R.string.link)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen.toList()) }, enabled = chosen.isNotEmpty()) {
                Text(stringResource(R.string.create_count, chosen.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )

    linking?.let { person ->
        ContactPickerSheet(
            vm,
            nameHint = person.name,
            onDismiss = { linking = null },
            onPick = { contact ->
                linking = null
                scope.launch {
                    if (vm.linkToContact(person, contact)) resolved(person)
                    else Toast.makeText(context, context.getString(R.string.contact_already_linked), Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}
