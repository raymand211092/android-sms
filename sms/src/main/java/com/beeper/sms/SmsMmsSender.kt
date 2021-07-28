package com.beeper.sms

import android.content.Context
import android.os.Parcelable
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import java.io.File

class SmsMmsSender(private val context: Context) {

    fun sendMessage(
        text: String,
        recipients: List<String>,
        thread: Long = 0,
        sentMessageParcelable: Parcelable? = null,
        subject: String? = null,
    ) = Transaction(context, settings).sendNewMessage(
        Message(text, recipients.toTypedArray()).apply {
            this.subject = subject
        },
        thread,
        sentMessageParcelable,
        null
    )

    fun sendMessage(
        recipients: List<String>,
        path: String,
        mimeType: String,
        filename: String,
        thread: Long = 0,
        sentMessageParcelable: Parcelable,
    ) = Transaction(context, settings).sendNewMessage(
        Message("", recipients.toTypedArray()).apply {
            addMedia(File(path).readBytes(), mimeType, filename)
        },
        thread,
        sentMessageParcelable,
        null
    )

    companion object {
        private val settings = Settings().apply {
            deliveryReports = true
            useSystemSending = true
        }
    }
}