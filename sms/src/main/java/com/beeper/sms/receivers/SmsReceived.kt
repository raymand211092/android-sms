package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.SmsProvider

abstract class SmsReceived : BroadcastReceiver() {
    abstract fun startSyncWindow()
    override fun onReceive(context: Context, intent: Intent) {
        com.beeper.sms.Log.d(TAG, "a new SMS message was received")
        val uri = SmsProvider(context).writeInboxMessage(intent)
        if (uri == null) {
            com.beeper.sms.Log.e(TAG, "Critical issue: ->Failed to write SMS message")
            return
        }
        com.beeper.sms.Log.d(TAG, "Message was successfully stored " +
                    "in Android's database")
        if(StartStopBridge.INSTANCE.running){
            val loadedMessage = MessageProvider(context).getMessage(uri)
            if(loadedMessage!=null) {
                com.beeper.sms.Log.d(TAG, "Asked to bridge this message " +
                        "in Android's database: ${loadedMessage.chat_guid}")
                StartStopBridge.INSTANCE.forwardMessageToBridge(
                    BridgeThisSmsOrMms(
                        loadedMessage
                    )
                )
            }else{
                com.beeper.sms.Log.e(TAG, "Couldn't load the received message" +
                        " to be bridged -> Message will be bridged on next sync window")
            }
        }
        else{
            com.beeper.sms.Log.d(TAG, "SyncWindow is stopped ->" +
                    " starting sms bridge sync window")
            startSyncWindow()
        }
    }
    companion object {
        private const val TAG = "SmsReceived"
    }
}
