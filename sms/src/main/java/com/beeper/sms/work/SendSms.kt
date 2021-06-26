package com.beeper.sms.work

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Bridge
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SendSms @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bridge: Bridge,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(URI)?.toUri() ?: return Result.failure()
        bridge.send(uri)
        return Result.success()
    }

    companion object {
        const val URI = "uri"
    }
}