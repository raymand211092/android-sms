package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.beeper.sms.Bridge
import com.beeper.sms.commands.Command
import com.beeper.sms.SmsProvider
import com.beeper.sms.commands.incoming.SendMessage
import com.klinker.android.send_message.SentReceiver
import com.klinker.android.send_message.Transaction.SENT_SMS_BUNDLE
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MySentReceiver : BroadcastReceiver() {
    @Inject lateinit var bridge: Bridge
    @Inject lateinit var smsProvider: SmsProvider

    override fun onReceive(context: Context?, intent: Intent?) = object : SentReceiver() {
        override fun onMessageStatusUpdated(context: Context?, intent: Intent?, resultCode: Int) {
            val command = intent?.getParcelableExtra(SENT_SMS_BUNDLE) as? Command ?: return
            val message =
                intent
                    .getStringExtra("message_uri")
                    ?.toUri()
                    ?.let { smsProvider.getMessage(it) }
                    ?: return
            bridge.send(
                Command(
                    "response",
                    SendMessage.Response(message.guid, message.timestamp),
                    command.id,
                )
            )
        }
    }.onReceive(context, intent)
}
