package com.beeper.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.beeper.sms.extensions.getText
import com.beeper.sms.extensions.recipients

// Adapted from https://android.googlesource.com/platform/packages/apps/Messaging/+/master/src/com/android/messaging/datamodel/NoConfirmationSmsSendService.java
class HeadlessSmsSendService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("HeadlessSmsSendService","Headless SMS send service onStartCommand")
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            Log.d("HeadlessSmsSendService","Stopping -> " +
                    "action isn't ACTION_RESPOND_VIA_MESSAGE")
            return stop(startId)
        }
        val message = intent.getText(Intent.EXTRA_TEXT)
        if(message == null){
            Log.d("HeadlessSmsSendService",
                "intent.getText(Intent.EXTRA_TEXT) is null")
            return stop(startId)
        }
        val recipients = intent.recipients
        if(recipients == null){
            Log.d("HeadlessSmsSendService",
                "recipients field is null")
            return stop(startId)
        }
        Log.d("HeadlessSmsSendService","SmsMmsSender sendMessage")

        SmsMmsSender(applicationContext).sendMessage(
            message,
            recipients,
            sentMessageParcelable = intent,
            subject = intent.getText(Intent.EXTRA_SUBJECT)
        )
        return stop(startId)
    }

    private fun stop(startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
