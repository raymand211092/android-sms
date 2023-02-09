package com.beeper.sms.provider

import android.content.Context


object PendingMMSProviderLocator {
    @Volatile private var INSTANCE: PendingMMSProvider? = null
    fun getInstance(applicationContext: Context): PendingMMSProvider =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: PendingMMSProvider(applicationContext).also {
                INSTANCE = it
            }
        }
}
