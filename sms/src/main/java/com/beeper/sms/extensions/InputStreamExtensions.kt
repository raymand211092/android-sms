package com.beeper.sms.extensions

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

fun InputStream.writeTo(file: File) = use { from ->
    FileOutputStream(file).use { to ->
        val buf = ByteArray(8192)
        while (true) {
            val r = from.read(buf)
            if (r == -1) {
                break
            }
            to.write(buf, 0, r)
        }
    }
}