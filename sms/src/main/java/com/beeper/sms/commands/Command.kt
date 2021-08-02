package com.beeper.sms.commands

data class Command(
    var command: String,
    var data: Any?,
    var id: Int? = null
)