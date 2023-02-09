package com.beeper.sms.provider

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.DatabaseUtils
import android.database.sqlite.SqliteWrapper
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.MmsSms.PendingMessages
import com.android.mms.transaction.DownloadManager
import com.android.mms.transaction.PushReceiver
import com.beeper.sms.extensions.getInt
import com.beeper.sms.extensions.getLong
import com.google.android.mms.MmsException
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduPersister
import com.klinker.android.send_message.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.concurrent.thread

data class PendingMessage(
    val msgId: Int,
    val threadId: Long,
    val from: String,
    val originalTimestamp: Long,
    val expiryDate: Long?,
    val dueTime: Int,
    val errorCode: Int,
    val errorType: Int,
    val lastTry: Int,
    val msgType: Int,
    val protoType: Int,
    val retryIndex: Int,
    val subscriptionId: Int,
){
    fun canRetry(): Boolean {
        return (errorType < Telephony.MmsSms.ERR_TYPE_SMS_PROTO_PERMANENT
                && errorType > 0)
    }
}

class PendingMMSProvider internal constructor(
    val context: Context,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ContentObserver(getHandler()){
    private val coroutineScope = CoroutineScope(dispatcher)
    private val pendingMMSFlow = MutableStateFlow<List<PendingMessage>>(listOf())
    private val mmsProvider = MmsProvider(context)
    private val contactProvider = ContactProvider(context)
    private val chatThreadProvider = ChatThreadProvider(context)

    private val cr = context.contentResolver

    init{
        registerObserver()
        coroutineScope.launch(Dispatchers.IO) {
            loadAllPendingMMS()
        }
    }

    fun getPendingMMSFlow() = pendingMMSFlow.asStateFlow()

    fun registerObserver() {
        val pendingMessagesUri = PendingMessages.CONTENT_URI
        Timber.tag(TAG).d("Registering to observe PendingMessages")
        context.contentResolver.registerContentObserver(
            pendingMessagesUri,
            true,
            this
        )
    }

    fun unregisterObserver(){
        context.contentResolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        coroutineScope.launch(Dispatchers.IO) {
            Timber.tag(TAG).d( "PendingMessagesChanged")
            loadAllPendingMMS()
        }
    }

    fun retrySendingMMS(msgId: Int){
        try {
            val uri = ContentUris.withAppendedId(
                Mms.CONTENT_URI,
                msgId.toLong()
            )
            val subId = Settings.DEFAULT_SUBSCRIPTION_ID

            DownloadManager.getInstance().downloadMultimediaMessage(
                context,
                PushReceiver.getContentLocation(context, uri),
                uri,
                false,
                subId,
                msgId
            )
        } catch (e: MmsException) {
            e.printStackTrace()
        }
    }

    fun getPendingMessage(msgId: Int) : PendingMessage? {
        val uriBuilder = PendingMessages.CONTENT_URI.buildUpon()
        uriBuilder.appendQueryParameter("protocol", "mms")
        val uri = uriBuilder.build()
        val selection = "${PendingMessages.ERROR_TYPE} > 0 AND ${PendingMessages.MSG_ID} = $msgId"
        cr.query(
            uri,
            null,
            selection,
            null,
            PendingMessages.MSG_ID
        )?.use {
            if (it.count > 0) {
                if (it.moveToFirst()) {
                    val pduPersister = PduPersister.getPduPersister(context)
                        val msgId = it.getInt(PendingMessages.MSG_ID)
                        val dueTime = it.getInt(PendingMessages.DUE_TIME)
                        val errorCode = it.getInt(PendingMessages.ERROR_CODE)
                        val errorType = it.getInt(PendingMessages.ERROR_TYPE)
                        val lastTry = it.getInt(PendingMessages.LAST_TRY)
                        val msgType = it.getInt(PendingMessages.MSG_TYPE)
                        val protoType = it.getInt(PendingMessages.PROTO_TYPE)
                        val retryIndex = it.getInt(PendingMessages.RETRY_INDEX)
                        val subscriptionId = it.getInt(PendingMessages.SUBSCRIPTION_ID)

                        val genericPdu =
                            PduPersister.getPduPersister(context).load(
                                Uri.parse(
                                    "content://mms/inbox/$msgId"
                                )
                            )
                        val threadId = pduPersister.getThreadId(genericPdu)
                        val originalTimestamp = findOriginalTimestamp(context, msgId.toString())
                        val nInd = genericPdu as? NotificationInd
                        val from = genericPdu.from.string
                        Timber.tag(TAG).d(
                            "getPendingMessage() " +
                                    "MSG_ID: $msgId " +
                                    "From: $from " +
                                    "thread_id: $threadId " +
                                    "originalTimestamp: $originalTimestamp" +
                                    "expiryDate: ${nInd?.expiry}" +
                                    "dueTime: $dueTime " +
                                    "errorCode: $errorCode " +
                                    "errorType: $errorType " +
                                    "lastTry: $lastTry " +
                                    "msgType: $msgType " +
                                    "protoType: $protoType " +
                                    "retryIndex: $retryIndex " +
                                    "subscriptionId: $subscriptionId "
                        )
                        return PendingMessage(
                            msgId,
                            threadId,
                            from,
                            originalTimestamp,
                            nInd?.expiry,
                            dueTime,
                            errorCode,
                            errorType,
                            lastTry,
                            msgType,
                            protoType,
                            retryIndex,
                            subscriptionId,
                        )
                    }
                }
            }
            return null
    }

    suspend fun loadAllPendingMMS() : List<PendingMessage> {
        return withContext(Dispatchers.IO) {
            val pendingMessages = mutableListOf<PendingMessage>()
            val uriBuilder = PendingMessages.CONTENT_URI.buildUpon()
            uriBuilder.appendQueryParameter("protocol", "mms")
            val uri = uriBuilder.build()
            cr.query(
                uri, null, PendingMessages.ERROR_TYPE + ">" + 0,
                null,
                PendingMessages.MSG_ID
            )?.use {
                if (it.count > 0) {
                    if (it.moveToFirst()) {
                        val pduPersister = PduPersister.getPduPersister(context)
                        while (it.moveToNext()) {
                            val msgId = it.getInt(PendingMessages.MSG_ID)
                            val dueTime = it.getInt(PendingMessages.DUE_TIME)
                            val errorCode = it.getInt(PendingMessages.ERROR_CODE)
                            val errorType = it.getInt(PendingMessages.ERROR_TYPE)
                            val lastTry = it.getInt(PendingMessages.LAST_TRY)
                            val msgType = it.getInt(PendingMessages.MSG_TYPE)
                            val protoType = it.getInt(PendingMessages.PROTO_TYPE)
                            val retryIndex = it.getInt(PendingMessages.RETRY_INDEX)
                            val subscriptionId = it.getInt(PendingMessages.SUBSCRIPTION_ID)

                            val genericPdu =
                                PduPersister.getPduPersister(context).load(
                                    Uri.parse(
                                        "content://mms/inbox/$msgId"
                                    )
                                )
                            val threadId = pduPersister.getThreadId(genericPdu)
                            val originalTimestamp = findOriginalTimestamp(context, msgId.toString())
                            val nInd = genericPdu as? NotificationInd
                            val from = genericPdu.from.string
                            Timber.tag(TAG).d(
                                "pendingMessage" +
                                        "MSG_ID: $msgId " +
                                        "From: $from " +
                                        "thread_id: $threadId " +
                                        "originalTimestamp: $originalTimestamp" +
                                        "expiryDate: ${nInd?.expiry}" +
                                        "dueTime: $dueTime " +
                                        "errorCode: $errorCode " +
                                        "errorType: $errorType " +
                                        "lastTry: $lastTry " +
                                        "msgType: $msgType " +
                                        "protoType: $protoType " +
                                        "retryIndex: $retryIndex " +
                                        "subscriptionId: $subscriptionId "
                            )

                            pendingMessages.add(
                                PendingMessage(
                                    msgId,
                                    threadId,
                                    from,
                                    originalTimestamp,
                                    nInd?.expiry,
                                    dueTime,
                                    errorCode,
                                    errorType,
                                    lastTry,
                                    msgType,
                                    protoType,
                                    retryIndex,
                                    subscriptionId,
                                )
                            )
                        }
                        pendingMMSFlow.value = pendingMessages

                    }
                }
                Timber.tag(TAG).d("pendingMessage list size: ${pendingMessages.size}")
                it.close()
                return@withContext pendingMessages
            }
            Timber.tag(TAG).d(
                "pendingMessage list size: 0"
            )
            return@withContext listOf()
        }
    }

    private fun findOriginalTimestamp(context: Context, messageId: String): Long {
        val sb = StringBuilder('('.code)
        sb.append(Mms._ID)
        sb.append('=')
        sb.append(DatabaseUtils.sqlEscapeString(messageId))
        val cursor = SqliteWrapper.query(
            context, context.contentResolver,
            Mms.CONTENT_URI, arrayOf(Mms.DATE),
            sb.toString(), null, null
        )
        if (cursor != null) {
            try {
                if (cursor.count == 1 && cursor.moveToFirst()) {
                    val originalTimestamp = cursor.getLong(Mms.DATE)
                    Timber.tag("SMS-").d(
                        "findOriginalTimestamp messageId: " +
                                "$messageId originalTimestamp: $originalTimestamp"
                    )
                    cursor.close()
                    return originalTimestamp
                }
            } finally {
                cursor.close()
            }
        }
        return -1
    }
    companion object {
        private const val TAG = "SMS-PendingMsgProvider"
        fun getHandler() = HandlerThread("PendingMessagesProvider").let {
            it.start()
            Handler(it.looper)
        }
    }
}