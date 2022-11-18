package com.beeper.sms.work.startstop

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.database.BridgeDatabase
import kotlinx.coroutines.*
import java.lang.Exception
import java.lang.IllegalStateException

class ClearData constructor(
    private val context: Context,
    private val workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val deleteBridgeDB =
                workerParams.inputData.getBoolean(
                    DELETE_BRIDGE_DB_PARAM_KEY,
                    false
                )
            Log.d(TAG, "ClearData doWork() shouldDeleteBridgeDB: $deleteBridgeDB")

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
                bridge.clearBridgeData(context, deleteBridgeDB)
                val database = BridgeDatabase.getInstance(context)
                database.bridgedMessageDao().clear()
                database.bridgedChatThreadDao().clear()
                database.bridgedReadReceiptDao().clear()
                database.pendingReadReceiptDao().clear()
                database.pendingRecipientUpdateDao().clear()
                database.notificationInfoDao().clear()
                database.infiniteBackfillChatEntryDao().clear()
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
        const val DELETE_BRIDGE_DB_PARAM_KEY = "delete_bridge_db"
    }
}