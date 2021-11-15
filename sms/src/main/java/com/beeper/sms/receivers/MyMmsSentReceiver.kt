package com.beeper.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.beeper.sms.Bridge
import com.beeper.sms.CommandProcessor.Companion.EXTRA_COMMAND_ID
import com.beeper.sms.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.SendMedia
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.MmsProvider
import com.klinker.android.send_message.Transaction.SENT_MMS_BUNDLE
import java.lang.System.currentTimeMillis
import java.util.*

class MyMmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "intent: ${intent.printExtras()}")
        val uri = intent?.getStringExtra(EXTRA_URI)?.toUri()
        val commandId =
            (intent?.getParcelableExtra(SENT_MMS_BUNDLE) as? Bundle)?.getInt(EXTRA_COMMAND_ID)
        val message = uri?.let { MmsProvider(context).getMessage(it) }
        val failureCause =
            intent?.getIntExtra(EXTRA_LAST_CONNECTION_FAILURE_CAUSE_CODE, 0)
        val handledByCarrierApp =
            intent?.getBooleanExtra(EXTRA_HANDLED_BY_CARRIER_APP, false) ?: false
        val (guid, timestamp) = when {
            commandId == null -> {
                Log.e(TAG, "missing command")
                return
            }
            message != null -> Pair(message.guid, message.timestamp)
            failureCause == 0 && handledByCarrierApp -> {
                Log.w(TAG, "$commandId handled by carrier app")
                Pair(UUID.randomUUID().toString(), currentTimeMillis())
            }
            else -> {
                Log.e(TAG, "message not found (commandId=$commandId, uri=$uri, failureCause=$failureCause, handledByCarrierApp=$handledByCarrierApp)")
                return
            }
        }
        Bridge.INSTANCE.send(
            Command("response", SendMedia.Response(guid, timestamp), commandId)
        )
    }

    companion object {
        private const val TAG = "MyMmsSentReceiver"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_LAST_CONNECTION_FAILURE_CAUSE_CODE =
            "android.telephony.extra.LAST_CONNECTION_FAILURE_CAUSE_CODE"
        private const val EXTRA_HANDLED_BY_CARRIER_APP =
            "android.telephony.extra.HANDLED_BY_CARRIER_APP"
    }
}