package com.beeper.sms.commands

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TimeMillisTest {
    @Test
    fun toSeconds() {
        assertEquals(
            TimeSeconds(1639002197.952),
            TimeMillis(BigDecimal(1639002197952.000)).toSeconds()
        )
    }
}