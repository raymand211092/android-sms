package com.beeper.sms

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BridgeService : JobIntentService() {

    @Inject lateinit var bridge: Bridge
    @Inject lateinit var commandProcessor: com.beeper.sms.CommandProcessor

    override fun onHandleWork(intent: Intent) {
        Log.d(TAG, "starting service")
        bridge.stdout.forEachLine {
            if (it.startsWith("{") && it.endsWith("}")) {
                Log.d(TAG, "receive: $it")
                commandProcessor.handle(it)
            } else {
                Log.d(TAG, it)
            }
        }
    }

    companion object {
        private const val TAG = "BridgeService"
        private const val JOB_ID = 19105

        fun start(context: Context) {
            enqueueWork(context, BridgeService::class.java, JOB_ID, Intent())
        }
    }
}