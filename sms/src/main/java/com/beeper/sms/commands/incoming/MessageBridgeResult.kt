package com.beeper.sms.commands.incoming

data class MessageBridgeResult(
    var chat_guid: String,
    var message_guid: String,
    var success: Boolean,
)