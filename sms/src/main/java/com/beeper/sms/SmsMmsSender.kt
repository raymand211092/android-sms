package com.beeper.sms

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import com.beeper.sms.receivers.MyDeliveredReceiver
import com.beeper.sms.receivers.MyMmsSentReceiver
import com.beeper.sms.receivers.MySentReceiver
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction

class SmsMmsSender(private val context: Context) {

    fun sendMessage(
        text: String,
        recipients: List<String>,
        thread: Long = 0,
        sentMessageParcelable: Parcelable? = null,
        subject: String? = null,
    ) {
        val transaction = newTransaction()
        val message = Message(text, recipients.toTypedArray()).apply {
            this.subject = subject
            if (transaction.checkMMS(this)) {
                setupMms()
            }
        }
        transaction.sendNewMessage(message, thread, sentMessageParcelable, null)
    }

    fun sendMessage(
        recipients: List<String>,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
        thread: Long = 0,
        sentMessageParcelable: Parcelable,
    ) = newTransaction().sendNewMessage(
        Message("", recipients.toTypedArray()).apply {
            addMedia(bytes, mimeType, filename)
            setupMms()
        },
        thread,
        sentMessageParcelable,
        null
    )

    private fun newTransaction() =
        Transaction(context, settings)
            .setExplicitBroadcastForDeliveredSms(Intent(context, MyDeliveredReceiver::class.java))
            .setExplicitBroadcastForSentSms(Intent(context, MySentReceiver::class.java))
            .setExplicitBroadcastForSentMms(Intent(context, MyMmsSentReceiver::class.java))

    companion object {
        private val settings = Settings().apply {
            deliveryReports = true
            useSystemSending = true
        }

        private fun Message.setupMms() = this.apply {
            save = false
        }
    }
}