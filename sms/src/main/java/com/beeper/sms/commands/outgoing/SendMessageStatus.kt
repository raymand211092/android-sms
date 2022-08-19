package com.beeper.sms.commands.outgoing

sealed class SendMessageStatusResult{
    abstract val status : String
    object Sent : SendMessageStatusResult(){
        override val status: String = "sent"
    }
    object Failed : SendMessageStatusResult(){
        override val status: String = "failed"
    }
}

data class SendMessageStatus(
    var guid: String,
    var chat_guid: String,
    var status: String,
)