package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["chat_guid"])
data class BridgedReadReceipt(
    val chat_guid: String,
    val read_up_to: String,
    val read_up_to_timestamp : Long
)
