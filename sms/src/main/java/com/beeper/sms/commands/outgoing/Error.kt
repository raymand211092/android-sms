package com.beeper.sms.commands.outgoing

data class Error(
    var code: String,
    var message: String,
)
