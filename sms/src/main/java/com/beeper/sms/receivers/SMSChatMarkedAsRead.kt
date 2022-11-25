package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.internal.BridgeThisSmsOrMms
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.database.models.InboxPreviewCache
import com.beeper.sms.provider.InboxPreviewProvider
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.SmsProvider

abstract class SMSChatMarkedAsRead: BroadcastReceiver() {
    abstract fun onChatMarkedAsRead(threadId: String)
    override fun onReceive(context: Context, intent: Intent) {
        com.beeper.sms.Log.d(TAG, "SMSChatMarkedAsRead")
        val threadId = intent.extras?.getString(THREAD_ID_EXTRA_KEY)
        if(threadId != null) {
            onChatMarkedAsRead(threadId)
        }else{
            Log.w(TAG, "SMSChatMarkedAsRead null threadId")
        }
    }
    companion object {
        private const val TAG = "SMSChatMarkedAsRead"
        const val THREAD_ID_EXTRA_KEY = "thread_id"
        const val ACTION = "com.beeper.sms.CHAT_MARKED_AS_READ"
    }
}
