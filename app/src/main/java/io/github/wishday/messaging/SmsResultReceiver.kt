package io.github.wishday.messaging

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.core.content.edit
import androidx.core.net.toUri
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

/**
 * Receives the network's result for each part of a sent SMS. Once every part has reported:
 * all sent confirms it; none sent retries (nothing reached them, so resending can't duplicate);
 * some sent gives up with a notification, since resending would repeat the parts that arrived.
 */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val personId = intent.getLongExtra(EXTRA_PERSON, -1)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val parts = intent.getIntExtra(EXTRA_PARTS, 1)
        val app = context.applicationContext
        val outcome = record(app, "$personId:$attempt:${message.hashCode()}", parts, ok = resultCode == Activity.RESULT_OK, code = resultCode) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val person = AppDatabase.get(app).people().get(personId) ?: return@launch
                val notifications = Notifications(app)
                when {
                    outcome.sent == parts -> notifications.smsSent(person, message)
                    outcome.sent > 0 -> notifications.smsFailed(person, message, app.getString(R.string.sms_error_partial))
                    attempt < MAX_ATTEMPTS -> {
                        val retry = OneTimeWorkRequestBuilder<SmsRetryWorker>()
                            .setInitialDelay(RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                            .setInputData(SmsRetryWorker.input(personId, message, attempt + 1))
                            .build()
                        WorkManager.getInstance(app).enqueue(retry)
                        notifications.smsRetrying(person, message, reason(app, outcome.lastError), RETRY_DELAY_MINUTES)
                    }
                    else -> notifications.smsFailed(person, message, reason(app, outcome.lastError))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private class Outcome(val sent: Int, val lastError: Int)

    /**
     * Tallies one part's result for [key]; returns the outcome once all [parts] have reported,
     * or null while some are still pending. Broadcasts arrive one at a time on the main thread,
     * and commit() writes before returning, so the tally can't be lost between parts.
     */
    private fun record(context: Context, key: String, parts: Int, ok: Boolean, code: Int): Outcome? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reported = prefs.getInt("$key:reported", 0) + 1
        val sent = prefs.getInt("$key:sent", 0) + if (ok) 1 else 0
        val lastError = if (ok) prefs.getInt("$key:error", 0) else code
        if (reported < parts) {
            prefs.edit(commit = true) {
                putInt("$key:reported", reported)
                putInt("$key:sent", sent)
                putInt("$key:error", lastError)
            }
            return null
        }
        prefs.edit(commit = true) {
            remove("$key:reported")
            remove("$key:sent")
            remove("$key:error")
        }
        return Outcome(sent, lastError)
    }

    companion object {
        const val MAX_ATTEMPTS = 4
        private const val RETRY_DELAY_MINUTES = 15L
        private const val PREFS = "sms_results"
        private const val EXTRA_PERSON = "person_id"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_ATTEMPT = "attempt"
        private const val EXTRA_PARTS = "parts"

        fun pendingIntent(context: Context, personId: Long, message: String, attempt: Int, part: Int, parts: Int): PendingIntent {
            val intent = Intent(context, SmsResultReceiver::class.java)
                // A distinct data URI per part keeps each part's PendingIntent separate.
                .setData("wishday-sms://$personId/$attempt/${message.hashCode()}/$part".toUri())
                .putExtra(EXTRA_PERSON, personId)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_ATTEMPT, attempt)
                .putExtra(EXTRA_PARTS, parts)
            return PendingIntent.getBroadcast(
                context,
                0,
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
