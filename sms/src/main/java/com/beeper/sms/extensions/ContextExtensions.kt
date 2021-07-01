package com.beeper.sms.extensions

import android.content.Context
import java.io.File

fun Context.cacheDir(subdir: String): String =
    File(cacheDir, subdir).apply { mkdirs() }.absolutePath