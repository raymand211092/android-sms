package com.beeper.sms.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BridgedChatThread(
    @PrimaryKey val chat_guid: Long,
)