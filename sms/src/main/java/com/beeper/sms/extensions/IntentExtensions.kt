package com.beeper.sms.extensions

import android.app.RemoteInput
import android.content.Intent
import android.telephony.PhoneNumberUtils

fun Intent.getText(extra: String): String? =
    getStringExtra(extra)
        ?: RemoteInput.getResultsFromIntent(this)?.getCharSequence(extra)?.toString()

val Intent.recipients: List<String>?
    get() =
        data?.takeIf { it.isSmsMmsUri }
            ?.schemeSpecificPart?.split("\\?")?.get(0)
            ?.let { PhoneNumberUtils.replaceUnicodeDigits(it).split(";") }
            ?.takeIf { it.isNotEmpty() }

fun Intent?.printExtras() =
    this?.extras?.keySet()?.map { it to extras?.get(it) }?.toString() ?: "null"
