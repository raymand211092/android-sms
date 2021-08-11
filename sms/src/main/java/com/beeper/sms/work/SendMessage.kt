package com.beeper.sms.work

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Bridge
import com.beeper.sms.commands.Command
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.provider.MmsProvider.Companion.isMms
import com.beeper.sms.provider.SmsProvider

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
        val message = if (uri.isMms) {
            MmsProvider(context).getMessage(uri)
        } else {
            SmsProvider(context).getMessage(uri)
        }
        if (message == null) {
            Log.e(TAG, "Failed to find $uri")
            return Result.failure()
        }
        if (message.sent_from_matrix) {
            Log.d(TAG, "Message originated from Matrix: $uri")
            return Result.success()
        }
        Bridge.INSTANCE.send(Command("message", message))
        return Result.success()
    }

    companion object {
        private const val TAG = "SendMessage"
        const val URI = "uri"
    }
}