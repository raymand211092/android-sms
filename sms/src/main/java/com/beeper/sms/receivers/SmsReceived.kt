package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        com.beeper.sms.Log.d(TAG, "Starting sms bridge sync window")
        startSyncWindow()
    }
    companion object {
        private const val TAG = "SmsReceived"
    }
}
