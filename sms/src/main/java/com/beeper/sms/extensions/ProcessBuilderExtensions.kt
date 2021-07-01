package com.beeper.sms.extensions

fun ProcessBuilder.env(vararg entries: Pair<String, String>): ProcessBuilder {
    entries.forEach {
        environment()[it.first] = it.second
    }
    return this
}