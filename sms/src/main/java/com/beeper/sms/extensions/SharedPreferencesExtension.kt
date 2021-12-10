package com.beeper.sms.extensions

import android.content.SharedPreferences
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.TimeMillis.Companion.toMillis
import com.beeper.sms.commands.TimeSeconds
import com.beeper.sms.commands.TimeSeconds.Companion.toSeconds
import java.math.BigDecimal

fun SharedPreferences.putLong(key: String, value: Long) {
    Log.d(TAG, "putLong($key, $value)")
    edit().putLong(key, value).apply()
}

fun SharedPreferences.putTimeMilliseconds(key: String, value: TimeMillis) {
    Log.d(TAG, "putTimeMilliseconds($key, $value)")
    edit().putLong(key, value.toLong()).apply()
}

fun SharedPreferences.getTimeMilliseconds(key: String): TimeMillis =
    getLong(key, 0L).toMillis()

fun SharedPreferences.putTimeSeconds(key: String, value: TimeSeconds) {
    Log.d(TAG, "putTimeSeconds($key, $value)")
    edit().putString(key, value.toString()).apply()
}

fun SharedPreferences.getTimeSeconds(key: String): TimeSeconds? =
    getString(key, null)?.toSeconds()

private const val TAG = "SharedPreferences"