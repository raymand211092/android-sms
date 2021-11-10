package com.beeper.sms.commands

import java.math.BigDecimal

abstract class Timestamp(protected val timestamp: BigDecimal) {
    fun toLong(): Long = timestamp.toLong()

    override fun toString(): String = timestamp.toPlainString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Timestamp

        if (timestamp.compareTo(other.timestamp) != 0) return false

        return true
    }

    override fun hashCode(): Int {
        return timestamp.hashCode()
    }
}