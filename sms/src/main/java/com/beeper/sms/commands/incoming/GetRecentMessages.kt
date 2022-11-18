package com.beeper.sms.commands.incoming

data class GetRecentMessages(
    override var chat_guid: String,
    var limit: Float,
    var backfill_id: String
) : GroupMessaging