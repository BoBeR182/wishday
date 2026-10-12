package io.github.wishday.messaging

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.wishday.R
import io.github.wishday.data.AppDatabase
import io.github.wishday.notify.Notifications
import io.github.wishday.work.SmsRetryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Receives the network's result for a sent SMS: confirms it, or schedules a retry, or gives up. */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val personId = intent.getLongExtra(EXTRA_PERSON, -1)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val code = resultCode
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val person = AppDatabase.get(app).people().get(personId) ?: return@launch
                val notifications = Notifications(app)
                when {
                    code == Activity.RESULT_OK -> notifications.smsSent(person, message)
                    attempt < MAX_ATTEMPTS -> {
                        val retry = OneTimeWorkRequestBuilder<SmsRetryWorker>()
                            .setInitialDelay(RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                            .setInputData(SmsRetryWorker.input(personId, message, attempt + 1))
                            .build()
                        WorkManager.getInstance(app).enqueue(retry)
                        notifications.smsRetrying(person, message, reason(app, code), RETRY_DELAY_MINUTES)
                    }
                    else -> notifications.smsFailed(person, message, reason(app, code))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 4
        private const val RETRY_DELAY_MINUTES = 15L
        private const val EXTRA_PERSON = "person_id"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_ATTEMPT = "attempt"

        fun pendingIntent(context: Context, personId: Long, message: String, attempt: Int): PendingIntent {
            val intent = Intent(context, SmsResultReceiver::class.java)
                .putExtra(EXTRA_PERSON, personId)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_ATTEMPT, attempt)
            return PendingIntent.getBroadcast(
                context,
                (personId * MAX_ATTEMPTS + attempt).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun reason(context: Context, code: Int): String = when (code) {
            SmsManager.RESULT_ERROR_NO_SERVICE -> context.getString(R.string.sms_error_no_service)
            SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.sms_error_radio_off)
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> context.getString(R.string.sms_error_limit)
            else -> context.getString(R.string.sms_error_network, code)
        }
    }
}
