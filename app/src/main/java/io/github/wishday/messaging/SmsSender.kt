package io.github.wishday.messaging

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import io.github.wishday.R
import io.github.wishday.data.Person
import io.github.wishday.data.Settings

/** Sends SMS directly in the background. Requires SEND_SMS. */
class SmsSender(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Hands the message to the radio. Every part reports to [SmsResultReceiver], which notifies
     * and, if nothing got through, retries; a failure here means it couldn't even be queued.
     */
    fun send(person: Person, text: String, attempt: Int = 1): Result<Unit> = runCatching {
        check(hasPermission()) { context.getString(R.string.sms_error_no_permission) }
        val phone = checkNotNull(person.phone?.takeIf { it.isNotBlank() }) { context.getString(R.string.sms_error_no_phone) }
        val sms = smsManager()
        val parts = sms.divideMessage(text)
        val sentIntents = ArrayList(parts.indices.map { SmsResultReceiver.pendingIntent(context, person.id, text, attempt, it, parts.size) })
        sms.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
    }

    /** The SIM chosen in Settings, or the phone's default for SMS if none is chosen or it's gone. */
    private fun smsManager(): SmsManager {
        val subscriptionId = Settings(context).smsSubscriptionId.takeIf { isActive(it) }
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val default = context.getSystemService(SmsManager::class.java)
                if (subscriptionId != null) default.createForSubscriptionId(subscriptionId) else default
            }
            subscriptionId != null -> @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            else -> @Suppress("DEPRECATION") SmsManager.getDefault()
        }
    }

    // Reading SIMs needs READ_PHONE_STATE, which is only asked for when picking a SIM in Settings.
    @SuppressLint("MissingPermission")
    private fun isActive(subscriptionId: Int): Boolean =
        subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
            runCatching {
                context.getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfo(subscriptionId) != null
            }.getOrDefault(false)
}
