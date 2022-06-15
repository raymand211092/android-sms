package com.beeper.sms.observers

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
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
        val uri = Uri.parse(BASE_THREAD_PATH)
        Log.d(TAG, "Watching $uri")
        context.contentResolver.registerContentObserver(uri, true, this)
    }

    override fun deliverSelfNotifications() = true

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if(uri.toString() == BASE_THREAD_PATH) {
            Log.v(TAG, "onChange $uri")
            _changes.tryEmit(Unit)
        }
    }

    companion object {
        private const val TAG = "AllMessagesObserver"
        private const val BASE_THREAD_PATH = "content://mms-sms/conversations/"
        fun getHandler() = HandlerThread("AllMessagesObserver").let {
            it.start()
            Handler(it.looper)
        }
    }
}