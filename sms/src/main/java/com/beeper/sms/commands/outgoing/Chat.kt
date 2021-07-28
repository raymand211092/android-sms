package com.beeper.sms.commands.outgoing

data class Chat(
    var chat_guid: String,
    var title: String,
    var members: List<String>,
)