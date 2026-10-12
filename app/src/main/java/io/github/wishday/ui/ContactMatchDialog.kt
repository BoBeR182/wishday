package io.github.wishday.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.wishday.contacts.ContactMatch
import io.github.wishday.ui.theme.BrandGradient
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Walks through contact matches one card at a time: Yes/No buttons, or swipe right/left.
 * Closing early still saves the ones already accepted.
 */
@Composable
fun ContactMatchDialog(
    matches: List<ContactMatch>,
    onDismiss: () -> Unit,
    /** The accepted matches, and whether to also add their birthdays to the contacts. */
    onConfirm: (List<ContactMatch>, Boolean) -> Unit,
) {
    var writeBirthdays by remember { mutableStateOf(true) }
    val accepted = remember(matches) { mutableStateListOf<ContactMatch>() }
    // One entry per answered card, so Undo can step back through yeses and noes alike.
    val history = remember(matches) { mutableStateListOf<Boolean>() }
    var index by remember(matches) { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun finish() = if (accepted.isEmpty()) onDismiss() else onConfirm(accepted.toList(), writeBirthdays)
    fun answer(yes: Boolean) {
        if (index >= matches.size) return
        if (yes) accepted += matches[index]
        history += yes
        index++
        scope.launch { offset.snapTo(0f) }
    }
    var swiping by remember { mutableStateOf(false) }
    fun swipe(yes: Boolean) {
        if (swiping) return // a quick double tap must not answer two cards
        swiping = true
        scope.launch {
            offset.animateTo(if (yes) 1400f else -1400f)
            answer(yes)
            swiping = false
        }
    }
    fun undo() {
        if (history.isEmpty() || swiping) return
        index--
        if (history.removeAt(history.lastIndex)) accepted.removeAt(accepted.lastIndex)
    }

    Dialog(onDismissRequest = ::finish, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandGradient)
                .safeDrawingPadding()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::finish) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                Text(
                    "Match with contacts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::undo, enabled = history.isNotEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo, "Undo",
                        tint = Color.White.copy(alpha = if (history.isEmpty()) 0.4f else 1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { index.toFloat() / matches.size },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
            )
            Text(
                if (index < matches.size) "${index + 1} of ${matches.size} · ${accepted.size} linked"
                else "${accepted.size} of ${matches.size} linked",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    "Also add their birthday to the contact",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = writeBirthdays, onCheckedChange = { writeBirthdays = it })
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (index < matches.size) {
                    MatchCard(
                        matches[index],
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = offset.value
                                rotationZ = offset.value / 40f
                            }
                            .pointerInput(index) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (offset.value.absoluteValue > size.width / 4) swipe(offset.value > 0)
                                        else scope.launch { offset.animateTo(0f) }
                                    },
                                ) { change, drag ->
                                    change.consume()
                                    scope.launch { offset.snapTo(offset.value + drag) }
                                }
                            },
                        lean = offset.value,
                    )
                } else {
                    Done(accepted.size)
                }
            }

            if (index < matches.size) {
                Text(
                    "Is this the right contact? Swipe or tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    RoundChoice(Icons.Default.Close, "No") { swipe(false) }
                    RoundChoice(Icons.Default.Check, "Yes") { swipe(true) }
                }
            } else {
                TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (accepted.isEmpty()) "Close" else "Save ${accepted.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: ContactMatch, lean: Float, modifier: Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
        ) {
            Text("In Wishday", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                match.person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text("↓", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp))
            Text("Contact", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                match.contact.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(match.contact.phone, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        // Stamp that fades in while dragging, like a yes/no verdict.
        val strength = (lean.absoluteValue / 300f).coerceIn(0f, 1f)
        if (strength > 0f) {
            val yes = lean > 0
            Text(
                if (yes) "YES" else "NO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (yes) Color(0xFF16A34A) else Color(0xFFDC2626),
                modifier = Modifier
                    .align(if (yes) Alignment.TopStart else Alignment.TopEnd)
                    .padding(16.dp)
                    .graphicsLayer { alpha = strength; rotationZ = if (yes) -12f else 12f }
                    .border(3.dp, if (yes) Color(0xFF16A34A) else Color(0xFFDC2626), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun RoundChoice(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Done(linked: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            if (linked == 0) "No changes" else "All done",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White,
        )
        Text(
            if (linked == 0) "Nobody was linked." else "$linked ${if (linked == 1) "person" else "people"} will get their phone number.",
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
