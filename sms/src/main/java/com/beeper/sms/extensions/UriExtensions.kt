package com.beeper.sms.extensions

import android.net.Uri
import android.provider.Telephony

val Uri.isMms: Boolean
    get() = toString().startsWith("${Telephony.Mms.CONTENT_URI}")
