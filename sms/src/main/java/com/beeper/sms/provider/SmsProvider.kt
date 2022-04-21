package com.beeper.sms.provider

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony.Sms.*
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.TimeMillis.Companion.toMillis
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageInfo
import com.beeper.sms.extensions.*
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid

class SmsProvider constructor(context: Context) {
    private val packageName = context.applicationInfo.packageName
    private val cr = context.contentResolver

    fun getActiveChats(timestamp: TimeMillis): List<MessageInfo> =
        getSms("$DATE > ${timestamp.toLong()}", this::messageInfoMapper)

    fun getMessageInfo(uri: Uri): MessageInfo? =
        getSms(uri, mapper = this::messageInfoMapper).firstOrNull()

    fun getLatest(thread: Long, limit: Int) =
        getSms(where = "$THREAD_ID = $thread", limit = limit, mapper = this::messageMapper)

    fun getMessagesAfter(thread: Long, timestamp: TimeMillis) =
        getSms(
            "$THREAD_ID = $thread AND $DATE > ${timestamp.toLong()} AND $CREATOR != '$packageName'",
            this::messageMapper
        )

    fun getMessage(uri: Uri) = getSms(uri, mapper = this::messageMapper).firstOrNull()

    private fun <T> getSms(where: String, mapper: (Cursor, Long, Uri) -> T?): List<T> =
        listOf(CONTENT_URI, Inbox.CONTENT_URI, Sent.CONTENT_URI).flatMap { uri ->
            getSms(uri = uri, where = where, mapper = mapper)
        }

    private fun <T> getSms(
        uri: Uri = CONTENT_URI,
        where: String? = null,
        limit: Int = 0,
        mapper: (Cursor, Long, Uri) -> T?
    ): List<T> =
        cr.map(
            uri = uri,
            where = where,
            order = if (limit > 0) "$DATE DESC LIMIT $limit" else null
        ) {
            val rowId = it.getLong(_ID)
            mapper(
                it,
                rowId,
                if (where == null) uri else ContentUris.withAppendedId(uri, rowId),
            )
        }

    private fun messageInfoMapper(it: Cursor, rowId: Long, uri: Uri): MessageInfo? {
        val address = it.getString(ADDRESS)
        if (address == null) {
            Log.e(TAG, "Missing address: ${it.dumpCurrentRow()}")
            return null
        }
        val creator = it.getString(CREATOR)
        return MessageInfo(
            "$SMS_PREFIX$rowId",
            it.getLong(DATE).toMillis().toSeconds(),
            address.chatGuid,
            uri,
            creator,
            creator == packageName,
        )
    }

    private fun messageMapper(it: Cursor, rowId: Long, uri: Uri): Message? {
        val messageInfo = messageInfoMapper(it, rowId, uri) ?: return null
        val isFromMe = when (it.getInt(TYPE)) {
            MESSAGE_TYPE_OUTBOX, MESSAGE_TYPE_SENT -> true
            else -> false
        }
        return Message(
            guid = messageInfo.guid,
            timestamp = messageInfo.timestamp,
            subject = it.getString(SUBJECT)?.takeUnless { sub -> sub == "NoSubject" } ?: "",
            text = it.getString(BODY) ?: "",
            chat_guid = messageInfo.chat_guid,
            sender_guid = if (isFromMe) null else messageInfo.chat_guid,
            is_from_me = isFromMe,
            is_mms = false,
            rowId = rowId,
            uri = uri,
            subId = it.getIntOrNull(SUBSCRIPTION_ID),
            creator = messageInfo.creator,
        )
    }

    companion object {
        private const val TAG = "SmsProvider"
        const val SMS_PREFIX = "sms_"
    }
}

