package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.ReadReceipt

@Entity(primaryKeys = ["msg_guid", "thread_id"])
data class SMSNotificationInfo(
    val msg_guid: String,
    val thread_id: Long,
    val added_at: Long
)
