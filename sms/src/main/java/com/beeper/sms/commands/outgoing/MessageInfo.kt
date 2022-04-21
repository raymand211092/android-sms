package com.beeper.sms.commands.outgoing

import android.net.Uri
import com.beeper.sms.commands.TimeSeconds

data class MessageInfo(
    var guid: String,
    var timestamp: TimeSeconds,
    var chat_guid: String,
    var uri: Uri,
    var creator: String?,
    var sent_from_matrix: Boolean
)