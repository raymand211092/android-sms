package com.beeper.sms.commands.outgoing

import com.beeper.sms.BuildConfig
import java.io.Serializable

data class PushKey(
    var url: String,
    var app_id: String,
    var pushkey: String,
): Serializable {
    override fun toString(): String {
        return "PushKey(url='$url', app_id='$app_id', pushkey='${if (BuildConfig.DEBUG) pushkey else "<redacted>"}')"
    }
}