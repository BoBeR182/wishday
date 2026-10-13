package io.github.wishday.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.wishday.R
import io.github.wishday.birthday.Birthdays
import io.github.wishday.data.AppDatabase
import io.github.wishday.data.Channel
import io.github.wishday.data.Person
import io.github.wishday.data.Settings
import io.github.wishday.messaging.SmsSender
import io.github.wishday.notify.Notifications
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Runs once a day at the configured time: sends SMS, prompts for Signal, posts reminders. */
class BirthdayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        val dao = AppDatabase.get(applicationContext).people()
        val notifications = Notifications(applicationContext)
        val sms = SmsSender(applicationContext)
        val today = LocalDate.now()
        val firstRunToday = settings.lastRunEpochDay != today.toEpochDay()
        val people = dao.getAll()

        // Days with no run at all (phone off, or the run slipped past midnight) must not pass silently.
        Birthdays.missed(people, settings.lastRunEpochDay, today).forEach { (person, day) -> notifications.missedBirthday(person, day) }
        for (person in people) {
            val days = Birthdays.daysUntil(person, today)
            when {
                // Claim first: only the run that marks them greeted gets to send.
                days == 0L && person.lastGreetedYear != today.year -> {
                    if (dao.markGreeted(person.id, today.year) == 1) greet(person, settings, sms, notifications, today)
                }
                // The early gift reminder comes first, so a person with both gets the gift note.
                firstRunToday && person.giftReminderDays != null && days == person.giftReminderDays.toLong() ->
                    notifications.giftReminder(person, days)
                firstRunToday && settings.remindDaysBefore > 0 && days == settings.remindDaysBefore.toLong() ->
                    notifications.upcoming(person, days)
            }
        }

        settings.lastRunEpochDay = today.toEpochDay()
        // A catch-up run sits outside the daily chain, so it must not extend it.
        if (!inputData.getBoolean(KEY_CATCH_UP, false)) schedule(applicationContext, fromWorker = true)
        return Result.success()
    }

    private fun greet(person: Person, settings: Settings, sms: SmsSender, notifications: Notifications, today: LocalDate) {
        val message = Birthdays.render(person.customMessage ?: settings.defaultMessage, person, today)
        when (person.channel) {
            Channel.SMS -> {
                if (person.phone.isNullOrBlank()) {
                    notifications.todayReminder(person, applicationContext.getString(R.string.notif_no_phone_no_sms))
                    return
                }
                // Success is confirmed (or retried) by SmsResultReceiver once the network answers.
                sms.send(person, message)
                    .onFailure { notifications.smsFailed(person, message, it.message ?: applicationContext.getString(R.string.sms_failed)) }
            }
            Channel.SIGNAL -> notifications.signalPrompt(person, message)
            Channel.WHATSAPP -> notifications.whatsAppPrompt(person, message)
            Channel.NONE -> notifications.todayReminder(person)
        }
    }

    companion object {
        private const val WORK_NAME = "daily-birthday-check"
        private const val CATCH_UP_NAME = "catch-up-birthday-check"
        private const val KEY_CATCH_UP = "catch_up"

        /**
         * Runs the check now if today's send time has already passed, so someone whose
         * birthday is today, added or edited after the daily run, still gets greeted.
         */
        fun catchUpIfDue(context: Context) {
            val settings = Settings(context)
            val now = LocalDateTime.now()
            if (now.isBefore(now.toLocalDate().atTime(settings.sendHour, settings.sendMinute))) return
            val request = OneTimeWorkRequestBuilder<BirthdayWorker>()
                .setInputData(workDataOf(KEY_CATCH_UP to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(CATCH_UP_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Schedules the next daily check. If today's check time has passed but the check
         * hasn't run yet (phone was off, app just installed), it runs right away.
         */
        fun schedule(context: Context, fromWorker: Boolean = false) {
            val settings = Settings(context)
            val now = LocalDateTime.now()
            val todayAt = now.toLocalDate().atTime(settings.sendHour, settings.sendMinute)
            val ranToday = settings.lastRunEpochDay == now.toLocalDate().toEpochDay()
            val next = when {
                now.isBefore(todayAt) -> todayAt
                !ranToday -> now
                else -> todayAt.plusDays(1)
            }
            val request = OneTimeWorkRequestBuilder<BirthdayWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            // From inside the worker, REPLACE would cancel the run that's finishing; append instead.
            val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
