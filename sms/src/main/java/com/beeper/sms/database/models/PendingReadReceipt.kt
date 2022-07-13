package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.ReadReceipt

@Entity(primaryKeys = ["chat_guid"])
data class PendingReadReceipt(
    val chat_guid: String,
    var read_up_to: String,
    var read_at: Long,
)
