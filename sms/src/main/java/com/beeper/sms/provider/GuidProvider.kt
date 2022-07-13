package com.beeper.sms.provider

import android.content.Context
import android.provider.Telephony.*
import android.telephony.PhoneNumberUtils
import android.util.Patterns
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getString
import java.net.URLEncoder
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

    fun getPhoneNumbersFromThreadId(thread: Long): List<String> {
        return getAddresses(thread)
            ?.mapNotNull { address -> getPhoneNumber(address) } ?: listOf()
    }

    fun getAddresses(thread: Long): List<String>? =
        cr.firstOrNull(URI_THREADS, "${Mms._ID} = $thread") {
            it.getString(ThreadsColumns.RECIPIENT_IDS)?.split(" ")
        }

    fun getPhoneNumber(recipient: String): String? =
        cr.firstOrNull(URI_ADDRESSES, "${CanonicalAddressesColumns._ID} = $recipient") {
            it.getString(CanonicalAddressesColumns.ADDRESS)
        }

    companion object {
        val URI_THREADS = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
        private val URI_ADDRESSES = "${MmsSms.CONTENT_URI}/canonical-addresses".toUri()
        private val EMAIL = Patterns.EMAIL_ADDRESS.toRegex()

        fun normalizeAddress(address : String) : String {
            // Return formatted numbers
            PhoneNumberUtils
                .formatNumberToE164(address, Locale.getDefault().country)
                ?.let { return it }

            // Return formatted emails
            EMAIL.find(address, 0)?.let { return it.value }
            
            // Didn't detect a number
            val addressWithoutDashes = address.filterNot {
                it=='-'
            }

            return if(addressWithoutDashes.isDigitsOnly()){
                addressWithoutDashes
            }else{
                address.replace("\"", "%22").replace("\'", "%27")
            }
        }

        val String.chatGuid: String
            get() = listOf(this).chatGuid

        val List<String>.chatGuid: String
            get() = "SMS;${if (size == 1) "-" else "+"};${joinToString(" ") { normalizeAddress(it) }}"
    }
}