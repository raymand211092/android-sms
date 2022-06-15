package com.beeper.sms.observers

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.ContactsContract
import com.beeper.sms.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ThreadObserver(
    private val threadId: Long,
    private val context: Context,
) : ContentObserver(getHandler()) {
    private val _threadChanges = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val threadChanges = _threadChanges.asSharedFlow()

    fun registerObserver() {
        val uri  = Uri.withAppendedPath(
            Uri.parse(BASE_THREAD_PATH),
            threadId.toString()
        )
        Log.d(TAG, "Watching $uri")
        context.contentResolver.registerContentObserver(uri, true, this)
    }

    override fun deliverSelfNotifications() = true

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        _threadChanges.tryEmit(Unit)
        if(uri.toString() == BASE_THREAD_PATH) {
            Log.v(TAG, "onChange $uri")
            _threadChanges.tryEmit(Unit)
        }
    }

    companion object {
        private const val TAG = "ThreadObserver"
        private const val BASE_THREAD_PATH = "content://mms-sms/conversations/"
        fun getHandler() = HandlerThread("ThreadObserver").let {
            it.start()
            Handler(it.looper)
        }
    }
}