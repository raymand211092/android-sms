package com.beeper.sms.commands.incoming

import com.beeper.sms.commands.incoming.SendMessage.Companion.isDirectMessage
import com.beeper.sms.commands.incoming.SendMessage.Companion.removePrefix

data class SendMedia(
    var chat_guid: String,
    var path_on_disk: String,
    var file_name: String,
    var mime_type: String,
) {
    data class Reponse(
        var guid: String,
        var timestamp: String,
    )

    val recipients: String
        get() = chat_guid.removePrefix()

    val isDirectMessage: Boolean
        get() = chat_guid.isDirectMessage
}