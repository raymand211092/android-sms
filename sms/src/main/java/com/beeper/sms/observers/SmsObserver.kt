package com.beeper.sms.observers

import android.content.Context
import android.content.UriMatcher
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import com.beeper.sms.Log
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.work.WorkManager

class SmsObserver(
    private val context: Context,
    private val messageProvider: MessageProvider = MessageProvider(context),
) : ContentObserver(getHandler()) {
    private val workManager = WorkManager(context)

    fun registerObserver() {
        URIS.forEach {
            Log.d(TAG, "Watching $it")
            context.contentResolver.registerContentObserver(it, true, this)
        }
    }

    override fun deliverSelfNotifications() = true

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        when(uriMatcher.match(uri)) {
            URI_SYNC -> {
                val message = uri?.lastPathSegment?.toIntOrNull()?.let {
                    messageProvider.getMessageInfo(uri)
                }
                if (message?.sent_from_matrix == true) {
                    Log.v(TAG, "Ignored $uri (sent from matrix)")
                } else {
                    Log.d(TAG, "schedule new message check: ${message ?: uri}")
                    workManager.longRunningSyncDb()
                }
            }
            URI_IGNORE -> Log.v(TAG, "Ignored $uri")
            else -> Log.d(TAG, "Unhandled $uri")
        }
    }

    companion object {
        private const val TAG = "SmsObserver"
        private val URIS = listOf(Sms.CONTENT_URI, Mms.CONTENT_URI)
        private val AUTH_SMS = Sms.CONTENT_URI.authority
        private val AUTH_MMS = Mms.CONTENT_URI.authority
        private const val URI_IGNORE = 1
        private const val URI_SYNC = 2
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            listOf(AUTH_SMS, AUTH_MMS).forEach {
                addURI(it, "/", URI_SYNC)
                addURI(it, "/#", URI_SYNC)
                addURI(it, "/inbox/#", URI_SYNC)
                addURI(it, "/sent/#", URI_SYNC)
            }
            addURI(AUTH_SMS, "/raw", URI_IGNORE)
            addURI(AUTH_SMS, "/raw/#", URI_IGNORE)
            addURI(AUTH_MMS, "/part/#", URI_IGNORE)
        }

        fun getHandler() = HandlerThread("SMS observer").let {
            it.start()
            Handler(it.looper)
        }
    }
}