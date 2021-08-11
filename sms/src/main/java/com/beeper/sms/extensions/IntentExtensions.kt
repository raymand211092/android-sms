package com.beeper.sms.extensions

import android.content.Intent

fun Intent?.printExtras() =
    this?.extras?.keySet()?.map { it to extras?.get(it) }?.toString() ?: "null"
