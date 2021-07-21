package com.beeper.sms.commands

data class Error(
    var id: Int,
    var data: Reason,
) {
    val command = "error"

    data class Reason(
        var code: String,
        var message: String,
    )
}
