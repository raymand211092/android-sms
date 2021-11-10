package com.beeper.sms.commands.incoming

import com.beeper.sms.commands.TimeSeconds

data class GetMessagesAfter(
    override var chat_guid: String,
    var timestamp: TimeSeconds,
) : GroupMessaging
