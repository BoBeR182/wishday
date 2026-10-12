package io.github.wishday

import io.github.wishday.ui.theme.LocalIsDark
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.activity.compose.LocalActivity
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wishday.notify.Notifications
import io.github.wishday.ui.AboutScreen
import io.github.wishday.ui.EditPersonScreen
import io.github.wishday.ui.PeopleScreen
import io.github.wishday.ui.SettingsScreen
import io.github.wishday.ui.StatsScreen
import io.github.wishday.ui.WishdayViewModel
import io.github.wishday.ui.theme.WishdayTheme
import io.github.wishday.work.BirthdayWorker
import androidx.lifecycle.lifecycleScope
import io.github.wishday.data.AppDatabase
import io.github.wishday.data.Channel
import io.github.wishday.messaging.SmsSender
import io.github.wishday.ui.markSmsAsked
import kotlinx.coroutines.launch

/** Keeps status/navigation bar icons readable when the app's light/dark mode differs from the system's. */
@Composable
private fun SystemBarIcons() {
    val activity = LocalActivity.current ?: return
    val dark = LocalIsDark.current
    LaunchedEffect(dark) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

class MainActivity : ComponentActivity() {

    /** Notifications, plus SMS when someone is set to be texted without it (e.g. after a CSV import). */
    private fun requestStartupPermissions() = lifecycleScope.launch {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            val textsSomeone = AppDatabase.get(this@MainActivity).people().getAll().any { it.channel == Channel.SMS }
            if (textsSomeone && !SmsSender(this@MainActivity).hasPermission()) {
                add(Manifest.permission.SEND_SMS)
                markSmsAsked(this@MainActivity)
            }
        }
        if (wanted.isNotEmpty()) startupPermissions.launch(wanted.toTypedArray())
    }

    private val startupPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Notifications(this).createChannels()
        BirthdayWorker.schedule(this)
        if (savedInstanceState == null) requestStartupPermissions()

        setContent {
            val vm: WishdayViewModel = viewModel()
            val appearance by vm.appearance.collectAsState()
            WishdayTheme(themeId = appearance.themeId, darkMode = appearance.darkMode) {
                SystemBarIcons()
                // Screen encoding: "list", "settings", "stats", "about:<screen to return to>", "edit:<id>" or "edit:new".
                var screen by rememberSaveable { mutableStateOf("list") }
                val back = { screen = if (screen.startsWith("about:")) screen.removePrefix("about:") else "list" }
                BackHandler(enabled = screen != "list", onBack = back)

                when {
                    screen == "settings" -> SettingsScreen(vm, onDone = back, onAbout = { screen = "about:settings" })
                    screen.startsWith("about:") -> AboutScreen(onDone = back)
                    screen == "stats" -> StatsScreen(vm, onDone = back, onOpen = { screen = "edit:$it" })
                    screen.startsWith("edit:") ->
                        EditPersonScreen(vm, screen.removePrefix("edit:").toLongOrNull(), onDone = back)
                    else -> PeopleScreen(
                        vm,
                        onAdd = { screen = "edit:new" },
                        onEdit = { screen = "edit:$it" },
                        onSettings = { screen = "settings" },
                        onStats = { screen = "stats" },
                    )
                }
            }
        }
    }
}
