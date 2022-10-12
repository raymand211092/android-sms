package com.beeper.sms.extensions

import android.Manifest.permission.*
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.provider.Telephony
import android.provider.Telephony.Threads
import androidx.core.content.ContextCompat
import com.beeper.sms.Log
import com.beeper.sms.commands.incoming.GroupMessaging
import java.io.File

fun Context.getSharedPreferences(): SharedPreferences =
    getSharedPreferences("sms_bridge", Context.MODE_PRIVATE)

fun Context.cacheDirPath(subdir: String): String =
    File(cacheDir, subdir).apply { mkdirs() }.absolutePath

val Context.mmsDir: File
    get() {
        val mmsDir = File(cacheDir,"mms")
        if (!mmsDir.exists()) {
            mmsDir.mkdirs()
        }
        return mmsDir
    }

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED

fun Context.getThread(group: GroupMessaging) = getThread(group.recipientList)

fun Context.getThread(recipients: List<String>) = try {
    Threads.getOrCreateThreadId(this, recipients.toSet())
} catch (ignored: Exception) {
    0L
}

val Context.isDefaultSmsApp: Boolean
    get()  {
        val defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(this)
        Log.d("isDefaultSmsApp","PackageName: $packageName," +
                " DefaultSMSPackageName: $defaultSmsPackageName")
        return packageName == defaultSmsPackageName
    }


val Context.hasPermissions: Boolean
    get() = SMS_PERMISSIONS.all { hasPermission(it) }

val SMS_PERMISSIONS = listOf(READ_SMS, SEND_SMS, READ_CONTACTS)
val DUAL_SIM_PERMISSION = listOf(READ_PHONE_STATE)

