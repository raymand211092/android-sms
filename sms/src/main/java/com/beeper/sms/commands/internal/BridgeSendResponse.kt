package com.beeper.sms.commands.internal

import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.commands.outgoing.SendMessageStatus

data class BridgeSendResponse(
    var commandId: Int,
    var bridgedMessage: BridgedMessage,
    var status: SendMessageStatus,
)