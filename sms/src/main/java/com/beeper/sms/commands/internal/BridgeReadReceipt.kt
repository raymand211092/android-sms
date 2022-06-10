package com.beeper.sms.commands.internal

import com.beeper.sms.commands.outgoing.ReadReceipt

data class BridgeReadReceipt(
    var readReceipt: ReadReceipt,
)