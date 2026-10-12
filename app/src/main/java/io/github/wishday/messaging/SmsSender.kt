package io.github.wishday.messaging

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import io.github.wishday.R
import io.github.wishday.data.Person

/** Sends SMS directly in the background. Requires SEND_SMS. */
class SmsSender(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Hands the message to the radio. The network's verdict arrives later in
     * [SmsResultReceiver], which notifies and retries; a failure here means it couldn't even be queued.
     */
    fun send(person: Person, text: String, attempt: Int = 1): Result<Unit> = runCatching {
        check(hasPermission()) { context.getString(R.string.sms_error_no_permission) }
        val phone = checkNotNull(person.phone?.takeIf { it.isNotBlank() }) { context.getString(R.string.sms_error_no_phone) }
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = sms.divideMessage(text)
        // Only the last part reports back: it's sent after the others, and greetings are rarely multipart.
        val sentIntents = ArrayList<PendingIntent?>(List(parts.size - 1) { null })
        sentIntents += SmsResultReceiver.pendingIntent(context, person.id, text, attempt)
        sms.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
    }
}
