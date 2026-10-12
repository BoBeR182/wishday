package io.github.wishday.ui

import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                if (version != null) {
                    Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            HorizontalDivider()
            AboutRow(Icons.Outlined.Person, stringResource(R.string.made_by), "stuckless.net") {
                context.open("https://stuckless.net")
            }
            AboutRow(
                Icons.Outlined.Lock, stringResource(R.string.private_by_design),
                stringResource(R.string.private_by_design_text),
            )
            AboutRow(Icons.Outlined.Code, stringResource(R.string.source_code), stringResource(R.string.source_code_text)) { context.open(REPO) }
            AboutRow(Icons.Outlined.BugReport, stringResource(R.string.report_problem), stringResource(R.string.report_problem_text)) {
                context.open("$REPO/issues")
            }
            AboutRow(Icons.Outlined.Gavel, stringResource(R.string.license), stringResource(R.string.license_text)) {
                context.open("$REPO/blob/main/LICENSE")
            }
            AboutRow(Icons.Outlined.Favorite, stringResource(R.string.inspired_by_birday), stringResource(R.string.inspired_by_birday_text)) {
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
        Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_SHORT).show()
    }
}
