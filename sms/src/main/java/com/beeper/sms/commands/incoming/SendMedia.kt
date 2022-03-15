package com.beeper.sms.commands.incoming

import com.beeper.sms.commands.TimeSeconds

data class SendMedia(
    override var chat_guid: String,
    var text: String,
    var path_on_disk: String,
    var file_name: String,
    var mime_type: String,
) : GroupMessaging {
    data class Response(
        var guid: String,
        var timestamp: TimeSeconds,
    )
}