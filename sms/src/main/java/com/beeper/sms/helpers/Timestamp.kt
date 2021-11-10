package com.beeper.sms.helpers

import com.beeper.sms.commands.TimeMillis.Companion.toMillis

fun currentTimeSeconds() = System.currentTimeMillis().toMillis().toSeconds()