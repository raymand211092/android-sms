package com.beeper.sms.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.*
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

class WorkManager constructor(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun sendMessage(uri: Uri) {
        Log.d(TAG, uri.toString())
        OneTimeWorkRequest
            .Builder(SendMessage::class.java)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10L, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(SendMessage.URI, uri.toString()).build())
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
            .apply { workManager.enqueue(this) }
    }

    fun backfillSentMMS() {
        OneTimeWorkRequest
            .Builder(BackfillSentMMS::class.java)
            .setInitialDelay(1, TimeUnit.SECONDS)
            .build()
            .apply {
                workManager.enqueueUniqueWork(WORK_BACKFILL, ExistingWorkPolicy.REPLACE, this)
            }
    }

    companion object {
        private const val TAG = "WorkManager"
        private const val WORK_BACKFILL = "backfill"
    }
}