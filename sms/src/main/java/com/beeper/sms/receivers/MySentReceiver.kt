package com.beeper.sms.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.beeper.sms.Bridge
import com.beeper.sms.CommandProcessor.Companion.EXTRA_COMMAND_ID
import com.beeper.sms.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.SmsProvider
import com.klinker.android.send_message.SentReceiver
import com.klinker.android.send_message.Transaction.SENT_SMS_BUNDLE

class MySentReceiver : SentReceiver() {
    override fun onMessageStatusUpdated(context: Context, intent: Intent?, resultCode: Int) {
        Log.d(TAG, intent.printExtras())
        val uri = intent?.getStringExtra("uri")?.toUri()
        val commandId =
            (intent?.getParcelableExtra(SENT_SMS_BUNDLE) as? Bundle)?.getInt(EXTRA_COMMAND_ID)
        val message = uri?.let { SmsProvider(context).getMessage(it) }
        when {
            message != null ->
                Bridge.INSTANCE.send(
                    Command(
                        "response",
                        SendMessage.Response(message.guid, message.timestamp),
                        commandId,
                    )
                )
            commandId != null ->
                Bridge.INSTANCE.send(
                    commandId,
                    Error(
                        "missing_sms",
                        "Message not found in Android's SMS database (uri=$uri)"
                    )
                )
            else -> {
                Log.e(TAG, "Unable to respond (command=$commandId uri=$uri)")
            }
        }
    }

    companion object {
        private const val TAG = "MySentReceiver"
    }
}
