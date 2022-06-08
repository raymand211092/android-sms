package com.beeper.sms.commands.incoming

data class SendReadReceipt(
    override var chat_guid: String,
    var read_up_to: String,
) : GroupMessaging {
    override fun toString(): String {
        return "SendReadReceipt(chat_guid='$chat_guid', read_up_to='$read_up_to')"
    }
}
