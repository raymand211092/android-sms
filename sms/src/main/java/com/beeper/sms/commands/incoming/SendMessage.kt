package com.beeper.sms.commands.incoming

import com.beeper.sms.BuildConfig
import com.beeper.sms.commands.TimeSeconds
import java.math.BigDecimal

data class SendMessage(
    override var chat_guid: String,
    var text: String,
) : GroupMessaging {
    data class Response(
        var guid: String,
        var timestamp: TimeSeconds,
    )

    override fun toString(): String {
        return "SendMessage(chat_guid='$chat_guid', text='${if (BuildConfig.DEBUG) text else "<redacted>"}')"
    }
}