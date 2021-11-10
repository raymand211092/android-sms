package com.beeper.sms.provider

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Sms.*
import com.beeper.sms.Log
import com.beeper.sms.commands.TimeMillis
import com.beeper.sms.commands.TimeMillis.Companion.toMillis
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.extensions.*
import com.beeper.sms.provider.ThreadProvider.Companion.chatGuid


class SmsProvider constructor(context: Context) {
    private val packageName = context.applicationInfo.packageName
    private val cr = context.contentResolver

    fun getMessagesAfter(timestamp: TimeMillis): List<Message> {
        val selection = "$DATE > ${timestamp.toLong()}"
        return getSms(where = selection)
            .plus(getSms(uri = Inbox.CONTENT_URI, where = selection))
            .plus(getSms(uri = Sent.CONTENT_URI, where = selection))
            .distinctBy { it.guid }
    }

    fun getMessage(uri: Uri) = getSms(uri).firstOrNull()

    fun getMessage(id: Long) = getSms(where = "_id = $id").firstOrNull()

    private fun getSms(uri: Uri = CONTENT_URI, where: String? = null): List<Message> =
        cr.map(uri, where) {
            val address = it.getString(ADDRESS)
            if (address == null) {
                Log.w(TAG, "Missing address: ${it.dumpCurrentRow()}")
                return@map null
            }
            val isFromMe = when (it.getInt(TYPE)) {
                MESSAGE_TYPE_OUTBOX, MESSAGE_TYPE_SENT -> true
                else -> false
            }
            val chatGuid = address.chatGuid
            val rowId = it.getLong(_ID)
            Message(
                guid = rowId.toString(),
                timestamp = it.getLong(DATE).toMillis().toSeconds(),
                subject = it.getString(SUBJECT) ?: "",
                text = it.getString(BODY) ?: "",
                chat_guid = chatGuid,
                sender_guid = if (isFromMe) null else chatGuid,
                is_from_me = isFromMe,
                sent_from_matrix = it.getString(CREATOR) == packageName,
                is_mms = false,
                thread = it.getLong(THREAD_ID),
                rowId = rowId,
                uri = if (where == null) uri else ContentUris.withAppendedId(uri, rowId),
            )
        }

    companion object {
        private const val TAG = "SmsProvider"
    }
}
