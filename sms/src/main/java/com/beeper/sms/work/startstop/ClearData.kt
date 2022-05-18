package com.beeper.sms.work.startstop

import android.content.Context
import androidx.room.Room
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Log
import com.beeper.sms.R
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.GetRecentMessages
import com.beeper.sms.commands.outgoing.Chat
import com.beeper.sms.database.BridgedEntitiesDatabase
import com.beeper.sms.database.models.BridgedChatThread
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.extensions.getThread
import com.beeper.sms.provider.ChatThreadProvider
import com.beeper.sms.provider.GuidProvider.Companion.chatGuid
import com.beeper.sms.provider.MessageProvider
import kotlinx.coroutines.*

//TODO: IMPORTANT! Should implement getForegroundInfo for backward compatibility < Android 12
class ClearData constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG,"Clearing SMS bridge data...")
        return withContext(Dispatchers.Default) {
            val bridge = StartStopBridge.INSTANCE
            bridge.clearBridgeData(context)
            val database = Room.databaseBuilder(
                context,
                BridgedEntitiesDatabase::class.java, "sms-bridged-entities"
            ).build()
            database.bridgedMessageDao().clear()
            database.bridgedChatThreadDao().clear()
            database.close()
            //TODO -> clear sharedPrefs to mark that sms is disabled
            Result.success()
        }
    }

    companion object {
        private const val TAG = "ClearData"
    }
}