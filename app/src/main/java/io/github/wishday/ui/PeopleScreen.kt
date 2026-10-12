package io.github.wishday.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import io.github.wishday.birthday.Birthdays
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import io.github.wishday.messaging.SignalLauncher
import io.github.wishday.ui.theme.AvatarGradients
import io.github.wishday.ui.theme.BrandGradient
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import kotlin.math.absoluteValue

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
    var quickAdd by rememberSaveable { mutableStateOf(false) }

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
    val askContacts = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }

    LightStatusBarIcons()

    // Everyone celebrating on the soonest date shares the "Up next" card.
    val next = people.firstOrNull()?.let { first ->
        people.takeWhile { Birthdays.daysUntil(it, today) == Birthdays.daysUntil(first, today) }
    }.orEmpty()
    val rest = people.drop(next.size)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { quickAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Quick add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp), // clear the FAB and navigation bar
        ) {
            item {
                Header(next, today, vm, onEdit, onImport = askContacts, onSettings = onSettings)
            }
            if (people.isEmpty()) {
                item { EmptyState(onImport = askContacts, onAdd = { quickAdd = true }) }
            } else if (rest.isNotEmpty()) {
                item {
                    Text(
                        "Coming up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(rest, key = { it.id }) { person ->
                    PersonCard(person, today, onClick = { onEdit(person.id) })
                }
            }
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            vm,
            onDismiss = { quickAdd = false },
            onMoreOptions = { id ->
                quickAdd = false
                if (id != null) onEdit(id) else onAdd()
            },
        )
    }
}

/** The header is a gradient in both themes, so its status bar icons are always light. */
@Composable
private fun LightStatusBarIcons() {
    val activity = LocalActivity.current ?: return
    val dark = isSystemInDarkTheme()
    DisposableEffect(activity, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = !dark }
    }
}

@Composable
private fun Header(
    next: List<Person>,
    today: LocalDate,
    vm: WishdayViewModel,
    onEdit: (Long) -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(BrandGradient)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Wishday",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onImport) {
                Icon(Icons.Default.Contacts, contentDescription = "Import from contacts", tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        Text(
            today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        if (next.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(end = 12.dp)) { UpNextCard(next, today, vm, onEdit) }
        }
    }
}

/** Frosted-glass card for the next birthday, with one-tap buttons to wish them. */
@Composable
private fun UpNextCard(next: List<Person>, today: LocalDate, vm: WishdayViewModel, onEdit: (Long) -> Unit) {
    val person = next.first()
    val context = LocalContext.current
    val days = Birthdays.daysUntil(person, today)
    val age = Birthdays.ageTurning(person, today)
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable { onEdit(person.id) }
            .padding(20.dp),
    ) {
        Text(
            if (days == 0L) "🎉 TODAY" else "UP NEXT",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(person.name, size = 56, ring = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val others = next.size - 1
                Text(
                    buildString {
                        append(shortDate(person))
                        if (age != null) append(" · turns $age")
                        if (others > 0) append(" · +$others more")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Countdown(days)
        }

        val phone = person.phone?.takeIf { it.isNotBlank() }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (phone != null) {
                QuickApp(Icons.AutoMirrored.Filled.Chat, SignalLauncher.appName(context)) {
                    context.launch(vm.signalIntent(person))
                }
                QuickApp(Icons.Default.Sms, "Text") {
                    context.launch(vm.quickMessage(person) { msg ->
                        Intent(Intent.ACTION_SENDTO, ("smsto:" + Uri.encode(phone)).toUri()).putExtra("sms_body", msg)
                    })
                }
                QuickApp(Icons.Default.Call, "Call") {
                    context.launch(Intent(Intent.ACTION_DIAL, ("tel:" + Uri.encode(phone)).toUri()))
                }
            }
            QuickApp(Icons.Default.Share, "Share") {
                context.launch(vm.quickMessage(person) { msg ->
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), null)
                })
            }
        }
    }
}

@Composable
private fun Countdown(days: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (days == 0L) "🎂" else days.toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
        if (days > 0) {
            Text(
                if (days == 1L) "day" else "days",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun QuickApp(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun PersonCard(person: Person, today: LocalDate, onClick: () -> Unit) {
    val days = Birthdays.daysUntil(person, today)
    val age = Birthdays.ageTurning(person, today)
    val soon = days <= 7

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Avatar(person.name, size = 44)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (age != null) "${shortDate(person)} · turns $age" else shortDate(person),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChannelIcon(person.channel)
            }
        }
        Spacer(Modifier.width(8.dp))
        val label = if (days == 1L) "Tomorrow" else "$days days"
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (soon) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (soon) Modifier.background(BrandGradient)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChannelIcon(channel: Channel) {
    val (icon, description) = when (channel) {
        Channel.SMS -> Icons.Default.Sms to "Sends SMS"
        Channel.SIGNAL -> Icons.AutoMirrored.Filled.Chat to "Signal"
        Channel.NONE -> return
    }
    Spacer(Modifier.width(6.dp))
    Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
}

/** A gradient circle with the person's initial; the gradient is picked from their name. */
@Composable
private fun Avatar(name: String, size: Int, ring: Boolean = false) {
    val colors = AvatarGradients[name.lowercase().hashCode().absoluteValue % AvatarGradients.size]
    Box(
        modifier = Modifier
            .size(size.dp)
            .then(if (ring) Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape) else Modifier)
            .padding(if (ring) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            style = if (size >= 56) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun shortDate(person: Person): String {
    val locale = LocalConfiguration.current.locales[0]
    return "${Month.of(person.month).getDisplayName(TextStyle.SHORT, locale)} ${person.day}"
}

@Composable
private fun EmptyState(onImport: () -> Unit, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎂", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("No birthdays yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import them from your contacts or a Birday backup (Settings → Backup), or add them by hand.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) { Text("Import from contacts") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAdd) { Text("Quick add") }
    }
}

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app found for that", Toast.LENGTH_SHORT).show()
    }
}
