package com.beeper.sms.commands.outgoing

import com.beeper.sms.commands.TimeSeconds

data class ReadReceipt(
    var chat_guid: String,
    var is_from_me: Boolean = true,
    var read_up_to: String,
    var read_at: TimeSeconds,
)
