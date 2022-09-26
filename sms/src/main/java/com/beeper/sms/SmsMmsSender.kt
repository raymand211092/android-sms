package com.beeper.sms

import android.Manifest.permission.READ_PHONE_STATE
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.webkit.MimeTypeMap
import com.beeper.sms.extensions.hasPermission
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.receivers.SmsDelivered
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import java.util.*

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
    ): List<Uri?> {
        val subscriptionId = getSubscriptionId(thread)
        val transaction = newTransaction(subscriptionId)
        val message = Message(text, recipients.toTypedArray()).apply {
            this.subject = subject
            if (transaction.checkMMS(this)) {
                setupMms()
            }
        }
        return transaction.sendNewMessage(message, thread, sentMessageParcelable, null)
    }

    fun sendMessage(
        text: String,
        recipients: List<String>,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
        thread: Long = 0,
        sentMessageParcelable: Parcelable,
    ): List<Uri?> {
        val subscriptionId = getSubscriptionId(thread)
        val transaction = newTransaction(subscriptionId)
        val message = Message(text, recipients.toTypedArray()).apply {
            addMedia(bytes, mimeType, filename)
            setupMms()
        }
        return transaction.sendNewMessage(message, thread, sentMessageParcelable, null)
    }

    fun sendMessage(
        text: String,
        recipients: List<String>,
        fileURI: Uri,
        thread: Long = 0,
        sentMessageParcelable: Parcelable,
    ) : List<Uri?> {
        val filename = fileURI.lastPathSegment ?: "File"
        val mimeType = fileURI.getMimeType(context)
        val bytes = context.contentResolver.openInputStream(fileURI)?.readBytes()
            ?: ByteArray(0)
        Log.d("SMSSendMedia", "$filename $mimeType ${bytes.size}")

        return if(mimeType != null && bytes.size <= MAX_FILE_SIZE && bytes.size >= 0) {
            sendMessage(text, recipients, bytes, mimeType, filename, thread, sentMessageParcelable)
        }else{
            Log.e("SMSSendMedia", "conditions to send media weren't met")
            emptyList()
        }
    }

    private fun newTransaction(subscriptionId: Int?): Transaction {
        val smsSentIntent = Intent("com.beeper.sms.SMS_SENT")
        smsSentIntent.setPackage(context.packageName)

        val mmsSentIntent = Intent("com.beeper.sms.MMS_SENT")
        mmsSentIntent.setPackage(context.packageName)

        return Transaction(context, newSettings(subscriptionId))
            .setExplicitBroadcastForDeliveredSms(Intent(context, SmsDelivered::class.java))
            .setExplicitBroadcastForSentSms(smsSentIntent)
            .setExplicitBroadcastForSentMms(mmsSentIntent)
    }

    private fun getSubscriptionId(thread: Long): Int? =
        if (context.hasPermission(READ_PHONE_STATE)) {
            messageProvider.getRecentMessages(thread, 1).firstOrNull()?.subId
        } else {
            null
        }

    companion object {
        const val MAX_FILE_SIZE = 409_600L
        private fun newSettings(subscriptionId: Int? = null) = Settings().apply {
            setSubscriptionId(subscriptionId)
            deliveryReports = true
            useSystemSending = true
        }

        private fun Message.setupMms() = this.apply {
            save = true
        }
    }
}

fun Uri.getMimeType(context: Context): String? {
    return when (scheme) {
        ContentResolver.SCHEME_CONTENT -> context.contentResolver.getType(this)
        ContentResolver.SCHEME_FILE -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(toString()).lowercase(Locale.getDefault())
        )
        else -> null
    }
}
