package com.beeper.sms.commands.incoming

data class MessageBridgeResult(
    var chat_guid: String,
    var message_guid: String,
    var event_id: String?,
    var success: Boolean,
)