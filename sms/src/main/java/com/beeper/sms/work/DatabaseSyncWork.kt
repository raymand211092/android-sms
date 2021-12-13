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
import com.beeper.sms.provider.GuidProvider
import com.beeper.sms.provider.MessageProvider

class DatabaseSyncWork constructor(
    context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {
    private val prefs = context.getSharedPreferences()
    private val workManager = WorkManager(context)
    private val messageProvider = MessageProvider(context)
    private val guidProvider = GuidProvider(context)

    override suspend fun doWork(): Result {
        val lastTimestamp = prefs.getTimeSeconds(PREF_LATEST_SYNC)
        if (lastTimestamp == null) {
            Log.e(TAG, "sync not initialized")
            return Result.failure()
        }
        val messages = messageProvider.getMessagesAfter(lastTimestamp).groupBy { it.thread }
        for ((thread, messageList) in messages) {
            if (thread == null) {
                continue
            }
            val chatGuid = guidProvider.getChatGuid(thread) ?: continue
            val response = Bridge.INSTANCE.await(
                Command(
                    "message_ids_after_time",
                    MessageIdsAfterTime(chatGuid, lastTimestamp)
                )
            )
            val ids =
                response
                    .takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("ids")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.map { it.asString }
                    ?: emptyList()
            messageList
                .filterNot { ids.contains(it.guid) }
                .mapNotNull { it.uri }
                .also { Log.d(TAG, "bridging ${it.size} messages: ${it.joinToString(",")}") }
                .forEach { workManager.sendMessage(it) }
        }
        prefs.putTimeSeconds(
            PREF_LATEST_SYNC,
            lastTimestamp.max(messages.values.flatten().maxOf { it.timestamp })
        )
        return Result.success()
    }

    companion object {
        private const val TAG = "DatabaseSyncWork"
        const val PREF_LATEST_SYNC = "latest_sync"
    }
}