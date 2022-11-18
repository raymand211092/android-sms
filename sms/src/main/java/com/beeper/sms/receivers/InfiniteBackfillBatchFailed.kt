package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

abstract class InfiniteBackfillBatchFailed : BroadcastReceiver() {
    abstract fun onInfiniteBackfillBatchFailed()
    override fun onReceive(context: Context, intent: Intent) {
        com.beeper.sms.Log.d(TAG, "InfiniteBackfillBatchFailed")
        onInfiniteBackfillBatchFailed()
    }
    companion object {
        private const val TAG = "InfiniteBackfillBatchFailed"
        const val ACTION = "com.beeper.sms.INFINITE_BACKFILL_BATCH_FAILED"
    }
}
