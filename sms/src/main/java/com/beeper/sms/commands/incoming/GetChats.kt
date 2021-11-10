package com.beeper.sms.commands.incoming

import com.beeper.sms.commands.TimeSeconds

data class GetChats(var min_timestamp: TimeSeconds)