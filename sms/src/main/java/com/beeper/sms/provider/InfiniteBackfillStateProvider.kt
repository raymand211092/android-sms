package com.beeper.sms.provider

import android.content.Context
import android.provider.Telephony.*
import android.telephony.PhoneNumberUtils
import android.util.Patterns
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import com.beeper.sms.database.BridgeDatabase
import com.beeper.sms.database.models.InfiniteBackfillChatEntry
import com.beeper.sms.database.models.SmsThreadMatrixRoomRelation
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getString
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder
import java.util.*

class InfiniteBackfillStateProvider constructor(
    context: Context,
) {
    private val infiniteBackfillChatEntryDao =
        BridgeDatabase.getInstance(context).infiniteBackfillChatEntryDao()

    fun getEntries() : Flow<List<InfiniteBackfillChatEntry>> {
        return infiniteBackfillChatEntryDao.getStream()
    }

}