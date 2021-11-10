package com.beeper.sms.work

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.provider.ThreadProvider

class BackfillSentMMS constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val messageIds = ThreadProvider(context).getMmsMessagesAfter(lastTimestamp)
        val messages = MmsProvider(context).getMessages(messageIds)
        if (messages.isEmpty()) {
            Log.d(TAG, "No new messages")
            return Result.success()
        }
        val workManager = WorkManager(context)
        messages
            .map { Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, it.rowId.toString()) }
            .forEach(workManager::sendMessage)
        messages.maxOfOrNull { it.timestamp }?.let { lastTimestamp = it }
        return Result.success()
    }

    companion object {
        private const val TAG = "BackfillSentMMS"
        internal var lastTimestamp = 0L
            set(value) {
                Log.d(TAG, "lastTimestamp $field -> $value")
                field = value
            }
    }
}