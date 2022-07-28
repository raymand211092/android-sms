package com.beeper.sms.repository

import android.content.Context
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.observers.ContactObserver
import com.beeper.sms.provider.ContactProvider


object ContactRepositoryLocator {
    @Volatile private var INSTANCE: ContactRepository? = null
    fun getInstance(context: Context): ContactRepository =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: ContactRepository(BridgeDatabase.getInstance(context).contactCacheDao(),
                ContactProvider(context),
                ContactObserver(context),
                BridgeDatabase.getInstance(context).pendingContactUpdateDao()
            ).also {
                INSTANCE = it
            }
        }
}
