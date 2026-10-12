package io.github.wishday.data

import android.content.Context
import androidx.core.content.edit
import io.github.wishday.R

/** Small user preferences, kept in SharedPreferences. */
class Settings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The template used when a person has no custom message. Unchanged, it follows the app's language. */
    var defaultMessage: String
        get() = prefs.getString(KEY_MESSAGE, null)?.takeUnless { it == LEGACY_DEFAULT_MESSAGE }
            ?: app.getString(R.string.default_message)
        set(value) = prefs.edit {
            if (value.isBlank() || value == app.getString(R.string.default_message)) remove(KEY_MESSAGE)
            else putString(KEY_MESSAGE, value)
        }

    /** Local time of day the daily check runs and SMS greetings go out. */
    var sendHour: Int
        get() = prefs.getInt(KEY_HOUR, 9)
        set(value) = prefs.edit { putInt(KEY_HOUR, value) }

    var sendMinute: Int
        get() = prefs.getInt(KEY_MINUTE, 0)
        set(value) = prefs.edit { putInt(KEY_MINUTE, value) }

    /** Remind this many days ahead of a birthday; 0 disables advance reminders. */
    var remindDaysBefore: Int
        get() = prefs.getInt(KEY_DAYS_BEFORE, 1)
        set(value) = prefs.edit { putInt(KEY_DAYS_BEFORE, value) }

    /** Color theme id (see Palettes), or "wallpaper". */
    var themeId: String
        get() = prefs.getString(KEY_THEME, null) ?: "sunset"
        set(value) = prefs.edit { putString(KEY_THEME, value) }

    /** "SYSTEM", "LIGHT" or "DARK". */
    var darkMode: String
        get() = prefs.getString(KEY_DARK_MODE, null) ?: "SYSTEM"
        set(value) = prefs.edit { putString(KEY_DARK_MODE, value) }

    /** Epoch day of the last completed daily check, or -1 if it never ran. */
    var lastRunEpochDay: Long
        get() = prefs.getLong(KEY_LAST_RUN, -1)
        set(value) = prefs.edit { putLong(KEY_LAST_RUN, value) }

    companion object {
        /** Older versions saved the English default itself; treat it as "not customised". */
        private const val LEGACY_DEFAULT_MESSAGE = "Happy birthday, {name}! 🎉 Hope you have a great day."
        private const val KEY_MESSAGE = "default_message"
        private const val KEY_HOUR = "send_hour"
        private const val KEY_MINUTE = "send_minute"
        private const val KEY_DAYS_BEFORE = "remind_days_before"
        private const val KEY_LAST_RUN = "last_run_epoch_day"
        private const val KEY_THEME = "theme"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
