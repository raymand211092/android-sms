package com.beeper.sms.receivers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.beeper.sms.work.WorkManager
import com.klinker.android.send_message.MmsReceivedReceiver

class MmsReceiver : MmsReceivedReceiver() {

    override fun onMessageReceived(context: Context, uri: Uri) {
        WorkManager(context).sendMessage(uri)
    }

    override fun onError(context: Context, error: String?) {
        Log.e(TAG, error ?: "Error")
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}
