package com.beeper.sms.observers

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.ContactsContract
import android.provider.Telephony
import com.beeper.sms.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class ContactObserver(
    private val context: Context,
) : ContentObserver(getHandler()) {
    private val isObserving : AtomicBoolean = AtomicBoolean(false)
    private val observedIds = mutableSetOf<Long>()
    private val _changes = MutableSharedFlow<Set<Long>>(
        replay = 0, extraBufferCapacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes = _changes.asSharedFlow()

    fun registerObserver(contactId: Long) {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            val updated = isObserving.compareAndSet(false, true)

            if(updated){
                Log.d(TAG, "Registering to observe ContactsContract.Contacts.CONTENT_URI")

                context.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    this
                )
            }
            val added = observedIds.add(contactId)
            if(added){
                Log.d(TAG, "Watching $contactId")
            }
    }

    override fun deliverSelfNotifications() = true

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.v(TAG, "onChange $uri")
        _changes.tryEmit(observedIds.toSet())
    }

    companion object {
        private const val TAG = "ContactObserver"
        fun getHandler() = HandlerThread("ContactObserver").let {
            it.start()
            Handler(it.looper)
        }
    }
}