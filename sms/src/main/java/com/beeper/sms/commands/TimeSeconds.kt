package com.beeper.sms.commands

import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class TimeSeconds(timestamp: BigDecimal): Timestamp(timestamp), Comparable<TimeSeconds> {

    constructor(timestamp: Double) : this(BigDecimal.valueOf(timestamp))

    fun toMillis(): TimeMillis = TimeMillis(timestamp.toMillis())

    override fun compareTo(other: TimeSeconds): Int = timestamp.compareTo(other.timestamp)

    fun max(maxOf: TimeSeconds) = if (compareTo(maxOf) >= 0) this else maxOf

    fun minusMinutes(minutes: Long) =
        TimeSeconds(timestamp.minus(TimeUnit.MINUTES.toSeconds(minutes).toBigDecimal()))

    companion object {
        val GSON_TYPE: Type = object : TypeToken<TimeSeconds>() {}.type
        val GSON_ADAPTER = object : TypeAdapter<TimeSeconds>() {
            override fun write(writer: JsonWriter, value: TimeSeconds) {
                writer.value(value.timestamp)
            }

            override fun read(reader: JsonReader): TimeSeconds {
                return TimeSeconds(reader.nextDouble())
            }

        }

        private fun BigDecimal.toMillis(): BigDecimal = multiply(1000.toBigDecimal())

        fun Long.toSeconds() = TimeSeconds(this.toBigDecimal())

        fun String.toSeconds() = TimeSeconds(BigDecimal(this))
    }
}