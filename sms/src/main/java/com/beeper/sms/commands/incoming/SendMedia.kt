package com.beeper.sms.commands.incoming

data class SendMedia(
    override var chat_guid: String,
    var path_on_disk: String,
    var file_name: String,
    var mime_type: String,
) : GroupMessaging {
    data class Response(
        var guid: String,
        var timestamp: Long,
    )
}