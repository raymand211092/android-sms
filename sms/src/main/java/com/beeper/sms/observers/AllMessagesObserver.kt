package com.beeper.sms.observers

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import com.beeper.sms.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AllMessagesObserver(
    private val context: Context,
) : ContentObserver(getHandler()) {
    private val _changes = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes = _changes.asSharedFlow()

    fun registerObserver() {
        val uri = Telephony.Threads.CONTENT_URI
        Log.d(TAG, "Watching $uri")
        context.contentResolver.registerContentObserver(uri, true, this)
    }

    fun unregisterObserver(){
        context.contentResolver.unregisterContentObserver(this)
    }

    override fun deliverSelfNotifications() = true

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.v(TAG, "onChange $uri")
        _changes.tryEmit(Unit)
    }

    companion object {
        private const val TAG = "AllMessagesObserver"
        fun getHandler() = HandlerThread("AllMessagesObserver").let {
            it.start()
            Handler(it.looper)
        }
    }
}