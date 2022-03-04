package com.beeper.sms

import android.Manifest.permission.READ_PHONE_STATE
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import com.beeper.sms.extensions.hasPermission
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.receivers.MyDeliveredReceiver
import com.beeper.sms.receivers.MyMmsSentReceiver
import com.beeper.sms.receivers.MySentReceiver
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction

class SmsMmsSender(
    private val context: Context,
    private val messageProvider: MessageProvider = MessageProvider(context),
) {
    fun sendMessage(
        text: String,
        recipients: List<String>,
        thread: Long = 0,
        sentMessageParcelable: Parcelable? = null,
        subject: String? = null,
    ) {
        val subscriptionId = getSubscriptionId(thread)
        val transaction = newTransaction(subscriptionId)
        val message = Message(text, recipients.toTypedArray()).apply {
            this.subject = subject
            if (transaction.checkMMS(this)) {
                setupMms()
            }
        }
        transaction.sendNewMessage(message, thread, sentMessageParcelable, null)
    }

    fun sendMessage(
        text: String,
        recipients: List<String>,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
        thread: Long = 0,
        sentMessageParcelable: Parcelable,
    ) {
        val subscriptionId = getSubscriptionId(thread)
        val transaction = newTransaction(subscriptionId)
        val message = Message(text, recipients.toTypedArray()).apply {
            addMedia(bytes, mimeType, filename)
            setupMms()
        }
        transaction.sendNewMessage(message, thread, sentMessageParcelable, null)
    }

    private fun newTransaction(subscriptionId: Int?) =
        Transaction(context, newSettings(subscriptionId))
            .setExplicitBroadcastForDeliveredSms(Intent(context, MyDeliveredReceiver::class.java))
            .setExplicitBroadcastForSentSms(Intent(context, MySentReceiver::class.java))
            .setExplicitBroadcastForSentMms(Intent(context, MyMmsSentReceiver::class.java))

    private fun getSubscriptionId(thread: Long): Int? =
        if (context.hasPermission(READ_PHONE_STATE)) {
            messageProvider.getRecentMessages(thread, 1).firstOrNull()?.subId
        } else {
            null
        }

    companion object {
        private fun newSettings(subscriptionId: Int? = null) = Settings().apply {
            setSubscriptionId(subscriptionId)
            deliveryReports = true
            useSystemSending = true
        }

        private fun Message.setupMms() = this.apply {
            save = false
        }
    }
}