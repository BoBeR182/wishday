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

class Notifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_TODAY, "Birthdays today", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_UPCOMING, "Upcoming birthdays", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SENT, "Sent messages", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    /** Today's birthday, delivered over Signal: tapping opens Signal with the message ready. */
    fun signalPrompt(person: Person, message: String) {
        val tap = PendingIntent.getActivity(
            context,
            requestCode(person, KIND_TODAY),
            SignalLauncher.intentFor(context, person.phone, message),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            person, KIND_TODAY, CH_TODAY,
            title = "🎂 It's ${person.name}'s birthday",
            text = "Tap to send on Signal: $message",
            tap = tap,
        )
    }

    /** Today's birthday with no automatic channel: a plain reminder. */
    fun todayReminder(person: Person, note: String? = null) {
        post(
            person, KIND_TODAY, CH_TODAY,
            title = "🎂 It's ${person.name}'s birthday",
            text = note ?: "Don't forget to say happy birthday!",
            tap = openApp(),
        )
    }

    fun upcoming(person: Person, days: Long) {
        val whenText = if (days == 1L) "tomorrow" else "in $days days"
        post(
            person, KIND_UPCOMING, CH_UPCOMING,
            title = "${person.name}'s birthday is $whenText",
            text = "Wishday will remind you on the day.",
            tap = openApp(),
        )
    }

    fun smsSent(person: Person, message: String) {
        post(person, KIND_SMS, CH_SENT, title = "Sent birthday SMS to ${person.name}", text = message, tap = openApp())
    }

    /** The network rejected an SMS; another attempt is scheduled. */
    fun smsRetrying(person: Person, message: String, reason: String, minutes: Long) {
        post(
            person, KIND_SMS, CH_SENT,
            title = "Couldn't text ${person.name} yet",
            text = "$reason. Trying again in $minutes minutes.",
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
            title = "Couldn't text ${person.name}",
            text = "$reason. Tap to send it yourself.",
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
        const val KIND_TODAY = 1
        const val KIND_UPCOMING = 2
        const val KIND_SMS = 3
    }
}
