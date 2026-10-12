package io.github.wishday.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.wishday.R
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
import io.github.wishday.ui.theme.LocalIsDark
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.wishday.contacts.ContactMatch
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import io.github.wishday.messaging.SignalLauncher
import io.github.wishday.messaging.WhatsAppLauncher
import io.github.wishday.ui.theme.AvatarGradients
import io.github.wishday.ui.theme.LocalPalette
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
    onStats: () -> Unit,
) {
    val people by vm.people.collectAsState()
    val res = LocalContext.current.resources
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var quickAdd by rememberSaveable { mutableStateOf(false) }

    val importContacts: () -> Unit = {
        scope.launch {
            val added = vm.importContacts()
            snackbar.showSnackbar(
                if (added == 0) res.getString(R.string.contacts_none_new)
                else res.getQuantityString(R.plurals.contacts_imported, added, added),
            )
        }
    }
    var matches by remember { mutableStateOf<List<ContactMatch>?>(null) }
    val findMatches: () -> Unit = {
        scope.launch {
            val found = vm.findContactMatches()
            if (found.isEmpty()) snackbar.showSnackbar(res.getString(R.string.match_none))
            else matches = found
        }
    }

    // What to run once contacts permissions are granted.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) afterPermission?.invoke()
        else scope.launch { snackbar.showSnackbar(res.getString(R.string.contacts_permission_needed)) }
        afterPermission = null
    }
    fun withContacts(write: Boolean = false, action: () -> Unit) {
        afterPermission = action
        contactsPermission.launch(
            if (write) arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            else arrayOf(Manifest.permission.READ_CONTACTS),
        )
    }
    fun contactWriteSummary(r: WishdayViewModel.ContactWriteResult) = buildList {
        add(res.getQuantityString(R.plurals.contacts_birthdays_added, r.added, r.added))
        if (r.alreadyHad > 0) add(res.getQuantityString(R.plurals.contacts_already_had, r.alreadyHad, r.alreadyHad))
        if (r.notWritten > 0) add(res.getQuantityString(R.plurals.contacts_not_editable, r.notWritten, r.notWritten))
    }.joinToString(" · ")
    var confirmSaveAll by remember { mutableStateOf(false) }
    var toCreate by remember { mutableStateOf<List<WishdayViewModel.UnlinkedPerson>>(emptyList()) }
    var addNumberFor by remember { mutableStateOf<Person?>(null) }
    val saveAllToContacts: () -> Unit = {
        withContacts(write = true) {
            scope.launch {
                val r = vm.writeBirthdaysToContacts()
                toCreate = r.unlinked
                snackbar.showSnackbar(contactWriteSummary(r))
            }
        }
    }
    val askContacts = { withContacts(action = importContacts) }

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
                text = { Text(stringResource(R.string.quick_add)) },
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
                Header(
                    next, today, vm, onEdit,
                    onImport = askContacts,
                    onMatch = { withContacts(action = findMatches) },
                    onSaveToContacts = { confirmSaveAll = true },
                    onStats = onStats,
                    onAddNumber = { person -> withContacts { addNumberFor = person } },
                    onSettings = onSettings,
                )
            }
            if (people.isEmpty()) {
                item { EmptyState(onImport = askContacts, onAdd = { quickAdd = true }) }
            } else if (rest.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.coming_up),
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

    matches?.let { found ->
        ContactMatchDialog(
            found,
            onDismiss = { matches = null },
            onConfirm = { chosen, writeBirthdays ->
                matches = null
                scope.launch {
                    val linked = vm.applyContactMatches(chosen)
                    if (writeBirthdays) {
                        withContacts(write = true) {
                            scope.launch {
                                val r = vm.writeBirthdaysToContacts(linked)
                                snackbar.showSnackbar(
                                    res.getQuantityString(R.plurals.linked_count, linked.size, linked.size) +
                                        " · " + contactWriteSummary(r),
                                )
                            }
                        }
                    } else {
                        snackbar.showSnackbar(res.getQuantityString(R.plurals.linked_choose_channel, linked.size, linked.size))
                    }
                }
            },
        )
    }

    addNumberFor?.let { person ->
        ContactPickerSheet(
            vm,
            nameHint = person.name,
            onDismiss = { addNumberFor = null },
            onPick = { contact ->
                addNumberFor = null
                vm.save(person.copy(phone = contact.phone))
                scope.launch { snackbar.showSnackbar(res.getString(R.string.number_added, contact.phone, person.name)) }
            },
        )
    }

    if (confirmSaveAll) {
        AlertDialog(
            onDismissRequest = { confirmSaveAll = false },
            title = { Text(stringResource(R.string.save_to_contacts_title)) },
            text = {
                Text(stringResource(R.string.save_to_contacts_text))
            },
            confirmButton = {
                TextButton(onClick = { confirmSaveAll = false; saveAllToContacts() }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { confirmSaveAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (toCreate.isNotEmpty()) {
        CreateContactsDialog(
            vm,
            toCreate,
            onDismiss = { toCreate = emptyList() },
            onConfirm = { chosen ->
                toCreate = emptyList()
                scope.launch {
                    val created = vm.createContacts(chosen)
                    snackbar.showSnackbar(res.getQuantityString(R.plurals.contacts_created, created, created))
                }
            },
        )
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
    val dark = LocalIsDark.current
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
    onMatch: () -> Unit,
    onSaveToContacts: () -> Unit,
    onStats: () -> Unit,
    onAddNumber: (Person) -> Unit,
    onSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(LocalPalette.current.brush)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.Contacts, contentDescription = stringResource(R.string.contacts), tint = Color.White)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_import_contacts)) },
                        onClick = { menu = false; onImport() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_match_contacts)) },
                        onClick = { menu = false; onMatch() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_save_to_contacts)) },
                        onClick = { menu = false; onSaveToContacts() },
                    )
                }
            }
            IconButton(onClick = onStats) {
                Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.stats), tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White)
            }
        }
        Text(
            today.format(java.time.format.DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern)))
                .replaceFirstChar { it.titlecase() },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        if (next.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(end = 12.dp)) { UpNextCard(next, today, vm, onEdit, onAddNumber) }
        }
    }
}

