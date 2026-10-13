package io.github.wishday.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.net.toUri
import java.util.Locale

/**
 * Like Signal, WhatsApp can't be sent to by other apps, so we open the chat with
 * the message prefilled and the user taps send.
 */
object WhatsAppLauncher {
    /** WhatsApp, then WhatsApp Business. */
    private val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    private fun installed(context: Context): String? =
        PACKAGES.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun isInstalled(context: Context): Boolean = installed(context) != null

    /**
     * Opens the chat with [phone] with [text] typed in, or WhatsApp's share sheet when there's no
     * number. Without WhatsApp installed it's Android's share sheet instead: never the web page.
     */
    fun intentFor(context: Context, phone: String?, text: String): Intent {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        val pkg = installed(context)
            ?: return Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val number = phone?.let { internationalDigits(context, it) }
        val intent = if (number != null) {
            Intent(Intent.ACTION_VIEW, "https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(text)}".toUri())
        } else {
            share
        }
        return intent.setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * WhatsApp wants the full international number as digits ("15195550123"). Local numbers
     * get the country code of the phone's SIM or network.
     */
    private fun internationalDigits(context: Context, phone: String): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val country = (telephony?.simCountryIso?.ifBlank { null } ?: telephony?.networkCountryIso?.ifBlank { null }
            ?: Locale.getDefault().country).uppercase(Locale.ROOT)
        val e164 = PhoneNumberUtils.formatNumberToE164(phone, country)
            ?: phone.takeIf { it.trim().startsWith("+") }
        return e164?.filter(Char::isDigit)?.ifEmpty { null }
    }
}
