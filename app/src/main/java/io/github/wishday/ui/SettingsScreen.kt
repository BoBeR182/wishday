package io.github.wishday.ui

import io.github.wishday.ui.theme.Palettes
import io.github.wishday.ui.theme.DarkMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.provider.Settings
import android.os.Build
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.wishday.R
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
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = ::saveAndClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Appearance(vm)
            HorizontalDivider()

            OutlinedTextField(
                value = message, onValueChange = { message = it },
                label = { Text(stringResource(R.string.default_message_label)) },
                supportingText = { Text(stringResource(R.string.placeholders_help)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            ListItem(
                modifier = Modifier.clickable { pickingTime = true },
                headlineContent = { Text(stringResource(R.string.send_time)) },
                supportingContent = { Text(stringResource(R.string.send_time_help)) },
                trailingContent = {
                    Text(
                        LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.advance_reminder)) },
                supportingContent = {
                    Column {
                        Text(
                            when (val d = daysBefore.roundToInt()) {
                                0 -> stringResource(R.string.off)
                                else -> pluralStringResource(R.plurals.days_before, d, d)
                            },
                        )
                        Slider(value = daysBefore, onValueChange = { daysBefore = it }, valueRange = 0f..14f, steps = 13)
                    }
                },
            )

            if (Build.VERSION.SDK_INT >= 33) {
                val context = LocalContext.current
                ListItem(
                    // Android's own per-app language screen (Android 13+).
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Text(stringResource(R.string.language_help)) },
                )
            }

            HorizontalDivider()
            Backup(vm)
            HorizontalDivider()
            AboutSignal()
            HorizontalDivider()
            ListItem(
                // Save first: leaving this screen would otherwise drop unsaved edits.
                modifier = Modifier.clickable { save(); onAbout() },
                headlineContent = { Text(stringResource(R.string.about_wishday)) },
                supportingContent = { Text(stringResource(R.string.about_wishday_help)) },
            )
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(hour, minute, is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { hour = state.hour; minute = state.minute; pickingTime = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Appearance(vm: WishdayViewModel) {
    val appearance by vm.appearance.collectAsState()
    val context = LocalContext.current
    Text(
        stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Palettes.ALL.forEach { p ->
            ThemeSwatch(stringResource(p.name), p.brush, selected = appearance.themeId == p.id) { vm.setTheme(p.id) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wall = remember(context) { dynamicLightColorScheme(context) }
            ThemeSwatch(
                stringResource(R.string.theme_wallpaper),
                Brush.linearGradient(listOf(wall.tertiary, wall.primary, wall.secondary)),
                selected = appearance.themeId == Palettes.WALLPAPER,
            ) { vm.setTheme(Palettes.WALLPAPER) }
        }
    }
    val modes = listOf(DarkMode.SYSTEM to R.string.mode_system, DarkMode.LIGHT to R.string.mode_light, DarkMode.DARK to R.string.mode_dark)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        modes.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = appearance.darkMode == mode,
                onClick = { vm.setDarkMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun ThemeSwatch(name: String, brush: Brush, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                )
                .padding(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(brush),
        ) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else null,
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
                .onSuccess { toast(context.resources.getQuantityString(R.plurals.exported_count, it, it)) }
                .onFailure { toast(context.getString(R.string.export_failed, it.message.orEmpty())) }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { vm.importFile(uri) }
                .onSuccess { r ->
                    toast(
                        buildList {
                            add(context.getString(R.string.imported_count, r.added))
                            if (r.duplicates > 0) add(context.getString(R.string.already_saved_count, r.duplicates))
                            if (r.invalid > 0) add(context.getString(R.string.skipped_count, r.invalid))
                        }.joinToString(" · "),
                    )
                }
                .onFailure { toast(context.getString(R.string.import_failed, it.message.orEmpty())) }
        }
    }

    Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
    ListItem(
        modifier = Modifier.clickable { export.launch("wishday-${LocalDate.now()}.csv") },
        headlineContent = { Text(stringResource(R.string.export_birthdays)) },
        supportingContent = { Text(stringResource(R.string.export_birthdays_help)) },
    )
    ListItem(
        modifier = Modifier.clickable {
            // Birday backups have no extension or MIME type, so any file can be picked; the contents decide.
            import.launch(arrayOf("*/*"))
        },
        headlineContent = { Text(stringResource(R.string.import_birthdays)) },
        supportingContent = { Text(stringResource(R.string.import_birthdays_help)) },
    )
}

@Composable
private fun AboutSignal() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.about_signal), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.about_signal_text),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
