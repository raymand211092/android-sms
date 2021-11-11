package com.beeper.sms.extensions

import android.content.SharedPreferences
import com.beeper.sms.Log

fun SharedPreferences.putLong(key: String, value: Long) {
    Log.d(TAG, "putLong($key, $value)")
    edit().putLong(key, value).apply()
}

private const val TAG = "SharedPreferences"