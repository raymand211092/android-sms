package com.beeper.sms.commands.incoming

data class GetRecentMessages(
    var chat_guid: String,
    var limit: Float,
)