/**
 * Frosted-glass card for the next birthday, with one-tap buttons to wish them. Everyone
 * sharing that date is listed; the quick buttons act on whoever is selected.
 */
@Composable
private fun UpNextCard(
    next: List<Person>,
    today: LocalDate,
    vm: WishdayViewModel,
    onEdit: (Long) -> Unit,
    onAddNumber: (Person) -> Unit,
) {
    val context = LocalContext.current
    val days = Birthdays.daysUntil(next.first(), today)
    val shape = RoundedCornerShape(28.dp)
    val several = next.size > 1
    var selectedId by remember(next.map { it.id }) { mutableStateOf(next.first().id) }
    val person = next.firstOrNull { it.id == selectedId } ?: next.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (days == 0L) R.string.today_label else R.string.up_next),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    if (several) {
                        shortDate(person) + " · " + pluralStringResource(R.plurals.birthdays_count, next.size, next.size)
                    } else {
                        shortDate(person)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Countdown(days)
        }
        Spacer(Modifier.height(10.dp))

        next.forEach { p ->
            val selected = p.id == person.id
            val age = Birthdays.ageTurning(p, today)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .then(if (several && selected) Modifier.background(Color.White.copy(alpha = 0.18f)) else Modifier)
                    // First tap picks who the buttons message; tapping the picked person opens them.
                    .clickable { if (selected) onEdit(p.id) else selectedId = p.id }
                    .padding(vertical = 6.dp, horizontal = if (several) 8.dp else 0.dp),
            ) {
                Avatar(p.name, size = if (several) 44 else 56, ring = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name + badges(p),
                        style = if (several) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (age != null) {
                        Text(stringResource(R.string.turns_age, age), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }

        val phone = person.phone?.takeIf { it.isNotBlank() }
        Spacer(Modifier.height(12.dp))
        if (several) {
            Text(
                stringResource(R.string.wish_person, person.name.substringBefore(' ')),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            val cell = Modifier.weight(1f)
            if (phone != null) {
                QuickApp(Icons.AutoMirrored.Filled.Chat, SignalLauncher.appName(context), cell) {
                    context.launch(vm.signalIntent(person))
                }
                if (WhatsAppLauncher.isInstalled(context)) {
                    QuickApp(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.whatsapp), cell) { context.launch(vm.whatsAppIntent(person)) }
                }
                QuickApp(Icons.Default.Sms, stringResource(R.string.quick_text), cell) {
                    context.launch(vm.quickMessage(person) { msg ->
                        Intent(Intent.ACTION_SENDTO, ("smsto:" + Uri.encode(phone)).toUri()).putExtra("sms_body", msg)
                    })
                }
                QuickApp(Icons.Default.Call, stringResource(R.string.quick_call), cell) {
                    context.launch(Intent(Intent.ACTION_DIAL, ("tel:" + Uri.encode(phone)).toUri()))
                }
            }
            if (phone == null) {
                QuickApp(Icons.Default.PersonAdd, stringResource(R.string.quick_add_number), cell) { onAddNumber(person) }
            }
            QuickApp(Icons.Default.Share, stringResource(R.string.quick_share), cell) {
                context.launch(vm.quickMessage(person) { msg ->
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), null)
                })
            }
            // Keep buttons a sensible size when only a few are shown.
            repeat((if (phone != null) 0 else 2)) { Spacer(cell) }
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
                pluralStringResource(R.plurals.days_unit, days.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun QuickApp(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                person.name + badges(person), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (age != null) shortDate(person) + " · " + stringResource(R.string.turns_age, age) else shortDate(person),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChannelIcon(person.channel)
            }
        }
        Spacer(Modifier.width(8.dp))
        val label = if (days == 1L) {
            stringResource(R.string.tomorrow)
        } else {
            pluralStringResource(R.plurals.days_count, days.toInt(), days.toInt())
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (soon) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (soon) Modifier.background(LocalPalette.current.brush)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChannelIcon(channel: Channel) {
    val (icon, description) = when (channel) {
        Channel.SMS -> Icons.Default.Sms to stringResource(R.string.channel_sends_sms)
        Channel.SIGNAL -> Icons.AutoMirrored.Filled.Chat to "Signal"
        Channel.WHATSAPP -> Icons.AutoMirrored.Filled.Chat to stringResource(R.string.whatsapp)
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

/** ⭐ for important people, 🎁 when a gift reminder is set. */
private fun badges(person: Person) = buildString {
    if (person.favorite) append(" ⭐")
    if (person.giftReminderDays != null) append(" 🎁")
}

@Composable
private fun shortDate(person: Person): String {
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(R.string.short_date, Month.of(person.month).getDisplayName(TextStyle.SHORT, locale), person.day)
}

@Composable
private fun EmptyState(onImport: () -> Unit, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎂", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_birthdays_yet), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_hint),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) { Text(stringResource(R.string.import_from_contacts)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.quick_add)) }
    }
}

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.no_app_found), Toast.LENGTH_SHORT).show()
    }
}
