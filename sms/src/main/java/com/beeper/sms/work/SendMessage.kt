package com.beeper.sms.work

import android.content.Context
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Bridge
import com.beeper.sms.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.provider.MessageProvider

class SendMessage constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(URI)?.toUri()
        if (uri == null) {
            Log.e(TAG, "Missing uri")
            return Result.failure()
        }
        Log.d(TAG, uri.toString())
        val message = MessageProvider(context).getMessage(uri)
        if (message?.is_mms == true &&
            message.attachments.isNullOrEmpty() &&
            message.text.isEmpty()
        ) {
            return if (runAttemptCount > MAX_RETRY) {
                Log.e(TAG, "Gave up waiting for attachment: $uri")
                Result.failure()
            } else {
                Log.d(TAG, "Waiting for attachment: $uri -> $this, runAttemptCount: $runAttemptCount")
                Result.retry()
            }
        }
        if (message == null) {
            Log.e(TAG, "Failed to find $uri")
            return Result.failure()
        }
        if (message.creator == context.applicationInfo.packageName) {
            Log.e(TAG, "Message originated from Matrix: $uri")
            return Result.success()
        }
        if (message.is_from_me &&
            message.is_mms &&
            message.resp_st == null &&
            message.creator == "com.android.mms.service"
        ) {
            Log.w(TAG, "Retrying $uri because resp_st=${message.resp_st}")
            return Result.retry()
        }
        // waiting for response from mautrix-imessage before returning success
        Bridge.INSTANCE.await(Command("message", message))
        return Result.success()
    }

    companion object {
        private const val TAG = "SendMessage"
        val RETRY_POLICY = BackoffPolicy.LINEAR
        const val RETRY_INTERVAL_MS = 10_000L
        private const val MAX_RETRY = 30
        const val URI = "uri"
    }
}