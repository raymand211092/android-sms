package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.provider.Telephony.Sms.Intents.EXTRA_IS_DEFAULT_SMS_APP
import android.util.Log
import com.beeper.sms.Bridge
import com.beeper.sms.StartStopBridge
import com.beeper.sms.work.WorkManager

class DefaultSmsAppBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        com.beeper.sms.Log.d(TAG, "onReceive: $intent")
        when (intent?.action) {
            Telephony.Sms.Intents.ACTION_EXTERNAL_PROVIDER_CHANGE,
            Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED ->
                if (intent.getBooleanExtra(EXTRA_IS_DEFAULT_SMS_APP, false)) {
                    com.beeper.sms.Log.d(TAG,"Beeper is now the Default SMS App")
                }else{
                    com.beeper.sms.Log.d(TAG,"Beeper is not the Default SMS App anymore")
                    com.beeper.sms.Log.d(TAG,"Clearing the SMS bridge data")
                    StartStopBridge.INSTANCE.disableSMSBridge(context)
                }
            else ->
                com.beeper.sms.Log.d(TAG, "Ignoring broadcast: ${intent?.action}")
        }
    }
    companion object {
        private const val TAG = "DefaultSmsAppReceiver"
    }
}
