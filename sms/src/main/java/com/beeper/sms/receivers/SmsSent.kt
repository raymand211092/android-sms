package com.beeper.sms.receivers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager.*
import androidx.core.net.toUri
import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.commands.internal.BridgeSendResponse
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.commands.outgoing.MessageStatus
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.ChatThreadProvider
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.SmsProvider
import com.klinker.android.send_message.SentReceiver
import com.klinker.android.send_message.Transaction

abstract class SmsSent : SentReceiver() {

    abstract fun mapMessageToInboxPreviewCache(message: Message) : InboxPreviewCache

    override fun onMessageStatusUpdated(context: Context, intent: Intent?, resultCode: Int) {
        Log.d(TAG, "result: $resultCode extras: ${intent.printExtras()}")
        val uri = intent?.getStringExtra("uri")?.toUri() ?: intent?.getStringExtra("message_uri")?.toUri()
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
                MessageStatus.Sent
            }else{
                MessageStatus.Failed
            }
            inboxPreviewProvider.update(
                mapMessageToInboxPreviewCache(message.copy(
                    messageStatus = messageStatus
                ))
            )
        }else{
            Log.e(TAG, "not updating inbox preview cache, failed to load message: $uri")
        }

        if(commandId != null && resultCode != Activity.RESULT_OK){
            Log.e(TAG, "Bridging error response to SMS not delivered:" +
                    " uri:$uri error:${resultCode.toError(intent)}")
            StartStopBridge.INSTANCE.forwardSendErrorToBridge(
                    commandId,
                    resultCode.toError(intent)
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



        if(StartStopBridge.INSTANCE.running){
            if(commandId != null) {
                // Has a command id -> this message was delivered because mautrix asked to.
                // We just need to answer it and save in db
                Log.d(
                    TAG, "confirmMessageDeliveryAndStoreMessage"
                )
                val bridgedMessage = BridgedMessage(
                    guid,
                    rowId,
                    isMms
                )

                StartStopBridge.INSTANCE.forwardSendResponseToBridge(
                    BridgeSendResponse(
                        commandId,
                        bridgedMessage,
                        SendMessage.Response(guid, timestamp)
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
            // just create a sync worker and it'll bridge the delivered message for us
            if(resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Starting a sync window to bridge a SMS message")
                startSyncWindow()
            }else{
                Log.w(
                    TAG, "Not starting a new sync window to bridge a user initiated" +
                        " failed SMS message")
            }
        }
    }

    abstract fun startSyncWindow()


    companion object {
        private const val TAG = "SmsSent"
        private const val ERR_NETWORK_ERROR = "network_error"
        private const val ERR_TIMEOUT = "timeout"
        private const val ERR_UNSUPPORTED = "unsupported"

        private fun Int.toError(intent: Intent?): Error {
            val message = errorToString(this, intent)
            return when (this) {
                RESULT_ERROR_NO_SERVICE,
                RESULT_ERROR_RADIO_OFF,
                RESULT_RIL_NETWORK_NOT_READY,
                RESULT_RADIO_NOT_AVAILABLE ->
                    Error(ERR_TIMEOUT, message)
                RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
                RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED ->
                    Error(ERR_UNSUPPORTED, message)
                else ->
                    Error(ERR_NETWORK_ERROR, message)
            }
        }

        private fun errorToString(rc: Int, intent: Intent?): String {
            val errorCode = intent?.getStringExtra("errorCode")
            return when (rc) {
                RESULT_ERROR_GENERIC_FAILURE -> "ERROR_GENERIC_FAILURE($errorCode)"
                RESULT_ERROR_RADIO_OFF -> "ERROR_RADIO_OFF"
                RESULT_ERROR_NULL_PDU -> "ERROR_NULL_PDU"
                RESULT_ERROR_NO_SERVICE -> "ERROR_NO_SERVICE"
                RESULT_ERROR_LIMIT_EXCEEDED -> "ERROR_LIMIT_EXCEEDED"
                RESULT_ERROR_FDN_CHECK_FAILURE -> "ERROR_FDN_CHECK_FAILURE"
                RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "ERROR_SHORT_CODE_NOT_ALLOWED"
                RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "ERROR_SHORT_CODE_NEVER_ALLOWED"
                RESULT_RADIO_NOT_AVAILABLE -> "RADIO_NOT_AVAILABLE"
                RESULT_NETWORK_REJECT -> "NETWORK_REJECT"
                RESULT_INVALID_ARGUMENTS -> "INVALID_ARGUMENTS"
                RESULT_INVALID_STATE -> "INVALID_STATE"
                RESULT_NO_MEMORY -> "NO_MEMORY"
                RESULT_INVALID_SMS_FORMAT -> "INVALID_SMS_FORMAT"
                RESULT_SYSTEM_ERROR -> "SYSTEM_ERROR"
                RESULT_MODEM_ERROR -> "MODEM_ERROR"
                RESULT_NETWORK_ERROR -> "NETWORK_ERROR"
                RESULT_ENCODING_ERROR -> "ENCODING_ERROR"
                RESULT_INVALID_SMSC_ADDRESS -> "INVALID_SMSC_ADDRESS"
                RESULT_OPERATION_NOT_ALLOWED -> "OPERATION_NOT_ALLOWED"
                RESULT_INTERNAL_ERROR -> "INTERNAL_ERROR"
                RESULT_NO_RESOURCES -> "NO_RESOURCES"
                RESULT_CANCELLED -> "CANCELLED"
                RESULT_REQUEST_NOT_SUPPORTED -> "REQUEST_NOT_SUPPORTED"
                RESULT_NO_BLUETOOTH_SERVICE -> "NO_BLUETOOTH_SERVICE"
                RESULT_INVALID_BLUETOOTH_ADDRESS -> "INVALID_BLUETOOTH_ADDRESS"
                RESULT_BLUETOOTH_DISCONNECTED -> "BLUETOOTH_DISCONNECTED"
                RESULT_UNEXPECTED_EVENT_STOP_SENDING -> "UNEXPECTED_EVENT_STOP_SENDING"
                RESULT_SMS_BLOCKED_DURING_EMERGENCY -> "SMS_BLOCKED_DURING_EMERGENCY"
                RESULT_SMS_SEND_RETRY_FAILED -> "SMS_SEND_RETRY_FAILED"
                RESULT_REMOTE_EXCEPTION -> "REMOTE_EXCEPTION"
                RESULT_NO_DEFAULT_SMS_APP -> "NO_DEFAULT_SMS_APP"
                RESULT_RIL_RADIO_NOT_AVAILABLE -> "RIL_RADIO_NOT_AVAILABLE"
                RESULT_RIL_SMS_SEND_FAIL_RETRY -> "RIL_SMS_SEND_FAIL_RETRY"
                RESULT_RIL_NETWORK_REJECT -> "RIL_NETWORK_REJECT"
                RESULT_RIL_INVALID_STATE -> "RIL_INVALID_STATE"
                RESULT_RIL_INVALID_ARGUMENTS -> "RIL_INVALID_ARGUMENTS"
                RESULT_RIL_NO_MEMORY -> "RIL_NO_MEMORY"
                RESULT_RIL_REQUEST_RATE_LIMITED -> "RIL_REQUEST_RATE_LIMITED"
                RESULT_RIL_INVALID_SMS_FORMAT -> "RIL_INVALID_SMS_FORMAT"
                RESULT_RIL_SYSTEM_ERR -> "RIL_SYSTEM_ERR"
                RESULT_RIL_ENCODING_ERR -> "RIL_ENCODING_ERR"
                RESULT_RIL_INVALID_SMSC_ADDRESS -> "RIL_INVALID_SMSC_ADDRESS"
                RESULT_RIL_MODEM_ERR -> "RIL_MODEM_ERR"
                RESULT_RIL_NETWORK_ERR -> "RIL_NETWORK_ERR"
                RESULT_RIL_INTERNAL_ERR -> "RIL_INTERNAL_ERR"
                RESULT_RIL_REQUEST_NOT_SUPPORTED -> "RIL_REQUEST_NOT_SUPPORTED"
                RESULT_RIL_INVALID_MODEM_STATE -> "RIL_INVALID_MODEM_STATE"
                RESULT_RIL_NETWORK_NOT_READY -> "RIL_NETWORK_NOT_READY"
                RESULT_RIL_OPERATION_NOT_ALLOWED -> "RIL_OPERATION_NOT_ALLOWED"
                RESULT_RIL_NO_RESOURCES -> "RIL_NO_RESOURCES"
                RESULT_RIL_CANCELLED -> "RIL_CANCELLED"
                RESULT_RIL_SIM_ABSENT -> "RIL_SIM_ABSENT"
                121 -> "RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED"
                122 -> "RIL_ACCESS_BARRED"
                123 -> "RIL_BLOCKED_DUE_TO_CALL"
                else -> "Unknown error ($rc, $errorCode)"
            }
        }
    }
}
