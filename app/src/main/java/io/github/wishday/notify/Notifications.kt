package io.github.wishday.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.wishday.MainActivity
import io.github.wishday.R
import io.github.wishday.data.Person
import io.github.wishday.messaging.SignalLauncher
import io.github.wishday.messaging.WhatsAppLauncher
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class Notifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_TODAY, context.getString(R.string.channel_today), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_UPCOMING, context.getString(R.string.channel_upcoming), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SENT, context.getString(R.string.channel_sent), NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    /** Today's birthday, delivered over Signal: tapping opens Signal with the message ready. */
    fun signalPrompt(person: Person, message: String) =
        appPrompt(person, message, SignalLauncher.appName(context), SignalLauncher.intentFor(context, person.phone, message))

    /** Today's birthday, delivered over WhatsApp: tapping opens the chat with the message ready. */
    fun whatsAppPrompt(person: Person, message: String) =
        appPrompt(person, message, "WhatsApp", WhatsAppLauncher.intentFor(context, person.phone, message))

    private fun appPrompt(person: Person, message: String, app: String, intent: Intent) {
        val tap = PendingIntent.getActivity(
            context,
            requestCode(person, KIND_TODAY),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            person, KIND_TODAY, CH_TODAY,
            title = context.getString(R.string.notif_birthday_today, person.name),
            text = context.getString(R.string.notif_tap_to_send, app, message),
            tap = tap,
        )
    }

    /** Today's birthday with no automatic channel: a plain reminder. */
    fun todayReminder(person: Person, note: String? = null) {
        post(
            person, KIND_TODAY, CH_TODAY,
            title = context.getString(R.string.notif_birthday_today, person.name),
            text = note ?: context.getString(R.string.notif_dont_forget),
            tap = openApp(),
        )
    }

    /** Their birthday passed on [day] without a run, so no greeting or reminder went out. */
    fun missedBirthday(person: Person, day: LocalDate) {
        post(
            person, KIND_MISSED, CH_TODAY,
            title = context.getString(R.string.notif_missed_title, person.name),
            text = context.getString(
                R.string.notif_missed_text,
                day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            ),
            tap = openApp(),
        )
    }

    /** The person's own early reminder, to leave time for a gift. */
    fun giftReminder(person: Person, days: Long) {
        val res = context.resources
        val whenText = when {
            days == 1L -> context.getString(R.string.when_tomorrow)
            days % 7 == 0L -> res.getQuantityString(R.plurals.when_in_weeks, (days / 7).toInt(), (days / 7).toInt())
            else -> res.getQuantityString(R.plurals.when_in_days, days.toInt(), days.toInt())
        }
        post(
            person, KIND_UPCOMING, CH_UPCOMING,
            title = context.getString(R.string.notif_gift_title, person.name, whenText),
            text = person.notes?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.notif_gift_ideas, it) }
                ?: context.getString(R.string.notif_gift_default),
            tap = openApp(),
        )
    }

    fun upcoming(person: Person, days: Long) {
        val whenText = if (days == 1L) {
            context.getString(R.string.when_tomorrow)
        } else {
            context.resources.getQuantityString(R.plurals.when_in_days, days.toInt(), days.toInt())
        }
        post(
            person, KIND_UPCOMING, CH_UPCOMING,
            title = context.getString(R.string.notif_upcoming_title, person.name, whenText),
            text = person.notes?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.notif_notes, it) }
                ?: context.getString(R.string.notif_upcoming_default),
            tap = openApp(),
        )
    }

    fun smsSent(person: Person, message: String) {
        post(person, KIND_SMS, CH_SENT, title = context.getString(R.string.notif_sms_sent, person.name), text = message, tap = openApp())
    }

    /** The network rejected an SMS; another attempt is scheduled. */
    fun smsRetrying(person: Person, message: String, reason: String, minutes: Long) {
        post(
            person, KIND_SMS, CH_SENT,
            title = context.getString(R.string.notif_sms_retry_title, person.name),
            text = context.resources.getQuantityString(R.plurals.notif_sms_retry_text, minutes.toInt(), minutes.toInt(), reason),
            tap = openApp(),
        )
    }

    /** SMS couldn't be sent; tapping opens the default SMS app with the message prefilled. */
    fun smsFailed(person: Person, message: String, reason: String) {
        val compose = Intent(Intent.ACTION_SENDTO, ("smsto:" + Uri.encode(person.phone.orEmpty())).toUri())
            .putExtra("sms_body", message)
        val tap = PendingIntent.getActivity(
            context,
            requestCode(person, KIND_SMS),
            compose,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            person, KIND_SMS, CH_TODAY,
            title = context.getString(R.string.notif_sms_failed_title, person.name),
            text = context.getString(R.string.notif_sms_failed_text, reason),
            tap = tap,
        )
    }

    private fun post(person: Person, kind: Int, channel: String, title: String, text: String, tap: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_cake)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(requestCode(person, kind), notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun requestCode(person: Person, kind: Int) = (person.id * 4 + kind).toInt()

    private companion object {
        const val CH_TODAY = "today"
        const val CH_UPCOMING = "upcoming"
        const val CH_SENT = "sent"
        const val KIND_MISSED = 0
        const val KIND_TODAY = 1
        const val KIND_UPCOMING = 2
        const val KIND_SMS = 3
    }
}
