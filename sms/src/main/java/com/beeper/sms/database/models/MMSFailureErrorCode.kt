package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.outgoing.ReadReceipt

@Entity(primaryKeys = ["msg_guid"])
data class MMSFailureErrorCode(
    val msg_guid: String,
    val error_code: Int
)
