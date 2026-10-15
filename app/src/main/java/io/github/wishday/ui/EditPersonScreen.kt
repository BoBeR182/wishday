package io.github.wishday.ui

import androidx.compose.ui.res.stringResource
import io.github.wishday.R
import kotlinx.coroutines.launch
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material.icons.filled.Contacts
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import io.github.wishday.messaging.SignalLauncher
import androidx.compose.ui.Alignment
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

/** Add (personId == null) or edit a birthday. */
@Composable
fun EditPersonScreen(vm: WishdayViewModel, personId: Long?, onDone: () -> Unit) {
    val loaded by produceState<Person?>(null, personId) {
        value = personId?.let { vm.get(it) }
            ?: vm.draft.also { vm.draft = null }
            ?: Person(name = "", month = 1, day = 1)
    }
    loaded?.let { PersonForm(vm, it, isNew = personId == null, onDone = onDone) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonForm(vm: WishdayViewModel, original: Person, isNew: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(original.name) }
    var phone by rememberSaveable { mutableStateOf(original.phone.orEmpty()) }
    var month by rememberSaveable { mutableStateOf(original.month) }
    var day by rememberSaveable { mutableStateOf(original.day.toString()) }
    var year by rememberSaveable { mutableStateOf(original.year?.toString().orEmpty()) }
    var channel by rememberSaveable { mutableStateOf(original.channel) }
    var message by rememberSaveable { mutableStateOf(original.customMessage.orEmpty()) }
    var favorite by rememberSaveable { mutableStateOf(original.favorite) }
    var giftDays by rememberSaveable { mutableStateOf(original.giftReminderDays) }
    var notes by rememberSaveable { mutableStateOf(original.notes.orEmpty()) }
    var lookupKey by rememberSaveable { mutableStateOf(original.contactLookupKey) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmSms by rememberSaveable { mutableStateOf(false) }

    val yearValue = year.toIntOrNull()
    // Feb 29 is always allowed; leap-year fallback is handled when celebrating.
    val maxDay = YearMonth.of(2000, month).lengthOfMonth()
    val dayValue = day.toIntOrNull()?.takeIf { it in 1..maxDay }
    val needsPhone = channel != Channel.NONE && phone.isBlank()
    val valid = name.isNotBlank() && dayValue != null && (year.isBlank() || yearValue in 1800..2200) && !needsPhone

    fun current() = original.copy(
        name = name.trim(),
        phone = phone.trim().ifBlank { null },
        month = month,
        day = dayValue ?: 1,
        year = yearValue,
        channel = channel,
        customMessage = message.ifBlank { null },
        favorite = favorite,
        giftReminderDays = giftDays,
        notes = notes.trim().ifBlank { null },
        contactLookupKey = lookupKey,
    )

    // The system picker grants access to just the chosen number, so no contacts permission is needed.
    // Wishday's own searchable picker; falls back to the system picker without contacts permission.
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val systemPicker = rememberLauncherForActivityResult(PickPhone) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(uri, arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { phone = it }
                if (name.isBlank()) c.getString(1)?.let { name = it }
            }
        }
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showPicker = true else systemPicker.launch(null)
    }
    fun pickContact() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            showPicker = true
        } else {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val sms = rememberSmsPermission()

    fun save() {
        if (channel == Channel.SMS) {
            sms.request { vm.save(current()); onDone() }
        } else {
            vm.save(current()); onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.add_birthday else R.string.edit_birthday)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { favorite = !favorite }) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(if (favorite) R.string.unmark_important else R.string.mark_important),
                            tint = if (favorite) Color(0xFFFFB300) else LocalContentColor.current,
                        )
                    }
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                    }
                    TextButton(onClick = ::save, enabled = valid) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthPicker(month, onChange = { month = it }, modifier = Modifier.weight(1.4f))
                OutlinedTextField(
                    value = day, onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.day)) }, singleLine = true, isError = dayValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.8f),
                )
                OutlinedTextField(
                    value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.year)) }, placeholder = { Text(stringResource(R.string.year_optional)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text(stringResource(R.string.phone_number)) }, singleLine = true,
                isError = needsPhone,
                supportingText = if (needsPhone) ({ Text(stringResource(R.string.phone_needed)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                trailingIcon = {
                    IconButton(onClick = ::pickContact) {
                        Icon(Icons.Default.Contacts, contentDescription = stringResource(R.string.pick_from_contacts))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.gift_reminder), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.gift_reminder_help),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                GIFT_REMINDER_OPTIONS.forEach { (days, label) ->
                    FilterChip(selected = giftDays == days, onClick = { giftDays = days }, label = { Text(stringResource(label)) })
                }
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(stringResource(R.string.gift_notes)) },
                placeholder = { Text(stringResource(R.string.gift_notes_placeholder)) },
                supportingText = { Text(stringResource(R.string.gift_notes_help)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.on_their_birthday), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Channel.entries.forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = channel == c,
                        onClick = {
                            channel = c
                            if (c == Channel.SMS) sms.request()
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, Channel.entries.size),
                    ) { Text(stringResource(c.labelRes)) }
                }
            }
            Text(stringResource(channel.explanationRes), style = MaterialTheme.typography.bodySmall)
            if (channel == Channel.SMS && !sms.granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sms_permission_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { sms.request() }) { Text(stringResource(R.string.allow)) }
                }
            }

            if (channel != Channel.NONE) {
                OutlinedTextField(
                    value = message, onValueChange = { message = it },
                    label = { Text(stringResource(R.string.custom_message)) },
                    placeholder = { Text(vm.settings.defaultMessage) },
                    supportingText = { Text(stringResource(R.string.custom_message_help)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelMedium)
                        Text(vm.messageFor(current()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!isNew && !needsPhone) {
                    FilledTonalButton(
                        onClick = {
                            if (channel == Channel.SMS) {
                                sms.request { if (it) confirmSms = true }
                            } else {
                                vm.save(current())
                                try {
                                    context.startActivity(
                                        if (channel == Channel.WHATSAPP) vm.whatsAppIntent(current())
                                        else vm.signalIntent(current()),
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, context.getString(R.string.app_not_installed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                channel == Channel.SMS -> stringResource(R.string.send_sms_now)
                                channel == Channel.WHATSAPP -> stringResource(R.string.open_in_app_now, stringResource(R.string.whatsapp))
                                SignalLauncher.isInstalled(context) ->
                                    stringResource(R.string.open_in_app_now, SignalLauncher.appName(context))
                                else -> stringResource(R.string.share_message_now)
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_person_title, original.name)) },
            confirmButton = { TextButton(onClick = { vm.delete(original); onDone() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showPicker) {
        ContactPickerSheet(
            vm,
            nameHint = name,
            onDismiss = { showPicker = false },
            onPick = { contact ->
                showPicker = false
                phone = contact.phone
                if (name.isBlank()) name = contact.name
                lookupKey = contact.lookupKey
            },
        )
    }

    if (confirmSms) {
        AlertDialog(
            onDismissRequest = { confirmSms = false },
            title = { Text(stringResource(R.string.text_now_title, name.trim())) },
            text = { Text(vm.messageFor(current())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSms = false
                    val person = current()
                    scope.launch {
                        val result = vm.saveAndSendSmsNow(person)
                        Toast.makeText(
                            context,
                            result.fold(
                                { context.getString(R.string.sending) },
                                { context.getString(R.string.couldnt_send, it.message.orEmpty()) },
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(R.string.send)) }
            },
            dismissButton = { TextButton(onClick = { confirmSms = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Opens the system contact picker filtered to phone numbers; returns the chosen number's URI. */
private object PickPhone : ActivityResultContract<Unit?, Uri?>() {
    override fun createIntent(context: Context, input: Unit?) = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthPicker(month: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    fun label(m: Int) = Month.of(m).getDisplayName(TextStyle.FULL, locale)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label(month), onValueChange = {}, readOnly = true,
            label = { Text(stringResource(R.string.month)) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..12).forEach { m ->
                DropdownMenuItem(text = { Text(label(m)) }, onClick = { onChange(m); expanded = false })
            }
        }
    }
}

/** Gift reminder choices: null is off. */
private val GIFT_REMINDER_OPTIONS = listOf(
    null to R.string.off,
    3 to R.string.gift_3_days,
    7 to R.string.gift_1_week,
    14 to R.string.gift_2_weeks,
    30 to R.string.gift_1_month,
)

private val Channel.labelRes
    get() = when (this) {
        Channel.NONE -> R.string.channel_remind
        Channel.SMS -> R.string.channel_sms
        Channel.SIGNAL -> R.string.channel_signal
        Channel.WHATSAPP -> R.string.whatsapp
    }

private val Channel.explanationRes
    get() = when (this) {
        Channel.NONE -> R.string.channel_remind_help
        Channel.SMS -> R.string.channel_sms_help
        Channel.SIGNAL -> R.string.channel_signal_help
        Channel.WHATSAPP -> R.string.channel_whatsapp_help
    }
