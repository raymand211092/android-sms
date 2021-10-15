package com.beeper.sms.extensions

import android.database.Cursor
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull

fun Cursor.getString(column: String): String? = getStringOrNull(getColumnIndex(column))

fun Cursor.getIntOrNull(column: String): Int? = getIntOrNull(getColumnIndex(column))

fun Cursor.getInt(column: String) = getInt(getColumnIndex(column))

fun Cursor.getLong(column: String) = getLong(getColumnIndex(column))