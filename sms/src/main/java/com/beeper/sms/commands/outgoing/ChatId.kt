package com.beeper.sms.commands.outgoing

data class ChatId(
    val old_guid: String,
    val new_guid: String,
)