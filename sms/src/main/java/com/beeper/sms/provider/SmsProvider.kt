package com.beeper.sms.provider

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Sms.*
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.TimeMillis.Companion.toMillis
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.extensions.*
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid


class SmsProvider constructor(context: Context) {
    private val packageName = context.applicationInfo.packageName
    private val cr = context.contentResolver

    fun getLatest(thread: Long, limit: Int) = getSms(where = "$THREAD_ID = $thread", limit = limit)

    fun getMessagesAfter(thread: Long, timestamp: TimeMillis) =
        getSms("$THREAD_ID = $thread AND $DATE > ${timestamp.toLong()}")

    fun getMessagesAfter(timestamp: TimeMillis) = getSms("$DATE > ${timestamp.toLong()}")

    fun getMessage(uri: Uri) = getSms(uri).firstOrNull()

    private fun getSms(where: String): List<Message> =
        getSms(uri = CONTENT_URI, where = where)
            .plus(getSms(uri = Inbox.CONTENT_URI, where = where))
            .plus(getSms(uri = Sent.CONTENT_URI, where = where))
            .distinctBy { it.guid }

    private fun getSms(
        uri: Uri = CONTENT_URI,
        where: String? = null,
        limit: Int = 0,
    ): List<Message> =
        cr.map(
            uri = uri,
            where = where,
            order = if (limit > 0) "$DATE DESC LIMIT $limit" else null
        ) {
            val address = it.getString(ADDRESS)
            if (address == null) {
                Log.w(TAG, "Missing address: ${it.dumpCurrentRow()}")
                return@map null
            }
            val isFromMe = when (it.getInt(TYPE)) {
                MESSAGE_TYPE_OUTBOX, MESSAGE_TYPE_SENT -> true
                else -> false
            }
            val chatGuid = address.chatGuid
            val rowId = it.getLong(_ID)
            Message(
                guid = "$SMS_PREFIX$rowId",
                timestamp = it.getLong(DATE).toMillis().toSeconds(),
                subject = it.getString(SUBJECT) ?: "",
                text = it.getString(BODY) ?: "",
                chat_guid = chatGuid,
                sender_guid = if (isFromMe) null else chatGuid,
                is_from_me = isFromMe,
                sent_from_matrix = it.getString(CREATOR) == packageName,
                is_mms = false,
                thread = it.getLong(THREAD_ID),
                rowId = rowId,
                uri = if (where == null) uri else ContentUris.withAppendedId(uri, rowId),
            )
        }

    companion object {
        private const val TAG = "SmsProvider"
        const val SMS_PREFIX = "sms_"
    }
}
