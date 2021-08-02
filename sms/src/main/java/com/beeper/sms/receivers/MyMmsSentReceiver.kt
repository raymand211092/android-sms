package com.beeper.sms.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.beeper.sms.Bridge
import com.beeper.sms.CommandProcessor.Companion.EXTRA_COMMAND_ID
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.MmsProvider
import com.beeper.sms.work.WorkManager
import com.klinker.android.send_message.MmsSentReceiver
import com.klinker.android.send_message.Transaction.SENT_MMS_BUNDLE

class MyMmsSentReceiver : MmsSentReceiver() {
    override fun onMessageStatusUpdated(context: Context, intent: Intent?, resultCode: Int) {
        Log.d(TAG, "intent: ${intent.printExtras()}")
        val uri = intent?.getStringExtra(EXTRA_CONTENT_URI)?.toUri()
        val commandId =
            (intent?.getParcelableExtra(SENT_MMS_BUNDLE) as? Bundle)?.getInt(EXTRA_COMMAND_ID)
        val message = uri?.let { MmsProvider(context).getMessage(it) }
        when {
            uri == null -> Log.e(TAG, "Missing uri")
            commandId == null ->
                WorkManager(context).sendMessage(uri) // did not originate from mautrix-imessage
            message != null ->
                Bridge.INSTANCE.send(
                    Command(
                        "response",
                        SendMessage.Response(message.guid, message.timestamp),
                        commandId,
                    )
                )
            else -> Log.e(TAG, "Unable to respond (command=$commandId uri=$uri)")
        }
    }

    companion object {
        private const val TAG = "MyMmsSentReceiver"
    }
}