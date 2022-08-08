package com.beeper.sms.receivers

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager.*
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.SyncWindowState
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.commands.internal.BridgeSendResponse
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageStatus
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.receivers.SmsDelivered.Companion.toError
import com.google.android.mms.util_alt.SqliteWrapper
import com.klinker.android.send_message.MmsSentReceiver
import com.klinker.android.send_message.Transaction

abstract class MmsSent : MmsSentReceiver() {
    abstract fun mapMessageToInboxPreviewCache(message: Message): InboxPreviewCache

    override fun onMessageStatusUpdated(context: Context, intent: Intent?, resultCode: Int) {
        Log.d(TAG, "result: $resultCode intent: ${intent.printExtras()}")

        val uri = intent?.getStringExtra(EXTRA_CONTENT_URI)?.toUri()

        if(resultCode != Activity.RESULT_OK) {
            Log.w(
                TAG, "Error sending MMS: " +
                    " uri:$uri error:${resultCode.toError(intent).message}")
        }

        val commandId : Int? =
            (intent?.getParcelableExtra(Transaction.SENT_MMS_BUNDLE) as? Bundle)?.getInt(Transaction.COMMAND_ID, -1).let {
                if(it != null && it < 0) {
                    null
                }else{
                    it
                }
            }
        val message = uri?.let { MmsProvider(context).getMessage(it) }

        if(uri!= null) {
            try {
                val values = ContentValues(1)
                val inboxPreviewProvider = InboxPreviewProviderLocator.getInstance(context)
                if (resultCode == Activity.RESULT_OK) {
                    values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                    if(message!= null) {
                        Log.d(TAG, "updating inbox preview cache: message sent")
                        val preview = mapMessageToInboxPreviewCache(message.copy(
                            messageStatus = MessageStatus.Sent
                        ))
                        inboxPreviewProvider.update(
                            preview
                        )
                    }else{
                        Log.e(TAG, "not updating inbox preview cache, failed to load message: $uri")
                    }
                } else {
                    values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_FAILED)
                    if(message!= null) {
                        Log.d(TAG, "updating inbox preview cache: message failed to send")
                        val preview = mapMessageToInboxPreviewCache(message.copy(
                            messageStatus = MessageStatus.Failed
                        ))
                        inboxPreviewProvider.update(
                            preview
                        )
                    }else{
                        Log.e(TAG, "not updating inbox preview cache, failed to load message: $uri")
                    }
                }
                SqliteWrapper.update(
                    context, context.contentResolver, uri, values,
                    null, null
                )
            }catch (e: Exception){
                Log.e(TAG, "Couldn't update MMS status MmsSent:onMessageStatusUpdated")
            }
        }else{
            Log.e(TAG, "Couldn't update MMS status, null URI")
        }

        // Notify content observers about MMS changes
        context.contentResolver.notifyChange(Telephony.Mms.CONTENT_URI, null)


        if(uri?.toString()?.startsWith("content://mms/outbox") == true){
            Log.d(TAG, "MMS is only on outbox, it wasn't delivered yet")
        }

        if(commandId == null && message == null){
            Log.e(TAG, "Error on SMS sent: Missing commandId and message uri")
            Log.e(TAG, "Missing message (uri=$uri) (commandId=$commandId)")
            return
        }

        if(commandId != null && resultCode != Activity.RESULT_OK){
            Log.e(TAG, "Bridging error response to MMS not delivered:" +
                    " ${errorToString(resultCode, intent)}")
            val error = Error("network_error", errorToString(resultCode, intent))
            // Bridge should be running, otherwise it will just fail
            StartStopBridge.INSTANCE.forwardSendErrorToBridge(
                commandId,
                error
            )
            return
        }

        val (guid, timestamp) = when {
            message != null -> Pair(message.guid, message.timestamp)
            else -> {
                Log.e(TAG, "Missing message (uri=$uri)")
                return
            }
        }


        val rowId = guid.removePrefix("mms_").toLong()
        val isMms = true
        //Doesn't have an ID, so doesn't need to be responded to
        val bridgedMessage = BridgedMessage(
            guid,
            rowId,
            isMms
        )

        val syncWindowState = StartStopBridge.INSTANCE.syncWindowState.value
        if(syncWindowState == SyncWindowState.Running){
            if(commandId != null) {
                // has a command id -> this message was delivered because mautrix asked to
                // we just need to answer it and save in db
                Log.d(
                    TAG, "confirmMessageDeliveryAndStoreMessage $commandId"
                )

                StartStopBridge.INSTANCE.forwardSendResponseToBridge(
                    BridgeSendResponse(
                        commandId,
                        bridgedMessage,
                        SendMessage.Response(guid, timestamp)
                    )
                )
            }else{
                Log.d(TAG, "Sent MMS is being forwarded to a running sync window")

                if(resultCode == Activity.RESULT_OK) {
                    // null command id -> a brand new message was locally delivered by the user ->
                    // as the bridge is running, we should bridge a message command
                    val newUri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, rowId)
                    val newMessage = MmsProvider(context).getMessage(newUri)
                    if (newMessage != null) {
                        Log.d(TAG, "MMS forwarded to running bridge ${newMessage.guid}")
                        StartStopBridge.INSTANCE.forwardMessageToBridge(
                            BridgeThisSmsOrMms(
                                newMessage
                            )
                        )
                    } else {
                        Log.e(TAG, "Couldn't load MMS message to bridge: $guid")
                    }
                }else{
                    Log.w(
                        TAG, "message failed -> we're not bridging user initiated failures $guid"
                    )
                    // TODO: we're not bridging failures right now
                }
            }
        }else{
            // just create a sync worker and it'll bridge the delivered message for us
            if(resultCode == Activity.RESULT_OK) {
                // simple approach to avoid a stopping bridge
                Log.d(TAG, "Starting a sync window to bridge a sent MMS message $guid")
                startSyncWindow()
            }else{
                Log.w(TAG, "Not starting a new sync window to bridge a user initiated" +
                        " failed MMS message $guid")
            }
        }

    }

    abstract fun startSyncWindow()


    companion object {
        private const val TAG = "MmsSent"
        private const val EXTRA_URI = "uri"

        private val Intent.httpError: Int?
            get() = if (hasExtra(EXTRA_MMS_HTTP_STATUS)) {
                getIntExtra(EXTRA_MMS_HTTP_STATUS, 0)
            } else {
                null
            }

        fun errorToString(rc: Int, intent: Intent?): String {
            return when (rc) {
                MMS_ERROR_UNSPECIFIED -> "MMS_ERROR_UNSPECIFIED"
                MMS_ERROR_INVALID_APN -> "MMS_ERROR_INVALID_APN"
                MMS_ERROR_UNABLE_CONNECT_MMS -> "MMS_ERROR_UNABLE_CONNECT_MMS"
                MMS_ERROR_HTTP_FAILURE -> "MMS_ERROR_HTTP_FAILURE (${intent?.httpError})"
                MMS_ERROR_IO_ERROR -> "MMS_ERROR_IO_ERROR"
                MMS_ERROR_RETRY -> "MMS_ERROR_RETRY"
                MMS_ERROR_CONFIGURATION_ERROR -> "MMS_ERROR_CONFIGURATION_ERROR"
                MMS_ERROR_NO_DATA_NETWORK -> "MMS_ERROR_NO_DATA_NETWORK"
                else -> "Unknown error ($rc)"
            }
        }
    }
}