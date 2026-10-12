package io.github.wishday.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: WishdayViewModel, onDone: () -> Unit, onAbout: () -> Unit) {
    val s = vm.settings
    var message by rememberSaveable { mutableStateOf(s.defaultMessage) }
    var hour by rememberSaveable { mutableIntStateOf(s.sendHour) }
    var minute by rememberSaveable { mutableIntStateOf(s.sendMinute) }
    var daysBefore by rememberSaveable { mutableFloatStateOf(s.remindDaysBefore.toFloat()) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    fun save() = vm.updateSettings(message, hour, minute, daysBefore.roundToInt())
    fun saveAndClose() {
        save()
        onDone()
    }
    BackHandler(onBack = ::saveAndClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = ::saveAndClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = message, onValueChange = { message = it },
                label = { Text("Default message") },
                supportingText = { Text("{name} first name · {fullname} full name · {age} age they turn") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            ListItem(
                modifier = Modifier.clickable { pickingTime = true },
                headlineContent = { Text("Send time") },
                supportingContent = { Text("When SMS go out and today's reminders appear") },
                trailingContent = {
                    Text(
                        LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )

            ListItem(
                headlineContent = { Text("Advance reminder") },
                supportingContent = {
                    Column {
                        Text(
                            when (val d = daysBefore.roundToInt()) {
                                0 -> "Off"
                                1 -> "1 day before"
                                else -> "$d days before"
                            },
                        )
                        Slider(value = daysBefore, onValueChange = { daysBefore = it }, valueRange = 0f..14f, steps = 13)
                    }
                },
            )

            HorizontalDivider()
            Backup(vm)
            HorizontalDivider()
            AboutSignal()
            HorizontalDivider()
            ListItem(
                // Save first: leaving this screen would otherwise drop unsaved edits.
                modifier = Modifier.clickable { save(); onAbout() },
                headlineContent = { Text("About Wishday") },
                supportingContent = { Text("Version, privacy, license and source code") },
            )
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(hour, minute, is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { hour = state.hour; minute = state.minute; pickingTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Backup(vm: WishdayViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { vm.exportCsv(uri) }
                .onSuccess { toast("Exported $it ${if (it == 1) "birthday" else "birthdays"}") }
                .onFailure { toast("Export failed: ${it.message}") }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { vm.importFile(uri) }
                .onSuccess { r ->
                    toast(
                        buildList {
                            add("Imported ${r.added}")
                            if (r.duplicates > 0) add("${r.duplicates} already saved")
                            if (r.invalid > 0) add("${r.invalid} skipped")
                        }.joinToString(" · "),
                    )
                }
                .onFailure { toast("Import failed: ${it.message}") }
        }
    }

    Text("Backup", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
    ListItem(
        modifier = Modifier.clickable { export.launch("wishday-${LocalDate.now()}.csv") },
        headlineContent = { Text("Export birthdays") },
        supportingContent = { Text("Save everyone to a CSV file you can keep or open in a spreadsheet") },
    )
    ListItem(
        modifier = Modifier.clickable {
            // Birday backups have no extension or MIME type, so any file can be picked; the contents decide.
            import.launch(arrayOf("*/*"))
        },
        headlineContent = { Text("Import birthdays") },
        supportingContent = { Text("From a Birday backup, a Wishday export, or any CSV with name and birthday columns") },
    )
}

@Composable
private fun AboutSignal() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("About Signal", style = MaterialTheme.typography.titleSmall)
        Text(
            "Signal doesn't let other apps send messages on your behalf, so Wishday prepares the message " +
                "and opens Signal for you — you just tap send. SMS can be sent fully automatically.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
