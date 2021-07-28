package com.beeper.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.beeper.sms.extensions.getText
import com.beeper.sms.extensions.recipients

// I don't think this would ever get invoked, it's here just in case
// Adapted from https://android.googlesource.com/platform/packages/apps/Messaging/+/master/src/com/android/messaging/datamodel/NoConfirmationSmsSendService.java
class HeadlessSmsSendService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            return stop(startId)
        }
        val message = intent.getText(Intent.EXTRA_TEXT) ?: return stop(startId)
        val recipients = intent.recipients ?: return stop(startId)
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