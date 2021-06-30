package com.beeper.sms.receivers

import android.content.Context
import android.net.Uri
import android.util.Log

class MmsReceiver: com.klinker.android.send_message.MmsReceivedReceiver()  {
    override fun onMessageReceived(context: Context?, uri: Uri?) {
        Log.d(TAG, "Received MMS: $uri")
    }

    override fun onError(context: Context?, error: String?) {
        Log.e(TAG, "Error: $error")
    }

    companion object {
        private const val TAG = "MmsReceiver"
    }
}
