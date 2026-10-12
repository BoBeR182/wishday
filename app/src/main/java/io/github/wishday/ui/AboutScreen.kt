package io.github.wishday.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.wishday.R

private const val REPO = "https://github.com/cybersecurityhandle/wishday"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon()
                Text("Wishday", style = MaterialTheme.typography.headlineMedium)
                if (version != null) {
                    Text("Version $version", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Remembers birthdays and helps you wish people a happy birthday, " +
                        "automatically by SMS or with one tap on Signal.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            HorizontalDivider()
            AboutRow(Icons.Outlined.Person, "Made by Daniel Stuckless", "stuckless.net") {
                context.open("https://stuckless.net")
            }
            AboutRow(
                Icons.Outlined.Lock, "Private by design",
                "No internet access, no accounts, no tracking. Your birthdays never leave this phone " +
                    "unless you export them.",
            )
            AboutRow(Icons.Outlined.Code, "Source code", "Free and open source on GitHub") { context.open(REPO) }
            AboutRow(Icons.Outlined.BugReport, "Report a problem or suggest a feature", "Opens GitHub issues") {
                context.open("$REPO/issues")
            }
            AboutRow(Icons.Outlined.Gavel, "License", "GNU General Public License v3.0 or later") {
                context.open("$REPO/blob/main/LICENSE")
            }
            AboutRow(Icons.Outlined.Favorite, "Inspired by Birday", "A lovely open-source birthday app") {
                context.open("https://github.com/m-i-n-a-r/birday")
            }
        }
    }
}

/** The launcher icon, drawn here because adaptive icons can't be loaded as a Compose painter. */
@Composable
private fun AppIcon() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF7A59), Color(0xFF7B2CBF)))),
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.fillMaxSize())
    }
}

@Composable
private fun AboutRow(icon: ImageVector, title: String, detail: String, onClick: (() -> Unit)? = null) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
    )
}

/** Opens a link in the browser; no INTERNET permission is needed for this. */
private fun Context.open(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No browser installed", Toast.LENGTH_SHORT).show()
    }
}
