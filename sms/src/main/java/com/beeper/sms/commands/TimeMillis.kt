package com.beeper.sms.commands

import java.math.BigDecimal

class TimeMillis(timestamp: BigDecimal): Timestamp(timestamp) {
    fun toSeconds(): TimeSeconds = TimeSeconds(timestamp.toSeconds())

    companion object {
        private fun BigDecimal.toSeconds(): BigDecimal = divide(1000.toBigDecimal())

        fun Long.toMillis() = TimeMillis(this.toBigDecimal())
    }
}