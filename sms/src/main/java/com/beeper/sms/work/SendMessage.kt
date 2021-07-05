package com.beeper.sms.work

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Bridge
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.provider.MmsProvider.Companion.isMms
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.commands.Command
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SendMessage @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bridge: Bridge,
    private val smsProvider: SmsProvider,
    private val mmsProvider: MmsProvider,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(URI)?.toUri()
        if (uri == null) {
            Log.e(TAG, "Missing uri")
            return Result.failure()
        }
        val message = if (uri.isMms) {
            mmsProvider.getMessage(uri)
        } else {
            smsProvider.getMessage(uri)
        }
        if (message == null) {
            Log.e(TAG, "Failed to find $uri")
            return Result.failure()
        }
        bridge.send(Command("message", message))
        return Result.success()
    }

    companion object {
        private const val TAG = "SendMessage"
        const val URI = "uri"
    }
}