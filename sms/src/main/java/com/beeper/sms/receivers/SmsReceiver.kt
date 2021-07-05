package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var smsProvider: SmsProvider
    @Inject lateinit var workManager: WorkManager

    override fun onReceive(context: Context, intent: Intent) {
        val uri = smsProvider.writeInboxMessage(intent)
        if (uri == null) {
            Log.e(TAG, "Failed to write message")
            return
        }
        workManager.sendMessage(uri)
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
