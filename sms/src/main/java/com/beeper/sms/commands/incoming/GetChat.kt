package com.beeper.sms.commands.incoming

data class GetChat(var chat_guid: String) {
    data class Response(
        var title: String,
        var members: List<String>,
    )
}
