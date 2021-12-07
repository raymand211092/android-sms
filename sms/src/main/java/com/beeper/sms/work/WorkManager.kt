package com.beeper.sms.work

import android.content.Context
import android.net.Uri
import androidx.work.*
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WorkManager constructor(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun sendMessage(uri: Uri) {
        OneTimeWorkRequest
            .Builder(SendMessage::class.java)
            .setBackoffCriteria(
                SendMessage.RETRY_POLICY,
                SendMessage.RETRY_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(Data.Builder().putString(SendMessage.URI, uri.toString()).build())
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
            .apply { workManager.enqueue(this) }
    }

    fun syncDb() {
        OneTimeWorkRequest
            .Builder(DatabaseSyncWork::class.java)
            .setInitialDelay(1, TimeUnit.SECONDS)
            .build()
            .apply {
                workManager.enqueueUniqueWork(WORK_SYNC_DB, ExistingWorkPolicy.REPLACE, this)
            }
    }

    companion object {
        private const val WORK_SYNC_DB = "sync_db"
    }
}