package com.beeper.sms.commands

data class Error(
    var id: Int,
    var data: Reason,
) {
    data class Reason(
        var code: String,
        var message: String,
    )
}
