package com.beeper.sms.provider

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony.Mms.*
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.TimeSeconds.Companion.toSeconds
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageErrorCode
import com.beeper.sms.commands.outgoing.MessageInfo
import com.beeper.sms.commands.outgoing.MessageStatus
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.extensions.*
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid
import com.google.android.mms.pdu_alt.PduHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class MmsProvider constructor(
    context: Context,
    private val partProvider: PartProvider = PartProvider(context),
    private val guidProvider: GuidProvider = GuidProvider(context),
) {
    val mmsFailureErrorCodeDao = BridgeDatabase.getInstance(context).mmsFailureErrorCodeDao()
    private val packageName = context.applicationInfo.packageName
    private val cr = context.contentResolver

    fun getActiveChats(timestamp: TimeSeconds): List<MessageInfo> =
        getMms("$DATE > ${timestamp.toLong()} AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            this::messageInfoMapper)

    fun getMessageInfo(uri: Uri): MessageInfo? =
        getMms(uri, mapper = this::messageInfoMapper).firstOrNull()

    fun getLatest(thread: Long, limit: Int) =
        getMms(where = "$THREAD_ID = $thread AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            limit = limit, mapper = this::messageMapper)

    fun getAll(thread: Long, limit: Int) =
        getMms(where = "$THREAD_ID = $thread",
            limit = limit, mapper = this::messageMapper)

    fun getMessagesAfter(thread: Long, timestamp: TimeSeconds) =
        getMms(
            "$THREAD_ID = $thread AND $DATE > ${timestamp.toLong()} " +
                    "AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            this::messageMapper
        )

    fun getMessagesAfterWithLimitIncludingTimestamp(thread: Long, timestamp: TimeSeconds, limit: Int = 30) =
        getMms(where = "$THREAD_ID = $thread AND $DATE >= ${timestamp.toLong()} ",
            limit = limit, mapper = this::messageMapper)

    fun getMessagesBeforeWithLimitIncludingTimestamp(thread: Long, timestamp: TimeSeconds, limit: Int = 30) =
        getMms(where = "$THREAD_ID = $thread AND $DATE <= ${timestamp.toLong()} ",
            limit = limit, mapper = this::messageMapper)

    fun getMessage(uri: Uri) = getMms(uri, mapper = this::messageMapper).firstOrNull()


    private fun <T> getMms(where: String? = "$MESSAGE_BOX <= $MESSAGE_BOX_SENT",
                           mapper: (Cursor, Long, Uri) -> T?): List<T> =
        listOf(CONTENT_URI).flatMap { uri ->
            getMms(uri = uri, where = where, mapper = mapper)
        }


    private fun getMmsMetadata(
        uri: Uri = CONTENT_URI,
        where: String? = null,
        limit: Int? = null,
        order: String? =  if (limit != null && limit > 0) "$DATE DESC LIMIT $limit" else null,
    ): List<Pair<Long,Long>> =
        cr.map(
            uri = uri,
            where = where,
            projection = listOf(
                _ID,
                THREAD_ID,
            ).toTypedArray(),
            order = order,
            limit = if (limit != null && limit > 0) limit else null
        ) {
            val rowId = it.getLong(_ID)
            val threadId = it.getLong(THREAD_ID)
            Pair(rowId, threadId)
        }

    private fun <T> getMms(
        uri: Uri = CONTENT_URI,
        where: String? = null,
        limit: Int? = null,
        order: String? =  if (limit != null && limit > 0) "$DATE DESC LIMIT $limit" else null,
        mapper: (Cursor, Long, Uri) -> T?,
    ): List<T> =
        cr.map(
            uri = uri,
            where = where,
            projection = listOf(
                _ID,
                THREAD_ID,
                DATE,
                MESSAGE_BOX,
                READ,
                SUBJECT,
                RESPONSE_STATUS,
                SUBSCRIPTION_ID
            ).toTypedArray(),
            order = order,
            limit = if (limit != null && limit > 0) limit else null
        ) {
            val rowId = it.getLong(_ID)
            mapper(
                it,
                rowId,
                if (where == null) uri else ContentUris.withAppendedId(uri, rowId)
            )
        }

    private fun messageInfoMapper(it: Cursor, rowId: Long, uri: Uri): MessageInfo? {
        val thread = it.getLong(THREAD_ID)
        val isRead = it.getInt(READ) == 1
        val date = it.getLong(DATE)
        val timestamp = date.toSeconds()

        val chatGuid = guidProvider.getChatGuid(thread)
        if (chatGuid.isNullOrBlank()) {
            Log.e(TAG, "Error generating guid for $thread, rowId: $rowId")
            return null
        }

        Timber.d("messageInfoMapper thread_id: $thread timestamp: $timestamp")
        val guid = "$MMS_PREFIX$rowId"

        return MessageInfo(
            guid,
            timestamp,
            chatGuid,
            uri,
            isRead,
            thread.toString(),
        )
    }

    private fun messageMapper(it: Cursor, rowId: Long, uri: Uri): Message? {
        val messageInfo = messageInfoMapper(it, rowId, uri) ?: return null
        val attachments = partProvider.getAttachment(rowId)
        val isFromMe = when (it.getInt(MESSAGE_BOX)) {
            MESSAGE_BOX_INBOX -> false
            else -> true
        }
        val messageStatus : MessageStatus = when(it.getInt(MESSAGE_BOX)){
            MESSAGE_BOX_FAILED -> {
                val errorCode = mmsFailureErrorCodeDao.getByMessageId(messageInfo.guid)?.error_code
                MessageStatus.Failed(MessageErrorCode.fromMmsResult(errorCode))
            }
            MESSAGE_BOX_OUTBOX -> MessageStatus.Waiting
            else -> MessageStatus.Sent
        }

        val sender = when {
            isFromMe -> null
            else -> getSender(rowId)?.chatGuid ?: messageInfo.chat_guid.takeIf { cg -> cg.isDm }
        }
        return Message(
            guid = messageInfo.guid,
            timestamp = messageInfo.timestamp,
            subject = it.getString(SUBJECT)?.takeUnless { sub -> sub == "NoSubject" } ?: "",
            text = attachments.mapNotNull { a -> a.text }.joinToString(""),
            chat_guid = messageInfo.chat_guid,
            thread_id = it.getLong(THREAD_ID).toString(),
            sender_guid = sender,
            is_from_me = isFromMe,
            attachments = attachments.mapNotNull { a -> a.attachment },
            is_mms = true,
            resp_st = it.getIntOrNull(RESPONSE_STATUS),
            rowId = rowId,
            uri = uri,
            subId = it.getIntOrNull(SUBSCRIPTION_ID),
            messageStatus = messageStatus,
            is_read = messageInfo.is_read
        )
    }


    fun getLastMessage(threadId: Long) : Message? {
        return getDistinctMms(
            where = "$THREAD_ID = $threadId",
            mapper = this::messageMapper,
            order = "$DATE DESC",
            limit = 1,
        ).firstOrNull()
    }

    fun getLastReadMessage(threadId: Long) : Message? {
        return getDistinctMms(
            where = "$THREAD_ID = $threadId " +
                    "AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT AND $READ = 1",
            mapper = this::messageMapper,
            order = "$DATE DESC",
        ).firstOrNull()
    }

    fun getLastReadMessageMetadata(threadId: Long) : MessageProvider.ReadReceiptInfo? {
        val query = cr.query(
            CONTENT_URI,
            listOf(
                _ID,
                THREAD_ID,
                DATE,
            ).toTypedArray(),
            "$THREAD_ID = $threadId " +
                    "AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT AND $READ = 1",
            null,
            "$DATE DESC"
        )
        query?.use{
            while(it.moveToNext()){
                val rowId =  it.getLong(_ID)
                val mmsId = "$MMS_PREFIX$rowId"
                val timestamp = it.getLong(DATE).toSeconds()
                return MessageProvider.ReadReceiptInfo(
                    mmsId,
                    timestamp
                )
            }
        }
        return null
    }

    /* SyncWindow */

    suspend fun getLastMmsIdFromThread(threadId : Long) : Long?{
        return withContext(Dispatchers.IO) {
            val query = cr.query(
                CONTENT_URI,
                listOf(
                    _ID,
                    THREAD_ID,
                ).toTypedArray(),
                "$THREAD_ID = $threadId ",
                null,
                "$_ID DESC"
            )
            query?.use{
                while(it.moveToNext()){
                    return@withContext it.getLong(_ID)
                }
            }
            return@withContext null
        }
    }

    fun getNewMmsMessages(initialId: Long) =
        getDistinctMms(
            "$_ID >= $initialId " +
                    //FILTER ONLY FOR ALREADY RECEIVED OR DELIVERED MMS
                    "AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            this::messageMapper
        )

    fun getNewMmsMessagesMetadata(initialId: Long) =
        getMmsMetadata(
            where = "$_ID >= $initialId ",
        )


    fun countValidMMSMessagesOnThread(threadId: Long): Int? {
        val query = cr.query(
            CONTENT_URI,
            listOf(
                _ID,
            ).toTypedArray(),
            "$THREAD_ID = $threadId " +
                    //FILTER ONLY FOR ALREADY RECEIVED OR DELIVERED MMS
                    "AND $MESSAGE_BOX <= $MESSAGE_BOX_SENT ",
            null,
            null
        )
        query?.use{
            return it.count
        }
        return null
    }

    private fun <T> getDistinctMms(
        where: String,
        mapper: (Cursor, Long, Uri) -> T?,
        order : String = "$_ID ASC",
        limit: Int? = null
    ): List<T> =
        listOf(CONTENT_URI).flatMap { uri ->
            getMms(uri = uri, where = where, order = order, mapper = mapper, limit = limit)
        }


    private fun getSender(message: Long): String? =
        cr.firstOrNull(
            uri = "$CONTENT_URI/$message/addr".toUri(),
            where = "${Addr.TYPE} = ${PduHeaders.FROM}"
        ) {
            it.getString(Addr.ADDRESS)
        }

    companion object {
        private const val TAG = "MmsProvider"
        const val MMS_PREFIX = "mms_"

        private val String.isDm: Boolean
            get() = startsWith("SMS;-;")
    }
}