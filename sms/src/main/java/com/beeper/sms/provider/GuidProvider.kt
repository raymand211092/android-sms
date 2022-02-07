package com.beeper.sms.provider

import android.content.Context
import android.provider.Telephony.*
import android.telephony.PhoneNumberUtils
import android.util.Patterns
import androidx.core.net.toUri
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getString
import java.util.*

class GuidProvider constructor(
    context: Context,
) {
    private val cr = context.contentResolver

    fun getChatGuid(thread: Long): String? =
        getAddresses(thread)
            ?.mapNotNull { addr -> getPhoneNumber(addr) }
            ?.takeIf { it.isNotEmpty() }
            ?.chatGuid

    private fun getAddresses(thread: Long): List<String>? =
        cr.firstOrNull(URI_THREADS, "${Mms._ID} = $thread") {
            it.getString(ThreadsColumns.RECIPIENT_IDS)?.split(" ")
        }

    private fun getPhoneNumber(recipient: String): String? =
        cr.firstOrNull(URI_ADDRESSES, "${CanonicalAddressesColumns._ID} = $recipient") {
            it.getString(CanonicalAddressesColumns.ADDRESS)
        }

    companion object {
        val URI_THREADS = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
        private val URI_ADDRESSES = "${MmsSms.CONTENT_URI}/canonical-addresses".toUri()
        private val EMAIL = Patterns.EMAIL_ADDRESS.toRegex()

        internal val String.normalize: String
            get() {
                PhoneNumberUtils
                    .formatNumberToE164(this, Locale.getDefault().country)
                    ?.let { return it }

                EMAIL.find(this, 0)?.let { return it.value }

                return filterNot { it.isWhitespace() || it == '"' || it == '\'' }
            }

        val String.chatGuid: String
            get() = listOf(this).chatGuid

        val List<String>.chatGuid: String
            get() = "SMS;${if (size == 1) "-" else "+"};${joinToString(" ") { it.normalize }}"
    }
}