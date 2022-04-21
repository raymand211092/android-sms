package com.beeper.sms.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Bridge
import com.beeper.sms.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.MessageIdsAfterTime
import com.beeper.sms.extensions.getSharedPreferences
import com.beeper.sms.extensions.getTimeSeconds
import com.beeper.sms.extensions.putTimeSeconds
import com.beeper.sms.provider.MessageProvider

class DatabaseSyncWork constructor(
    context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {
    private val prefs = context.getSharedPreferences()
    private val workManager = WorkManager(context)
    private val messageProvider = MessageProvider(context)

    override suspend fun doWork(): Result {
        val lastTimestamp = prefs.getTimeSeconds(PREF_LATEST_SYNC)?.minusMinutes(5)
        if (lastTimestamp == null) {
            Log.e(TAG, "sync not initialized")
            return Result.failure()
        }
        val messages = messageProvider.getActiveChats(lastTimestamp)
        Log.d(TAG, messages.toString())
        for ((chatGuid, messageList) in messages.groupBy { it.chat_guid }) {
            val response = Bridge.INSTANCE.await(
                Command("message_ids_after_time", MessageIdsAfterTime(chatGuid, lastTimestamp))
            )
            val ids =
                response
                    .takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("ids")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.map { it.asString }
                    ?: emptyList()
            messageList
                .filterNot { it.sent_from_matrix || ids.contains(it.guid) }
                .also { Log.d(TAG, "bridging ${it.size} messages: $it") }
                .forEach { workManager.sendMessage(it.uri) }
        }
        messages
            .maxOfOrNull { it.timestamp }
            ?.let { prefs.putTimeSeconds(PREF_LATEST_SYNC, lastTimestamp.max(it)) }
        return Result.success()
    }

    companion object {
        private const val TAG = "DatabaseSyncWork"
        const val PREF_LATEST_SYNC = "latest_sync"
    }
}