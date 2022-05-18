package com.beeper.sms.commands.incoming

data class StartupSyncHookResponse(
    var skip_sync: Boolean,
)