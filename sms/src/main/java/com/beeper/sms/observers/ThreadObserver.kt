package com.beeper.sms.observers

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import com.beeper.sms.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ThreadObserver(
    private val threadId: Long,
    private val context: Context,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val _threadChanges = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val threadChanges = _threadChanges.asSharedFlow()

    fun registerObserver() {
        URIS.onEach{
            uri ->
            Log.d(TAG, "Watching $uri")
            context.contentResolver.registerContentObserver(uri, true, this)
        }
    }

    override fun deliverSelfNotifications() = true

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.v(TAG, "onChange $uri selfChange: $selfChange")
        _threadChanges.tryEmit(Unit)
    }

    companion object {
        private val URIS = listOf(Telephony.Sms.CONTENT_URI, Telephony.Mms.CONTENT_URI)
        private const val TAG = "ThreadObserver"
    }
}