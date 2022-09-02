package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.ReadReceipt

@Entity(primaryKeys = ["room_id"], indices = [
    Index(value = ["room_id"]),
    Index(value = ["thread_id"]),
    Index(value = ["chat_guid"]),
    Index(value = ["timestamp"])
])

data class SmsThreadMatrixRoomRelation(
    val room_id: String,
    val thread_id: Long,
    val chat_guid: String,
    val timestamp: Long,
)
