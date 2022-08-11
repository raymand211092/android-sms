package com.beeper.sms.repository

import android.content.Context
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.observers.ContactObserver
import com.beeper.sms.provider.ContactProvider


object ContactInfoRepositoryLocator {
    @Volatile private var INSTANCE: ContactInfoRepository? = null
    fun getInstance(context: Context): ContactInfoRepository =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: ContactInfoRepository(BridgeDatabase.getInstance(context).contactInfoCacheDao(),
                ContactProvider(context),
                ContactObserver(context),
            ).also {
                INSTANCE = it
            }
        }
}
