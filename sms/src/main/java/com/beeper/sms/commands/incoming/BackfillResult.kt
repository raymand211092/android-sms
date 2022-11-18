package com.beeper.sms.commands.incoming

data class BackfillResult(
    var backfill_id: String,
    var chat_guid: String,
    var success: Boolean,
)