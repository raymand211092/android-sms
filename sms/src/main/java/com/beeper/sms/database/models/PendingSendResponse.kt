package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.ReadReceipt

@Entity(primaryKeys = ["guid"])
data class PendingSendResponse(
    val guid: String,
    val chat_guid: String,
    var status: String,
)
