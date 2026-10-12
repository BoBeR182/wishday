package io.github.wishday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wishday.data.Person
import io.github.wishday.ui.theme.BrandGradient
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Adds someone with just a name and a date. "More options" saves the draft and opens
 * the full editor for a phone number, channel and message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(vm: WishdayViewModel, onDismiss: () -> Unit, onMoreOptions: (Long?) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var name by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var day by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    val dayValue = day.toIntOrNull()?.takeIf { it in 1..YearMonth.of(2000, month).lengthOfMonth() }
    val yearValue = year.toIntOrNull()?.takeIf { it in 1800..today.year }
    val valid = name.isNotBlank() && dayValue != null && (year.isBlank() || yearValue != null)

    fun draft() = Person(name = name.trim(), month = month, day = dayValue ?: 1, year = yearValue)
    fun close(then: () -> Unit) = scope.launch { sheet.hide() }.invokeOnCompletion { then() }
    fun add() {
        if (!valid) return
        vm.save(draft()) // view-model scope: survives the sheet closing
        close(onDismiss)
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quick add", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthPicker(month, { month = it }, Modifier.weight(1.6f))
                OutlinedTextField(
                    value = day, onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Day") }, singleLine = true, isError = day.isNotEmpty() && dayValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(0.9f),
                )
                OutlinedTextField(
                    value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Year") }, placeholder = { Text("opt.") }, singleLine = true,
                    isError = year.isNotEmpty() && yearValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (valid) {
                        scope.launch {
                            val id = vm.saveNow(draft())
                            close { onMoreOptions(id) }
                        }
                    } else {
                        close { onMoreOptions(null) }
                    }
                }) { Text("More options") }
                Box(Modifier.weight(1f))
                GradientButton("Add birthday", enabled = valid, onClick = ::add)
            }
        }
    }
}

/** A pill button filled with the brand gradient. */
@Composable
fun GradientButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(50))
            .background(BrandGradient)
            .height(48.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
    }
}
