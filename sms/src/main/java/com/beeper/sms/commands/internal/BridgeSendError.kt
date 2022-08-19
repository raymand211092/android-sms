package com.beeper.sms.commands.internal

import com.beeper.sms.commands.outgoing.SendMessageStatus

data class BridgeSendError(
    var commandId: Int,
    var status: SendMessageStatus,
)