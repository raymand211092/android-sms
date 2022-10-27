package com.beeper.sms.receivers

import android.content.Context
import android.net.Uri
import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.SyncWindowState
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.provider.MessageProvider
import com.klinker.android.send_message.MmsReceivedReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import timber.log.Timber

abstract class MmsReceived : MmsReceivedReceiver() {
    abstract fun mapMessageToInboxPreviewCache(message: Message): InboxPreviewCache

    abstract fun showSMSNotification(message: Message)

    override fun onMessageReceived(context: Context?, uri: Uri?) {
        com.beeper.sms.Log.d(TAG, "a new MMS message was received")

        if(context != null && uri != null) {
            com.beeper.sms.Log.d(TAG, "a new MMS message was received -> uri: $uri")

            val loadedMessage = MessageProvider(context).getMessage(uri)
            if(loadedMessage!=null){
                com.beeper.sms.Log.d(TAG, "updating inbox preview cache")
                val preview = mapMessageToInboxPreviewCache(loadedMessage)
                val inboxPreviewProvider = InboxPreviewProviderLocator.getInstance(context)
                inboxPreviewProvider.update(
                    preview
                )
                showSMSNotification(loadedMessage)
            }

            val syncWindowState = StartStopBridge.INSTANCE.syncWindowState.value

            if(syncWindowState == SyncWindowState.Running){
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
                            " starting sms bridge sync window to bridge a received MMS")
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
