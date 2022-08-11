package com.beeper.sms.repository

import android.content.Context
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.observers.ContactObserver
import com.beeper.sms.provider.ContactProvider


object RecipientRepositoryLocator {
    @Volatile private var INSTANCE: RecipientRepository? = null
    fun getInstance(context: Context): RecipientRepository =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: RecipientRepository(BridgeDatabase.getInstance(context).recipientCacheDao(),
                ContactProvider(context),
                ContactObserver(context),
                BridgeDatabase.getInstance(context).pendingRecipientUpdateDao()
            ).also {
                INSTANCE = it
            }
        }
}
