package com.beeper.sms.extensions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.provider.Telephony.Threads
import androidx.core.content.ContextCompat
import com.beeper.sms.commands.incoming.GroupMessaging
import java.io.File

fun Context.cacheDir(subdir: String): String =
    File(cacheDir, subdir).apply { mkdirs() }.absolutePath

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.getThread(group: GroupMessaging): Long {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            return Threads.getOrCreateThreadId(this, group.recipientList.toSet())
        } catch (ignored: Exception) {
        }
    }
    return 0L
}

val Context.isDefaultSmsApp: Boolean
    get() = packageName == Telephony.Sms.getDefaultSmsPackage(this)
