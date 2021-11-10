package com.beeper.sms.commands

import com.beeper.sms.commands.incoming.GetMessagesAfter
import com.beeper.sms.helpers.newGson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TimeSecondsTest {
    private val gson = newGson()

    @Test
    fun gsonSerialization() {
        val command = GetMessagesAfter(
            "SMS;-;+15551234567",
            TimeSeconds(1639006730.000)
        )
        assertEquals(
            """{"chat_guid":"SMS;-;+15551234567","timestamp":1.63900673E+9}""",
            gson.toJson(command)
        )
    }

    @Test
    fun gsonDeserialization() {
        val command = """{"chat_guid":"SMS;-;+15551234567","timestamp":1639002197.952}"""
        val expected = GetMessagesAfter(
            "SMS;-;+15551234567",
            TimeSeconds(1639002197.952)
        )
        assertEquals(expected, gson.fromJson(command, GetMessagesAfter::class.java))
    }

    @Test
    fun toMillis() {
        assertEquals(
            TimeMillis(BigDecimal(1639002197952.000)),
            TimeSeconds(1639002197.952).toMillis()
        )
    }
}