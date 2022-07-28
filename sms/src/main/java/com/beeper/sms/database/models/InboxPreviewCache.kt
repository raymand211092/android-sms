package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class InboxMessageStatus{
    PENDING,
    SUCCESS,
    FAILURE
}

@Entity(primaryKeys = ["thread_id"], indices = [
    Index(value = ["chat_guid"], unique = true),
    Index(value = ["message_guid"], unique = true),
])
data class InboxPreviewCache(
    val thread_id: Long,
    val chat_guid: String,
    val message_guid: String,
    val recipient_ids: String?,
    val preview: String,
    val timestamp: Long,
    val send_status: InboxMessageStatus,
    val is_read: Boolean,
)