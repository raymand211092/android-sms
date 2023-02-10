package com.beeper.sms.provider

import android.content.Context
import android.provider.Telephony.*
import android.provider.Telephony.ThreadsColumns.RECIPIENT_IDS
import android.telephony.PhoneNumberUtils
import android.util.Patterns
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getString
import timber.log.Timber
import java.util.*


class GuidProvider constructor(
    context: Context,
) {
    private val cr = context.contentResolver

    fun getChatGuid(thread: Long): String? {
        val addresses = getAddresses(thread)
        return addresses?.mapNotNull { addr ->
                val number = getPhoneNumber(addr)
                number
            }
            ?.takeIf {
                it.isNotEmpty()
            }?.chatGuid
    }

    fun getPhoneNumbersFromThreadId(thread: Long): List<String> {
        return getAddresses(thread)
            ?.mapNotNull { address -> getPhoneNumber(address) } ?: listOf()
    }

    fun getAddresses(thread: Long): List<String>? =
        cr.firstOrNull(ALL_THREADS_URI, "${Threads._ID} = $thread", projection = listOf(RECIPIENT_IDS).toTypedArray()) {
            val recipientIds = it.getString(RECIPIENT_IDS)?.split(" ")
            Timber.tag("SMS-GuidProvider").d("getAddresses() recipientIds: $recipientIds")
            recipientIds
        }

    fun getPhoneNumber(recipient: String): String? =
        cr.firstOrNull(URI_ADDRESSES, "${CanonicalAddressesColumns._ID} = $recipient",
            projection = listOf(CanonicalAddressesColumns.ADDRESS).toTypedArray()) {
            it.getString(CanonicalAddressesColumns.ADDRESS)
        }

    companion object {
        private val ALL_THREADS_URI =
            Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        private val URI_ADDRESSES = "${MmsSms.CONTENT_URI}/canonical-addresses".toUri()
        private val EMAIL = Patterns.EMAIL_ADDRESS.toRegex()

        fun transformToServerCompatibleAddress(address : String) : String {
            // Return formatted numbers
            PhoneNumberUtils
                .formatNumberToE164(address, Locale.getDefault().country)
                ?.let { return it }

            // Return formatted emails
            EMAIL.find(address, 0)?.let { return it.value }

            // Didn't detect a number
            val addressWithEscapedSpaces = address.replace(" ", "%20")
            val addressWithoutDashes = addressWithEscapedSpaces.filterNot {
                it=='-'
            }

            return if(addressWithoutDashes.isDigitsOnly()){
                addressWithoutDashes
            }else{
                addressWithEscapedSpaces
                    .replace("\"", "%22")
                    .replace("\'", "%27")
            }
        }

        fun transformToPhoneCompatibleAddress(address : String) : String {
            // Return formatted numbers
            PhoneNumberUtils
                .formatNumberToE164(address, Locale.getDefault().country)
                ?.let { return it }

            // Return formatted emails
            EMAIL.find(address, 0)?.let { return it.value }

            return address.filterNot {
                it.isWhitespace()
            }
        }

        fun removeEscapingFromGuid(escapedAddress : String) : String {
            return escapedAddress
                .replace("%20", " ")
                .replace("%22","\"")
                .replace("%27", "\'")
        }

        val String.chatGuid: String
            get() = listOf(this).chatGuid

        val List<String>.chatGuid: String
            get() = "SMS;${if (size == 1) "-" else "+"};${joinToString(" ") { transformToServerCompatibleAddress(it) }}"
    }
}