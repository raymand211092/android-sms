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

abstract class BackfillSuccess: BroadcastReceiver() {
    abstract fun onBackfillSuccess()
    override fun onReceive(context: Context, intent: Intent) {
        com.beeper.sms.Log.d(TAG, "BackfillSuccess")
        onBackfillSuccess()
    }
    companion object {
        private const val TAG = "BackfillSuccess"
        const val ACTION = "com.beeper.sms.BACKFILL_SUCCESS"
    }
}
