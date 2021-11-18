package com.beeper.sms.commands.outgoing

import java.io.Serializable

data class PushKey(
    var url: String,
    var app_id: String,
    var pushkey: String,
): Serializable