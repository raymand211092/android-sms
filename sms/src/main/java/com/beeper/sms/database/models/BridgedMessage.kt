package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(primaryKeys = ["chat_guid","message_id", "is_mms"])
data class BridgedMessage(
    val chat_guid: String,
    val message_id: Long,
    val is_mms: Boolean
)