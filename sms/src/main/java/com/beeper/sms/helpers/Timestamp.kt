package com.beeper.sms.helpers

import com.beeper.sms.commands.TimeMillis.Companion.toMillis

fun currentTimeMillis() = System.currentTimeMillis().toMillis()

fun currentTimeSeconds() = currentTimeMillis().toSeconds()
