package com.beeper.sms.commands.internal

import com.beeper.sms.provider.ContactRow

data class BridgeContactUpdate(
    var contactRow: ContactRow,
)
