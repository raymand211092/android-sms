package com.beeper.sms.commands.internal

import com.beeper.sms.commands.outgoing.Message

data class BridgeThisSentSmsOrMms(
    var message: Message,
)