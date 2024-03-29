package com.beeper.sms.receivers

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.provider.Telephony.Sms
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.SyncWindowState
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.commands.internal.BridgeSendError
import com.beeper.sms.commands.internal.BridgeSendResponse
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageErrorCode
import com.beeper.sms.commands.outgoing.MessageStatus
import com.beeper.sms.commands.outgoing.SendMessageStatus
import com.beeper.sms.commands.outgoing.SendMessageStatusResult
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.database.models.PendingSendResponse
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.receivers.SmsDelivered.Companion.toError
import com.google.android.mms.util_alt.SqliteWrapper
import com.klinker.android.send_message.SentReceiver
import com.klinker.android.send_message.Transaction
import mapSMSErrorToHumanReadableMessage

abstract class SmsSent : SentReceiver() {

    abstract fun mapMessageToInboxPreviewCache(message: Message) : InboxPreviewCache

    override fun onMessageStatusUpdated(context: Context, intent: Intent?, resultCode: Int) {
        Log.d(TAG, "result: $resultCode extras: ${intent.printExtras()}")



        val uri = intent?.getStringExtra("uri")?.toUri() ?: intent?.getStringExtra("message_uri")?.toUri()

        if(resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "Error sending SMS: " +
                    " uri:$uri error:${resultCode.toError(intent).message}")
        }

        val commandId =
            (intent?.getParcelableExtra(Transaction.SENT_SMS_BUNDLE)
                    as? Bundle)?.getInt(Transaction.COMMAND_ID, -1).let {
                if(it != null && it < 0) {
                    null
                }else{
                    it
                }
            }

        val message = uri?.let { SmsProvider(context).getMessage(it) }

        if(commandId == null && message == null){
            Log.e(TAG, "Error on SMS sent: Missing commandId and message uri")
            Log.e(TAG, "Missing message (uri=$uri) (commandId=$commandId)")
            return
        }

        if(message != null){
            val inboxPreviewProvider = InboxPreviewProviderLocator.getInstance(context)
            Log.d(TAG, "updating inbox preview cache")
            val messageStatus = if(resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "SMS message was successfully received -> updating inbox preview")
                MessageStatus.Sent
            }else{
                Log.d(TAG, "SMS message failed to send -> updating inbox preview")
                MessageStatus.Failed(MessageErrorCode.fromSmsResult(resultCode))
            }
            inboxPreviewProvider.update(
                mapMessageToInboxPreviewCache(message.copy(
                    messageStatus = messageStatus
                ))
            )
        }else{
            Log.e(TAG, "not updating inbox preview cache, failed to load message: $uri")
        }

        val syncWindowState = StartStopBridge.INSTANCE.syncWindowState.value


        if(commandId != null && message != null && resultCode != Activity.RESULT_OK){
            Log.e(TAG, "Bridging error response to SMS not delivered:" +
                    " uri:$uri error:${resultCode.toError(intent)}")

            val errorMessage = mapSMSErrorToHumanReadableMessage(resultCode)

            val errorSendMessageStatus = SendMessageStatus(
                message.guid,
                message.chat_guid,
                SendMessageStatusResult.Failed.status,
                errorMessage
            )

            if(syncWindowState == SyncWindowState.Running) {
                // Bridge is running, we're just sending the failed ack to it
                StartStopBridge.INSTANCE.forwardSendErrorToBridge(
                    commandId,
                    BridgeSendError(
                        commandId,
                        errorSendMessageStatus
                    )
                )
            }else{
                val sendResponseDao = BridgeDatabase.getInstance(context).pendingSendResponseDao()
                //Store pending ack
                sendResponseDao.insert(
                    PendingSendResponse(
                        message.guid,
                        message.chat_guid,
                        SendMessageStatusResult.Failed.status,
                        errorMessage
                    )
                )
                // Start a sync window to bridge the ack to mautrix-imessage
                startSyncWindow()
            }
            return
        }

        val (guid, timestamp) = when {
            message != null -> Pair(message.guid, message.timestamp)
            else -> {
                Log.e(TAG, "Missing message (uri=$uri)")
                return
            }
        }

        val (rowId,isMms) = if(guid.startsWith("sms_")){
            val rowId = guid.removePrefix("sms_").toLong()
            val isMms = false
            Pair(rowId,isMms)
        }else{
            if(guid.startsWith("mms_")) {
                val rowId = guid.removePrefix("mms_").toLong()
                val isMms = true
                Pair(rowId, isMms)
            }else{
                Log.e(TAG,
                    "Error onMessageStatusUpdated -> " +
                            "delivered message has an invalid prefix (not sms_/mms_)")
                return
            }
        }


        if(syncWindowState == SyncWindowState.Running){
            if(commandId != null) {
                // Has a command id -> this message was delivered because mautrix asked to.
                // We just need to answer it and save in db
                Log.d(
                    TAG, "confirmMessageDeliveryAndStoreMessage"
                )
                val bridgedMessage = BridgedMessage(
                    guid,
                    rowId,
                    isMms,
                null
                )

                StartStopBridge.INSTANCE.forwardSendResponseToBridge(
                    BridgeSendResponse(
                        commandId,
                        bridgedMessage,
                        SendMessageStatus(
                            guid,
                            message.chat_guid,
                            SendMessageStatusResult.Sent.status,
                            null
                        )
                    )
                )
            }else{
                // Null command id -> a brand new message was locally delivered by the user ->
                // As the bridge is running, we should ask it to bridge the message
                val loadedMessage = MessageProvider(context).getMessage(uri)
                if(resultCode == Activity.RESULT_OK){
                    if (loadedMessage != null) {
                        Log.d(TAG, "SMS forwarded to running bridge: ${loadedMessage.guid}")

                        StartStopBridge.INSTANCE.forwardMessageToBridge(
                            BridgeThisSmsOrMms(
                                loadedMessage
                            )
                        )
                    }else{
                        Log.e(TAG, "Couldn't load SMS message to bridge: $guid")
                    }
                }else{
                    Log.w(
                        TAG, "message failed -> we're not bridging failures yet $guid"
                    )
                    // TODO: we're not bridging failures right now
                }
            }
        }else{
            if(commandId != null) {
                val sendResponseDao = BridgeDatabase.getInstance(context).pendingSendResponseDao()
                sendResponseDao.insert(
                    PendingSendResponse(
                        guid,
                        message.chat_guid,
                        SendMessageStatusResult.Sent.status,
                        null
                    )
                )
                // Start a sync window to bridge the ack to mautrix-imessage
                startSyncWindow()
            }else {
                // just create a sync worker and it'll bridge the delivered message for us
                // message sent by the user, no ack needed for mautrix-imessage
                if (resultCode == Activity.RESULT_OK) {
                    // simple approach to avoid a stopping bridge
                    Log.d(TAG, "Starting a sync window to bridge a sent SMS message $guid")
                    startSyncWindow()
                } else {
                    Log.w(
                        TAG, "Not starting a new sync window to bridge a user initiated" +
                                " failed SMS message"
                    )
                }
            }
        }
    }

    abstract fun startSyncWindow()


    companion object {
        private const val TAG = "SmsSent"
    }
}
