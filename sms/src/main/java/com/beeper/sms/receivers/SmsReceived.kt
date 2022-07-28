package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.SmsProvider

abstract class SmsReceived : BroadcastReceiver() {
    abstract fun mapMessageToInboxPreviewCache(message: Message): InboxPreviewCache
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

        val loadedMessage = MessageProvider(context).getMessage(uri)
        if(loadedMessage!=null){
            com.beeper.sms.Log.d(TAG, "updating inbox preview cache")
            val preview = mapMessageToInboxPreviewCache(loadedMessage)
            val inboxPreviewProvider = InboxPreviewProviderLocator.getInstance(context)
            inboxPreviewProvider.update(
                preview
            )
        }

        if(StartStopBridge.INSTANCE.running){
            if(loadedMessage!=null) {
                com.beeper.sms.Log.d(TAG, "Asked to bridge this message " +
                        "in Android's database: ${loadedMessage.guid}" +
                        "chat_guid: ${loadedMessage.chat_guid}")
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
