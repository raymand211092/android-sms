package com.beeper.sms.receivers

import android.app.Activity.RESULT_OK
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager.*
import androidx.core.net.toUri
import com.beeper.sms.Bridge
import com.beeper.sms.Log
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.incoming.SendMedia
import com.beeper.sms.commands.outgoing.Error
import com.beeper.sms.extensions.printExtras
import com.beeper.sms.provider.MmsProvider
import com.klinker.android.send_message.Transaction.COMMAND_ID
import com.klinker.android.send_message.Transaction.SENT_MMS_BUNDLE
import java.lang.System.currentTimeMillis
import java.util.*

class MyMmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "intent: ${intent.printExtras()}")
        val uri = intent?.getStringExtra(EXTRA_URI)?.toUri()
        val commandId =
            (intent?.getParcelableExtra(SENT_MMS_BUNDLE) as? Bundle)?.getInt(COMMAND_ID)
        val message = uri?.let { MmsProvider(context).getMessage(it) }
        val (guid, timestamp) = when {
            commandId == null -> {
                Log.e(TAG, "missing command")
                return
            }
            resultCode != RESULT_OK -> {
                Bridge.INSTANCE.send(
                    commandId,
                    Error("network_error", errorToString(resultCode, intent))
                )
                return
            }
            message != null -> Pair(message.guid, message.timestamp)
            else -> Pair(UUID.randomUUID().toString(), currentTimeMillis())
        }
        Bridge.INSTANCE.send(
            Command("response", SendMedia.Response(guid, timestamp), commandId)
        )
    }

    companion object {
        private const val TAG = "MyMmsSentReceiver"
        private const val EXTRA_URI = "uri"

        private val Intent.httpError: Int?
            get() = if (hasExtra(EXTRA_MMS_HTTP_STATUS)) {
                getIntExtra(EXTRA_MMS_HTTP_STATUS, 0)
            } else {
                null
            }

        private fun errorToString(rc: Int, intent: Intent?): String {
            return when (rc) {
                MMS_ERROR_UNSPECIFIED -> "MMS_ERROR_UNSPECIFIED"
                MMS_ERROR_INVALID_APN -> "MMS_ERROR_INVALID_APN"
                MMS_ERROR_UNABLE_CONNECT_MMS -> "MMS_ERROR_UNABLE_CONNECT_MMS"
                MMS_ERROR_HTTP_FAILURE -> "MMS_ERROR_HTTP_FAILURE (${intent?.httpError})"
                MMS_ERROR_IO_ERROR -> "MMS_ERROR_IO_ERROR"
                MMS_ERROR_RETRY -> "MMS_ERROR_RETRY"
                MMS_ERROR_CONFIGURATION_ERROR -> "MMS_ERROR_CONFIGURATION_ERROR"
                MMS_ERROR_NO_DATA_NETWORK -> "MMS_ERROR_NO_DATA_NETWORK"
                else -> "Unknown error ($rc)"
            }
        }
    }
}