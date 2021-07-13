package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.work.WorkManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val uri = SmsProvider(context).writeInboxMessage(intent)
        if (uri == null) {
            Log.e(TAG, "Failed to write message")
            return
        }
        WorkManager(context).sendMessage(uri)
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
