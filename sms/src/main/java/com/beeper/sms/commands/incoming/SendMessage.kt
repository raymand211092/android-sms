package com.beeper.sms.commands.incoming

data class SendMessage(
    var chat_guid: String,
    var text: String,
) {
    data class Response(
        var guid: String,
        var timestamp: Long,
    )
}