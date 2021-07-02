package com.beeper.sms.commands.incoming

data class SendMessage(
    override var chat_guid: String,
    var text: String,
) : GroupMessaging {
    data class Response(
        var guid: String,
        var timestamp: Long,
    )
}