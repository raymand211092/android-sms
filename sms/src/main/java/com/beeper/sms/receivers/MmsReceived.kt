package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.SmsProvider
import com.klinker.android.send_message.MmsReceivedReceiver
import timber.log.Timber

abstract class MmsReceived : MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context?, uri: Uri?) {
        if(context != null && uri != null) {
            com.beeper.sms.Log.d(TAG, "a new MMS message was received")
            if (StartStopBridge.INSTANCE.running) {
                val loadedMessage = MessageProvider(context).getMessage(uri)
                if (loadedMessage != null) {
                    com.beeper.sms.Log.d(
                        TAG, "Asked to bridge this message " +
                                "in Android's database: ${loadedMessage.guid}" +
                                "chat_guio: ${loadedMessage.chat_guid}"
                    )
                    StartStopBridge.INSTANCE.forwardMessageToBridge(
                        BridgeThisSmsOrMms(
                            loadedMessage
                        )
                    )
                } else {
                    com.beeper.sms.Log.e(
                        TAG, "Couldn't load the received MMS message" +
                                " to be bridged -> Message will be bridged on next sync window"
                    )
                }
            } else {
                com.beeper.sms.Log.d(
                    TAG, "SyncWindow is stopped ->" +
                            " starting SMS bridge's sync window"
                )
                startSyncWindow()
            }
        }else{
            com.beeper.sms.Log.e(
                TAG, "a new MMS message was received, but context or URI was null"
            )
        }
    }

    override fun onError(context: Context?, error: String?) {
        Timber.tag(TAG).e( "Critical issue: -> error receiving MMS Message -> $error")
    }

    abstract fun startSyncWindow()
    companion object {
        private const val TAG = "MmsReceived"
    }
}
