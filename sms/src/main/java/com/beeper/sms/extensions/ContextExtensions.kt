package com.beeper.sms.extensions

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
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

fun Context.getThread(group: GroupMessaging) = getThread(group.recipientList)

fun Context.getThread(recipients: List<String>) = try {
    Threads.getOrCreateThreadId(this, recipients.toSet())
} catch (ignored: Exception) {
    0L
}

val Context.isDefaultSmsApp: Boolean
    get() = packageName == Telephony.Sms.getDefaultSmsPackage(this)

fun Context.requestSmsRoleIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getSystemService(RoleManager::class.java)
            .createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
    }
