package com.beeper.sms.provider

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.provider.Telephony.Sms.*
import android.telephony.SmsMessage
import android.text.TextUtils
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
            "$THREAD_ID = $thread AND $DATE > ${timestamp.toLong()}",
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
        order: String? = if (limit > 0) "$DATE DESC LIMIT $limit" else null,
        mapper: (Cursor, Long, Uri) -> T?
    ): List<T> =
        cr.map(
            uri = uri,
            where = where,
            projection = listOf(
                _ID,
                THREAD_ID,
                DATE,
                ADDRESS,
                CREATOR,
                TYPE,
                SUBJECT,
                BODY,
                SUBSCRIPTION_ID
            ).toTypedArray(),
            order = order
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

    /* SyncWindow */
    fun getNewSmsMessages(initialId: Long) =
        getDistinctSms(
            "$_ID >= $initialId " +
                    //FILTER ONLY FOR ALREADY RECEIVED OR DELIVERED SMS
                    "AND $TYPE <= $MESSAGE_TYPE_SENT ",
            order = "$_ID ASC",
            this::messageMapper
        )
    private fun <T> getDistinctSms(where: String, order: String, mapper: (Cursor, Long, Uri) -> T?): List<T> =
        listOf(CONTENT_URI).flatMap { uri ->
            getSms(uri = uri, where = where, order = order, mapper = mapper)
        }

    /**
     * Store a received SMS into Telephony provider
     *
     * @param intent The intent containing the received SMS
     * @return The URI of written message
     */
    fun writeInboxMessage(intent: Intent): Uri? {
        val messages = Intents.getMessagesFromIntent(intent)
        if (messages == null || messages.isEmpty()) {
            Log.e(TAG, "Failed to parse SMS pdu")
            return null
        }
        // Sometimes, SmsMessage is null if it can’t be parsed correctly.
        for (sms in messages) {
            if (sms == null) {
                Log.e(TAG, "Can’t write null SmsMessage")
                return null
            }
        }
        val values = parseSmsMessage(messages)
        val identity = Binder.clearCallingIdentity()
        try {
            return cr.insert(Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist inbox message ${e.message}")
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
        return null
    }

    /**
     * Convert SmsMessage[] into SMS database schema columns
     *
     * @param msgs The SmsMessage array of the received SMS
     * @return ContentValues representing the columns of parsed SMS
     */
    private fun parseSmsMessage(msgs: Array<SmsMessage>): ContentValues {
        val sms = msgs[0]
        val values = ContentValues()
        values.put(Inbox.ADDRESS, sms.displayOriginatingAddress)
        values.put(Inbox.BODY, buildMessageBodyFromPdus(msgs))
        values.put(Inbox.DATE_SENT, sms.timestampMillis)
        values.put(Inbox.DATE, System.currentTimeMillis())
        values.put(Inbox.PROTOCOL, sms.protocolIdentifier)
        values.put(Inbox.SEEN, 0)
        values.put(Inbox.READ, 0)
        val subject = sms.pseudoSubject
        if (!TextUtils.isEmpty(subject)) {
            values.put(Inbox.SUBJECT, subject)
        }
        values.put(Inbox.REPLY_PATH_PRESENT, if (sms.isReplyPathPresent) 1 else 0)
        values.put(Inbox.SERVICE_CENTER, sms.serviceCenterAddress)
        return values
    }

    /**
     * Build up the SMS message body from the SmsMessage array of received SMS
     *
     * @param msgs The SmsMessage array of the received SMS
     * @return The text message body
     */
    private fun buildMessageBodyFromPdus(msgs: Array<SmsMessage>): String {
        return if (msgs.size == 1) {
            // There is only one part, so grab the body directly.
            replaceFormFeeds(msgs[0].displayMessageBody)
        } else {
            // Build up the body from the parts.
            val body = StringBuilder()
            for (msg in msgs) {
                // getDisplayMessageBody() can NPE if mWrappedMessage inside is null.
                body.append(msg.displayMessageBody)
            }
            replaceFormFeeds(body.toString())
        }
    }

    // Some providers send formfeeds in their messages. Convert those formfeeds to newlines.
    private fun replaceFormFeeds(s: String?) = s?.replace(FORM_FEED, '\n') ?: ""

    companion object {
        private const val TAG = "SmsProvider"
        private const val FORM_FEED = '\u000C' // '\f'
        const val SMS_PREFIX = "sms_"
    }
}

