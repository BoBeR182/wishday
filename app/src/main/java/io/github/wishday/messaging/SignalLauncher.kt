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
    const val PACKAGE = "org.thoughtcrime.securesms"

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    /**
     * Best available intent, in order:
     * 1. Signal's `smsto:` handler, which opens the chat with that number and a draft.
     * 2. Signal's share sheet with the text, where the user picks the recipient.
     * 3. The system share sheet, if Signal isn't installed.
     */
    fun intentFor(context: Context, phone: String?, text: String): Intent {
        val pm = context.packageManager
        if (!phone.isNullOrBlank()) {
            val direct = Intent(Intent.ACTION_SENDTO, ("smsto:" + Uri.encode(phone)).toUri())
                .setPackage(PACKAGE)
                .putExtra("sms_body", text)
                .putExtra(Intent.EXTRA_TEXT, text)
            if (direct.resolveActivity(pm) != null) return direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val signalShare = Intent(share).setPackage(PACKAGE)
        if (signalShare.resolveActivity(pm) != null) return signalShare.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
