package com.beeper.sms.extensions

import android.content.SharedPreferences
import com.beeper.sms.Log

fun SharedPreferences.putLong(key: String, value: Long) {
    Log.d(TAG, "putLong($key, $value)")
    edit().putLong(key, value).apply()
}

fun SharedPreferences.putBoolean(key: String, value: Boolean) {
    Log.d(TAG, "putBoolean($key, $value)")
    edit().putBoolean(key, value).apply()
}

private const val TAG = "SharedPreferences"
const val PREF_USE_OLD_MMS_GUIDS = "use_old_mms_guids"
const val PREF_FIXED_ROOM_IDS = "fixed_room_ids"