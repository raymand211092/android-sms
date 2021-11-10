package com.beeper.sms.extensions

import android.Manifest.permission.*
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.provider.Telephony.Threads
import androidx.core.content.ContextCompat
import com.beeper.sms.commands.incoming.GroupMessaging
import java.io.File

fun Context.getSharedPreferences() = getSharedPreferences("sms_bridge", Context.MODE_PRIVATE)

fun Context.cacheDir(subdir: String): String =
    File(cacheDir, subdir).apply { mkdirs() }.absolutePath

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED

fun Context.getThread(group: GroupMessaging) = try {
    Threads.getOrCreateThreadId(this, group.recipientList.toSet())
} catch (ignored: Exception) {
    null
}

val Context.hasPermissions: Boolean
    get() = SMS_PERMISSIONS.all { hasPermission(it) }

val SMS_PERMISSIONS = listOf(READ_SMS, SEND_SMS, READ_CONTACTS)
