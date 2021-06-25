package com.beeper.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var smsProvider: SmsProvider
    @Inject lateinit var bridge: Bridge

    override fun onReceive(context: Context, intent: Intent) {
        val uri = smsProvider.writeInboxMessage(intent)
        if (uri == null) {
            Log.e(TAG, "Failed to write message")
            return
        }
        bridge.send(uri)
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
