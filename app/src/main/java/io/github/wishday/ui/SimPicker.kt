package io.github.wishday.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.wishday.R

private data class Sim(val subscriptionId: Int, val label: String)

/** "Send texts from": which SIM sends birthday SMS. Shown only on phones with more than one SIM slot. */
@Composable
fun SmsSimSetting(vm: WishdayViewModel) {
    val context = LocalContext.current
    if (simSlots(context) < 2) return
    var chosen by remember { mutableIntStateOf(vm.settings.smsSubscriptionId) }
    var sims by remember { mutableStateOf<List<Sim>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sims = activeSims(context)
        else Toast.makeText(context, context.getString(R.string.sim_permission_needed), Toast.LENGTH_LONG).show()
    }
    val canRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED
    // Without permission the chosen SIM can't be named, but the choice still stands.
    val current = if (canRead) activeSims(context).firstOrNull { it.subscriptionId == chosen }?.label else null

    ListItem(
        modifier = Modifier.clickable {
            if (canRead) sims = activeSims(context) else permission.launch(Manifest.permission.READ_PHONE_STATE)
        },
        headlineContent = { Text(stringResource(R.string.send_texts_from)) },
        supportingContent = { Text(current ?: stringResource(R.string.sim_default)) },
    )

    sims?.let { list ->
        AlertDialog(
            onDismissRequest = { sims = null },
            title = { Text(stringResource(R.string.send_texts_from)) },
            text = {
                Column {
                    val options = listOf(Sim(-1, stringResource(R.string.sim_default))) + list
                    options.forEach { sim ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosen = sim.subscriptionId
                                    vm.settings.smsSubscriptionId = sim.subscriptionId
                                    sims = null
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = chosen == sim.subscriptionId, onClick = null)
                            Text(sim.label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sims = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun simSlots(context: Context): Int {
    val telephony = context.getSystemService(TelephonyManager::class.java) ?: return 0
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephony.activeModemCount
    else @Suppress("DEPRECATION") telephony.phoneCount
}

/** Active SIMs as "SIM 1 · Carrier". Needs READ_PHONE_STATE. */
@SuppressLint("MissingPermission")
private fun activeSims(context: Context): List<Sim> =
    runCatching {
        context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().map { info ->
            val slot = context.getString(R.string.sim_slot, info.simSlotIndex + 1)
            val name = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: info.carrierName?.toString()
            Sim(info.subscriptionId, if (name.isNullOrBlank()) slot else "$slot · $name")
        }
    }.getOrDefault(emptyList())
