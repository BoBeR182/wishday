package io.github.wishday

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
import io.github.wishday.ui.EditPersonScreen
import io.github.wishday.ui.PeopleScreen
import io.github.wishday.ui.SettingsScreen
import io.github.wishday.ui.WishdayViewModel
import io.github.wishday.ui.theme.WishdayTheme
import io.github.wishday.work.BirthdayWorker

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Notifications(this).createChannels()
        BirthdayWorker.schedule(this)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            WishdayTheme {
                val vm: WishdayViewModel = viewModel()
                // Screen encoding: "list", "settings", "edit:<id>" or "edit:new".
                var screen by rememberSaveable { mutableStateOf("list") }
                val back = { screen = "list" }
                BackHandler(enabled = screen != "list", onBack = back)

                when {
                    screen == "settings" -> SettingsScreen(vm, onDone = back)
                    screen.startsWith("edit:") ->
                        EditPersonScreen(vm, screen.removePrefix("edit:").toLongOrNull(), onDone = back)
                    else -> PeopleScreen(
                        vm,
                        onAdd = { screen = "edit:new" },
                        onEdit = { screen = "edit:$it" },
                        onSettings = { screen = "settings" },
                    )
                }
            }
        }
    }
}
