package com.beeper.sms

import android.content.Context
import android.content.UriMatcher
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import com.beeper.sms.work.WorkManager

class SmsObserver(private val context: Context) : ContentObserver(getHandler()) {
    private val workManager = WorkManager(context)

    fun registerObserver() {
        URIS.forEach {
            Log.d(TAG, "${hashCode()} Watching $it")
            context.contentResolver.registerContentObserver(it, true, this)
        }
    }

    override fun deliverSelfNotifications() = true

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        when(uriMatcher.match(uri)) {
            URI_SEND -> {
                Log.v(TAG, "${hashCode()} new message: $uri")
                workManager.sendMessage(uri!!)
            }
            URI_BACKFILL -> {
                Log.v(TAG, "${hashCode()} schedule check for new outgoing")
                workManager.backfillSentMMS()
            }
            URI_IGNORE -> Log.v(TAG, "${hashCode()} Ignored $uri")
            else -> Log.d(TAG, "${hashCode()} Unhandled $uri")
        }
    }

    companion object {
        private const val TAG = "SmsObserver"
        private val URIS = listOf(
            Telephony.Sms.CONTENT_URI,
            Telephony.Mms.CONTENT_URI,
            Telephony.MmsSms.CONTENT_URI,
        )
        private const val AUTH_SMS = "sms"
        private const val AUTH_MMS = "mms"
        private const val AUTH_MMS_SMS = "mms-sms"
        private const val URI_IGNORE = 1
        private const val URI_SEND = 2
        private const val URI_BACKFILL = 4
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTH_SMS, "/#", URI_SEND)
            addURI(AUTH_MMS, "/inbox/#", URI_SEND)
            addURI(AUTH_MMS_SMS, "/", URI_BACKFILL)
            addURI(AUTH_MMS_SMS, "/conversations/", URI_IGNORE)
            addURI(AUTH_SMS, "/", URI_IGNORE)
            addURI(AUTH_SMS, "/raw", URI_IGNORE)
            addURI(AUTH_SMS, "/raw/#", URI_IGNORE)
        }

        fun getHandler() = HandlerThread("${hashCode()} handler)").let {
            it.start()
            Handler(it.looper)
        }
    }
}