package com.beeper.sms.extensions

import android.net.Uri

private const val SCHEME_SMS = "sms"
private const val SCHEME_SMSTO = "smsto"
private const val SCHEME_MMS = "mms"
private const val SCHEME_MMSTO = "smsto"
private val SMS_MMS_SCHEMES =
    listOf(SCHEME_SMS, SCHEME_MMS, SCHEME_SMSTO, SCHEME_MMSTO).toHashSet()

val Uri.isSmsMmsUri: Boolean
    get() = SMS_MMS_SCHEMES.contains(scheme)
