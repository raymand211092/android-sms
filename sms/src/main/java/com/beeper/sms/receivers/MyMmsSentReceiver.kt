package com.beeper.sms.receivers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.beeper.sms.extensions.IntentExtensions.printExtras
import com.klinker.android.send_message.MmsSentReceiver

class MyMmsSentReceiver : MmsSentReceiver() {
    override fun onMessageStatusUpdated(context: Context?, intent: Intent?, resultCode: Int) {
        Log.d(TAG, "intent: ${intent.printExtras()}")
    }

    companion object {
        private const val TAG = "MyMmsSentReceiver"
    }
}