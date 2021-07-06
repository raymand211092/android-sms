package com.beeper.sms.extensions

import android.content.ContentResolver
import android.content.ContentValues.TAG
import android.database.Cursor
import android.database.DatabaseUtils
import android.net.Uri
import android.util.Log

fun <T> ContentResolver.map(
    uri: Uri,
    where: String? = null,
    projection: Array<String>? = null,
    order: String? = null,
    block: (Cursor) -> T?
): List<T> {
    val result = ArrayList<T>()
    query(uri, projection, where, null, order)?.use {
        Log.d(TAG, "$uri where=$where: ${DatabaseUtils.dumpCursorToString(it)}")
        while (it.moveToNext()) {
            block(it)?.let { t -> result.add(t) }
        }
    }
    return result
}

fun <T> ContentResolver.firstOrNull(
    uri: Uri,
    where: String? = null,
    projection: Array<String>? = null,
    order: String? = null,
    block: (Cursor) -> T?
): T? = map(uri, where, projection, order, block).firstOrNull()
