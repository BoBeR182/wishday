package io.github.wishday.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/**
 * Signal has no public API for third-party sending, so we open Signal with the
 * message prefilled and the user taps send.
 */
object SignalLauncher {
    /** Signal, then Molly (a Signal fork that uses the same accounts and intents). */
    private val PACKAGES = listOf("org.thoughtcrime.securesms" to "Signal", "im.molly.app" to "Molly")

    private fun installed(context: Context): Pair<String, String>? =
        PACKAGES.firstOrNull { (pkg, _) -> context.packageManager.getLaunchIntentForPackage(pkg) != null }

    fun isInstalled(context: Context): Boolean = installed(context) != null

    /** "Signal" or "Molly", whichever is installed; "Signal" when neither is. */
    fun appName(context: Context): String = installed(context)?.second ?: "Signal"

    /**
     * Best available intent, in order:
     * 1. Signal's `smsto:` handler, which opens the chat with that number and a draft.
     * 2. Signal's share sheet with the text, where the user picks the recipient.
     * 3. The system share sheet, if Signal isn't installed.
     */
    fun intentFor(context: Context, phone: String?, text: String): Intent {
        val pm = context.packageManager
        val pkg = installed(context)?.first
            ?: return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!phone.isNullOrBlank()) {
            val direct = Intent(Intent.ACTION_SENDTO, ("smsto:" + Uri.encode(phone)).toUri())
                .setPackage(pkg)
                .putExtra("sms_body", text)
                .putExtra(Intent.EXTRA_TEXT, text)
            if (direct.resolveActivity(pm) != null) return direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val signalShare = Intent(share).setPackage(pkg)
        if (signalShare.resolveActivity(pm) != null) return signalShare.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
