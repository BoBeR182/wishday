package io.github.wishday.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.wishday.R
import io.github.wishday.data.AppDatabase
import io.github.wishday.messaging.SmsSender
import io.github.wishday.notify.Notifications

/** Sends a birthday SMS again after the network rejected an earlier attempt. */
class SmsRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val person = AppDatabase.get(applicationContext).people().get(inputData.getLong(KEY_PERSON, -1))
            ?: return Result.success() // deleted in the meantime
        val message = inputData.getString(KEY_MESSAGE) ?: return Result.success()
        SmsSender(applicationContext).send(person, message, inputData.getInt(KEY_ATTEMPT, 2))
            .onFailure { Notifications(applicationContext).smsFailed(person, message, it.message ?: applicationContext.getString(R.string.sms_failed)) }
        return Result.success()
    }

    companion object {
        private const val KEY_PERSON = "person_id"
        private const val KEY_MESSAGE = "message"
        private const val KEY_ATTEMPT = "attempt"

        fun input(personId: Long, message: String, attempt: Int): Data =
            workDataOf(KEY_PERSON to personId, KEY_MESSAGE to message, KEY_ATTEMPT to attempt)
    }
}
