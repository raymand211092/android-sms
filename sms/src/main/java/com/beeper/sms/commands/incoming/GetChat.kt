package com.beeper.sms.commands.incoming

data class GetChat(override var chat_guid: String, var thread_id: String) : GroupMessaging {
    data class Response(
        var title: String,
        var members: List<String>,
        var thread_id: String
    )
}
