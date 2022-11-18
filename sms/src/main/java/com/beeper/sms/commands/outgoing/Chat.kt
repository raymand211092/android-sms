package com.beeper.sms.commands.outgoing

data class Chat(
    var chat_guid: String,
    var thread_id: String,
    var title: String,
    var members: List<String>,
    var new_guid: String? = null,
    var no_create_room: Boolean = false,
)