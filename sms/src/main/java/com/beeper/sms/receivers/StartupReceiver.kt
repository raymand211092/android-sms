package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED != intent?.action
            || Intent.ACTION_MY_PACKAGE_REPLACED != intent.action) {
            return
        }

        // noop
    }
}