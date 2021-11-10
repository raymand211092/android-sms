package com.beeper.sms.commands.outgoing

import com.beeper.sms.commands.TimeSeconds

data class MessageIdsAfterTime(
    var chat_guid: String,
    var after_time: TimeSeconds,
)