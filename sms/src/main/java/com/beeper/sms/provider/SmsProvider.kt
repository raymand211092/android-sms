package com.beeper.sms.provider

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.provider.Telephony.Sms.*
import android.telephony.SmsMessage
import android.text.TextUtils
import android.util.Log
import com.beeper.sms.commands.outgoing.Message
import com.beeper.sms.extensions.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


class SmsProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    private val cr = context.contentResolver

    fun getMessage(uri: Uri) = getMessages(where = "_id = ${uri.lastPathSegment}").firstOrNull()

    private fun getMessages(where: String? = null): List<Message> =
        cr.map(CONTENT_URI, where) {
            val address = it.getString(ADDRESS)
            val isFromMe = when (it.getInt(TYPE)) {
                MESSAGE_TYPE_OUTBOX, MESSAGE_TYPE_SENT -> true
                else -> false
            }
            Message(
                guid = it.getInt(_ID).toString(),
                timestamp = it.getLong(DATE) / 1000,
                subject = it.getString(SUBJECT) ?: "",
                text = it.getString(BODY) ?: "",
                chat_guid = "SMS;-;$address",
                sender_guid = if (isFromMe) null else "SMS;-;$address",
                is_from_me = isFromMe,
            )
        }

    /**
     * Store a received SMS into Telephony provider
     *
     * @param intent The intent containing the received SMS
     * @return The URI of written message
     */
    fun writeInboxMessage(intent: Intent): Uri? {
        val messages = Intents.getMessagesFromIntent(intent)
        if (messages == null || messages.isEmpty()) {
            Log.e(TAG, "Failed to parse SMS pdu")
            return null
        }
        // Sometimes, SmsMessage is null if it can’t be parsed correctly.
        for (sms in messages) {
            if (sms == null) {
                Log.e(TAG, "Can’t write null SmsMessage")
                return null
            }
        }
        val values = parseSmsMessage(messages)
        val identity = Binder.clearCallingIdentity()
        try {
            return cr.insert(Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist inbox message", e)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
        return null
    }

    /**
     * Convert SmsMessage[] into SMS database schema columns
     *
     * @param msgs The SmsMessage array of the received SMS
     * @return ContentValues representing the columns of parsed SMS
     */
    private fun parseSmsMessage(msgs: Array<SmsMessage>): ContentValues {
        val sms = msgs[0]
        val values = ContentValues()
        values.put(Inbox.ADDRESS, sms.displayOriginatingAddress)
        values.put(Inbox.BODY, buildMessageBodyFromPdus(msgs))
        values.put(Inbox.DATE_SENT, sms.timestampMillis)
        values.put(Inbox.DATE, System.currentTimeMillis())
        values.put(Inbox.PROTOCOL, sms.protocolIdentifier)
        values.put(Inbox.SEEN, 0)
        values.put(Inbox.READ, 0)
        val subject = sms.pseudoSubject
        if (!TextUtils.isEmpty(subject)) {
            values.put(Inbox.SUBJECT, subject)
        }
        values.put(Inbox.REPLY_PATH_PRESENT, if (sms.isReplyPathPresent) 1 else 0)
        values.put(Inbox.SERVICE_CENTER, sms.serviceCenterAddress)
        return values
    }

    /**
     * Build up the SMS message body from the SmsMessage array of received SMS
     *
     * @param msgs The SmsMessage array of the received SMS
     * @return The text message body
     */
    private fun buildMessageBodyFromPdus(msgs: Array<SmsMessage>): String {
        return if (msgs.size == 1) {
            // There is only one part, so grab the body directly.
            replaceFormFeeds(msgs[0].displayMessageBody)
        } else {
            // Build up the body from the parts.
            val body = StringBuilder()
            for (msg in msgs) {
                // getDisplayMessageBody() can NPE if mWrappedMessage inside is null.
                body.append(msg.displayMessageBody)
            }
            replaceFormFeeds(body.toString())
        }
    }

    // Some providers send formfeeds in their messages. Convert those formfeeds to newlines.
    private fun replaceFormFeeds(s: String?) = s?.replace(FORM_FEED, '\n') ?: ""

    companion object {
        private const val TAG = "SmsProvider"
        private const val FORM_FEED = '\u000C' // '\f'
    }
}