package com.beeper.sms.commands.incoming

data class ChatBridgeResult (
    override var chat_guid: String,
    var mxid: String,
) : GroupMessaging
