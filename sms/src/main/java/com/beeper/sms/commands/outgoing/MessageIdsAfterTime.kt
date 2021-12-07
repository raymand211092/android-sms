package com.beeper.sms.commands.outgoing

data class MessageIdsAfterTime(
    var chat_guid: String,
    var after_time: Long,
)