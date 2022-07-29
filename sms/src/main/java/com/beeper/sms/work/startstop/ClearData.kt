package com.beeper.sms.work.startstop

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.BridgedEntitiesDatabase
import com.beeper.sms.database.models.*
import com.beeper.sms.provider.InboxPreviewProviderLocator
import com.beeper.sms.repository.ContactRepositoryLocator
import kotlinx.coroutines.*
import java.lang.Exception
import java.lang.IllegalStateException

class ClearData constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Log.d(TAG, "ClearData doWork()")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try {
                    setForeground(getForegroundInfo())
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Critical -> Couldn't set the work to run on foreground!")
                    return Result.failure()
                }
            }
            Log.d(TAG, "Clearing SMS bridge data...")
            return withContext(Dispatchers.Default) {
                val bridge = StartStopBridge.INSTANCE
                bridge.clearBridgeData(context)
                val database = BridgeDatabase.getInstance(context)
                database.bridgedMessageDao().clear()
                database.bridgedChatThreadDao().clear()
                database.bridgedReadReceiptDao().clear()
                database.pendingReadReceiptDao().clear()
                database.inboxPreviewCacheDao().clear()
                database.chatThreadMetadataCache().clear()
                database.contactCacheDao().clear()
                database.pendingContactUpdateDao().clear()

                Result.success()
            }
        }catch(e : Exception){
            Log.e(TAG, e)
            val bridge = StartStopBridge.INSTANCE
            bridge._workerExceptions.tryEmit(Pair(TAG,e))
            bridge.stop()
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val contentText = context.getString(R.string.notification_body_clearing_data)
        return getDefaultForegroundInfo(applicationContext,contentText)
    }

    companion object {
        private const val TAG = "ClearData"
    }
}