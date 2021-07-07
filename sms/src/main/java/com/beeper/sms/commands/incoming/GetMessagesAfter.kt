package com.beeper.sms.commands.incoming

data class GetMessagesAfter(
    override var chat_guid: String,
    var timestamp: Long,
) : GroupMessaging
