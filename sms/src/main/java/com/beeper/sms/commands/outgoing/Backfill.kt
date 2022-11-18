package com.beeper.sms.commands.outgoing

import com.beeper.sms.BuildConfig

data class Backfill(
    var chat_guid: String,
    var messages: List<Message>
){
    override fun toString(): String {
        return "Backfill(chat_guid='$chat_guid', messages:'${messages.map { it.guid }}')"
    }
}