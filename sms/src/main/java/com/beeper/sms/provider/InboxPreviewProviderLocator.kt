package com.beeper.sms.provider

import android.content.Context
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.InboxPreviewCache


object InboxPreviewProviderLocator {
    @Volatile private var INSTANCE: InboxPreviewProvider? = null
    // Inbox preview mapping should come from the application itself, not the library
    fun getInstance(context: Context): InboxPreviewProvider =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: InboxPreviewProvider(
                BridgeDatabase.getInstance(context).inboxPreviewCacheDao(),
                ChatThreadProvider(context),
                MessageProvider(context),
            ).also {
                INSTANCE = it
            }
        }
}
