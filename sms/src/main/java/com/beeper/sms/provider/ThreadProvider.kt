package com.beeper.sms.provider

import android.content.Context
import android.net.Uri
import android.provider.Telephony.*
import android.telephony.PhoneNumberUtils
import androidx.core.net.toUri
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getLong
import com.beeper.sms.extensions.getString
import com.beeper.sms.extensions.map
import java.util.*

class ThreadProvider constructor(context: Context) {
    private val cr = context.contentResolver

    fun getMessagesAfter(thread: Long, timestampSeconds: Long): List<Pair<Long, Boolean>> =
        getMessagesAfter(thread, timestampSeconds * 1000, false)
            .plus(getMessagesAfter(thread, timestampSeconds, true))
            .sortedBy { it.first }
            .map { Pair(it.second, it.third) }

    fun getRecentMessages(thread: Long, limit: Int): List<Pair<Long, Boolean>> =
        getRecentMessages(thread, limit, false)
            .plus(getRecentMessages(thread, limit, true))
            .sortedBy { it.first }
            .takeLast(limit)
            .map { Pair(it.second, it.third) }

    fun getChatGuid(thread: Long): String? =
        getAddresses(thread)
            ?.mapNotNull { addr -> getPhoneNumber(addr) }
            ?.takeIf { it.isNotEmpty() }
            ?.chatGuid

    private fun getRecentMessages(thread: Long, limit: Int, mms: Boolean) =
        getMessages(thread, mms.isMms, "LIMIT $limit")

    private fun getMessagesAfter(thread: Long, timestamp: Long, mms: Boolean) =
        getMessages(thread, "${mms.isMms} AND ${Sms.Conversations.DATE} > $timestamp")

    private fun getMessages(thread: Long, where: String, limit: String = "") =
        cr.map(
            Uri.withAppendedPath(MmsSms.CONTENT_CONVERSATIONS_URI, thread.toString()),
            where = where,
            projection = PROJECTION,
            order = "${Sms.Conversations.DATE} DESC $limit"
        ) {
            val mms = it.getString(MmsSms.TYPE_DISCRIMINATOR_COLUMN) == "mms"
            Triple(
                it.getLong(Sms.Conversations.DATE) * if (mms) 1000 else 1,
                it.getLong(Sms.Conversations._ID),
                mms
            )
        }

    private fun getAddresses(thread: Long): List<String>? =
        cr.firstOrNull(URI_THREADS, "${Mms._ID} = $thread") {
            it.getString(ThreadsColumns.RECIPIENT_IDS)?.split(" ")
        }

    private fun getPhoneNumber(recipient: String): String? =
        cr.firstOrNull(URI_ADDRESSES, "${Mms._ID} = $recipient") {
            it.getString(Mms.Addr.ADDRESS)
        }

    companion object {
        val URI_THREADS = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
        private val URI_ADDRESSES = "${MmsSms.CONTENT_URI}/canonical-addresses".toUri()
        private val PROJECTION = arrayOf(
            Sms.Conversations._ID,
            Sms.Conversations.DATE,
            MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        )

        val Boolean.isMms: String
            get() = "${MmsSms.TYPE_DISCRIMINATOR_COLUMN} ${if (this) "=" else "!="} 'mms'"

        internal val String.normalize: String
            get() =
                PhoneNumberUtils
                    .formatNumberToE164(this, Locale.getDefault().country)
                    ?.takeIf { it != this }
                    ?: filterNot { it.isWhitespace() }

        val String.chatGuid: String
            get() = listOf(this).chatGuid

        val List<String>.chatGuid: String
            get() = "SMS;${if (size == 1) "-" else "+"};${joinToString(" ") { it.normalize }}"
    }
}