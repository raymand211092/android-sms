package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.Index

@Entity(primaryKeys = ["chat_guid"], indices = [
    Index(value = ["thread_id"], unique = true),
    Index(value = ["mx_id"], unique = true),
])
data class ChatThreadMetadataCache(
    val chat_guid: String,
    var thread_id: String,
    var mx_id: String,
    var title: String,
    var recipient_ids: String,
)
