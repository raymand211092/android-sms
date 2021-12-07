package com.beeper.sms.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beeper.sms.Bridge
import com.beeper.sms.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.MessageIdsAfterTime
import com.beeper.sms.extensions.getSharedPreferences
import com.beeper.sms.extensions.putLong
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.provider.MmsProvider.Companion.MMS_PREFIX
import com.beeper.sms.provider.SmsProvider
import com.beeper.sms.provider.ThreadProvider
import kotlin.math.max

class DatabaseSyncWork constructor(
    private val context: Context,
    workerParams: WorkerParameters,
): CoroutineWorker(context, workerParams) {
    private val prefs = context.getSharedPreferences()
    private val workManager = WorkManager(context)
    private val threadProvider = ThreadProvider(context)

    override suspend fun doWork(): Result {
        val lastTimestamp = prefs.getLong(PREF_LATEST_SYNC, 0L)
        if (lastTimestamp <= 0) {
            Log.e(TAG, "sync not initialized")
            return Result.failure()
        }
        val messages =
            MmsProvider(context).getMessagesAfter(lastTimestamp / 1000)
                .plus(SmsProvider(context).getMessagesAfter(lastTimestamp))
                .sortedBy { it.timestamp }
                .groupBy { it.thread }
        for ((thread, messageList) in messages) {
            if (thread == null) {
                continue
            }
            val chatGuid = threadProvider.getChatGuid(thread) ?: continue
            val response = Bridge.INSTANCE.await(
                Command(
                    "message_ids_after_time",
                    MessageIdsAfterTime(chatGuid, lastTimestamp / 1000)
                )
            )
            val ids =
                response
                    .takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("ids")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.map { it.asString }
                    ?: emptyList()
            messageList
                .filterNot { ids.contains(it.guid.removePrefix(MMS_PREFIX)) }
                .mapNotNull { it.uri }
                .also { Log.d(TAG, "bridging ${it.size} messages: ${it.joinToString(",")}") }
                .forEach { workManager.sendMessage(it) }
        }
        prefs.putLong(
            PREF_LATEST_SYNC,
            max(lastTimestamp, messages.values.flatten().maxOf { it.timestamp } * 1000)
        )
        return Result.success()
    }

    companion object {
        private const val TAG = "DatabaseSyncWork"
        const val PREF_LATEST_SYNC = "latest_sync"
    }
}