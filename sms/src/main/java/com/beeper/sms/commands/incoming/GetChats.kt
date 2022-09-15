package com.beeper.sms.commands.incoming

import com.beeper.sms.commands.TimeSeconds

data class GetChats(var min_timestamp: TimeSeconds)

data class ContactIdentifier(var chat_guid: String, var thread_id: String)