package com.beeper.sms.database.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class InfiniteBackfillChatEntry(
        @PrimaryKey val thread_id: Long,
        val oldest_bridged_message: String?,
        val count: Long,
        val bridged_count: Long,
        val backfill_finished: Boolean,
        val newest_message_date: Long,
        @ColumnInfo(defaultValue = "0")
        val retryCount : Long
    )