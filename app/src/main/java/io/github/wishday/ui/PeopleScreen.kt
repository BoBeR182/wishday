package io.github.wishday.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.wishday.birthday.Birthdays
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    vm: WishdayViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val people by vm.people.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    val importContacts: () -> Unit = {
        scope.launch {
            val added = vm.importContacts()
            snackbar.showSnackbar(
                if (added == 0) "No new birthdays found in contacts" else "Imported $added birthdays",
            )
        }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) importContacts()
        else scope.launch { snackbar.showSnackbar("Contacts permission is needed to import birthdays") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wishday") },
                actions = {
                    IconButton(onClick = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }) {
                        Icon(Icons.Default.Contacts, contentDescription = "Import from contacts")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add birthday") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (people.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                onImport = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                onAdd = onAdd,
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(people, key = { it.id }) { person ->
                    PersonRow(person, today, onClick = { onEdit(person.id) })
                }
                item { Spacer(Modifier.height(88.dp)) } // clear the FAB
            }
        }
    }
}

@Composable
private fun PersonRow(person: Person, today: LocalDate, onClick: () -> Unit) {
    val days = Birthdays.daysUntil(person, today)
    val age = Birthdays.ageTurning(person, today)
    val isToday = days == 0L
    val locale = LocalConfiguration.current.locales[0]
    val date = "${Month.of(person.month).getDisplayName(TextStyle.SHORT, locale)} ${person.day}"

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = if (isToday) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = { Initial(person.name) },
        headlineContent = { Text(person.name, fontWeight = if (isToday) FontWeight.Bold else null) },
        supportingContent = { Text(if (age != null) "$date · turns $age" else date) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelIcon(person.channel)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (days) {
                        0L -> "Today 🎂"
                        1L -> "Tomorrow"
                        else -> "in $days days"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}

@Composable
private fun ChannelIcon(channel: Channel) {
    when (channel) {
        Channel.SMS -> Icon(Icons.Default.Sms, contentDescription = "Sends SMS", tint = MaterialTheme.colorScheme.primary)
        Channel.SIGNAL -> Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Signal", tint = MaterialTheme.colorScheme.tertiary)
        Channel.NONE -> Unit
    }
}

@Composable
private fun Initial(name: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onImport: () -> Unit, onAdd: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎂", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("No birthdays yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import them from your contacts, or add them by hand.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) { Text("Import from contacts") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAdd) { Text("Add manually") }
    }
}
