package com.beeper.sms.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager.*
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.MmsProvider
import com.klinker.android.send_message.MmsSentReceiver.EXTRA_CONTENT_URI
import com.klinker.android.send_message.Transaction

abstract class MmsSent : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "result: $resultCode intent: ${intent.printExtras()}")

        val uri = intent?.getStringExtra(EXTRA_CONTENT_URI)?.toUri()
        val commandId =
            (intent?.getParcelableExtra(Transaction.SENT_MMS_BUNDLE) as? Bundle)?.getInt(Transaction.COMMAND_ID)
        val message = uri?.let { MmsProvider(context).getMessageInfo(it) }

        if(commandId == null && message == null){
            Log.e(TAG, "Error on SMS sent: Missing commandId and message uri")
            Log.e(TAG, "Missing message (uri=$uri) (commandId=$commandId)")
            return
        }

        if(commandId != null && resultCode != Activity.RESULT_OK){
            StartStopBridge.INSTANCE.send(
                commandId,
                Error("network_error", errorToString(resultCode, intent))
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

        if(StartStopBridge.INSTANCE.running){
            if(commandId != null) {
                // has a command id -> this message was delivered because mautrix asked to
                // we just need to answer it and save in db
                Log.d(
                    TAG, "confirmMessageDeliveryAndStoreMessage"
                )
                StartStopBridge.INSTANCE.confirmMessageDeliveryAndStoreMessage(
                    Command("response", SendMessage.Response(guid, timestamp), commandId),
                    bridgedMessage
                )
            }else{
                // null command id -> a brand new message was locally delivered by the user ->
                // as the bridge is running, we should bridge a message command
                val newMessage = MessageProvider(context).getMessage(uri)
                if(newMessage!=null) {
                    StartStopBridge.INSTANCE.forwardMessageToBridge(
                        BridgeThisSmsOrMms(
                            newMessage
                        )
                    )
                }
            }
        }else{
            // just create a sync worker and it'll bridge the delivered message for us
            startSyncWindow()
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

        private fun errorToString(rc: Int, intent: Intent?): String {
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