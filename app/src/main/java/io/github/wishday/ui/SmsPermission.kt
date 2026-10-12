package io.github.wishday.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.wishday.R
import io.github.wishday.messaging.SmsSender

/** SEND_SMS for a screen. [granted] is kept current, including after a trip to Settings. */
class SmsPermission internal constructor(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set
    internal var launch: ((Boolean) -> Unit) -> Unit = {}

    /** Asks unless already granted; [onResult] gets the outcome either way. */
    fun request(onResult: (Boolean) -> Unit = {}) {
        if (granted) onResult(true) else launch(onResult)
    }
}

@Composable
fun rememberSmsPermission(): SmsPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val state = remember { SmsPermission(SmsSender(context).hasPermission()) }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        state.granted = ok
        if (!ok) Toast.makeText(context, context.getString(R.string.sms_permission_denied), Toast.LENGTH_LONG).show()
        pending?.invoke(ok)
        pending = null
    }
    state.launch = { onResult ->
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS) == true
        if (prefs.getBoolean(KEY_ASKED, false) && !rationale) {
            // Denied twice: Android no longer shows the dialog, so Settings is the only way left.
            Toast.makeText(context, context.getString(R.string.sms_permission_settings), Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
            onResult(false)
        } else {
            markSmsAsked(context)
            pending = onResult
            launcher.launch(Manifest.permission.SEND_SMS)
        }
    }
    LifecycleResumeEffect(Unit) {
        state.granted = SmsSender(context).hasPermission()
        onPauseOrDispose {}
    }
    return state
}

/** Records that Android's SMS dialog was shown, so a later refusal can be told apart from never asking. */
fun markSmsAsked(context: Context) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }

private const val PREFS = "permissions"
private const val KEY_ASKED = "sms_asked"
