package com.beeper.sms.commands.internal

import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.commands.outgoing.Error

data class BridgeSendResponse(
    var commandId: Int,
    var bridgedMessage: BridgedMessage,
    var response: SendMessage.Response,
    var error: Error? = null,
)