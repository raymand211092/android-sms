package com.beeper.sms.repository

import com.beeper.sms.Log
import com.beeper.sms.StartStopBridge
import com.beeper.sms.commands.incoming.SendMessage
import com.beeper.sms.commands.internal.BridgeSendResponse
import com.beeper.sms.database.models.*
import com.beeper.sms.observers.ContactObserver
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.ContactRow
import com.beeper.sms.provider.EntityChange
import com.beeper.sms.provider.ExtendedContactRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


class ContactRepository(
    private val contactCacheDao: ContactCacheDao,
    private val contactProvider: ContactProvider,
    private val contactObserver: ContactObserver,
    private val pendingContactUpdateDao: PendingContactUpdateDao,
    dispatcher : CoroutineDispatcher = Dispatchers.IO
) {
    private val coroutineScope = CoroutineScope(dispatcher)
    private val mutContactListChanged = MutableSharedFlow<Unit>()
    val contactListChanged = mutContactListChanged.asSharedFlow()

    init{
        contactObserver.changes.onEach {
            observedContacts ->
            Log.d(TAG, "Contact list changed, refreshing cache")
            observedContacts.onEach {
                getContact(it)
            }
        }.launchIn(coroutineScope)
    }


    suspend fun fetchContacts(
        limit: Int = 0,
    ): List<ExtendedContactRow> {
        return contactProvider.fetchContacts(limit)
    }

    fun getContacts(phones: List<String>) = contactProvider.getContacts(phones)

    fun getContact(phone: String): ContactRow = contactProvider.getContact(phone)

    suspend fun getContact(canonicalAddressId: Long): ContactCache? {
        val cacheAddress = contactCacheDao.getContact(canonicalAddressId)
        if(cacheAddress != null){
            Log.d(TAG,"InboxPreview ContactRepository getContact: returning cached cacheAddress:$canonicalAddressId")
            coroutineScope.launch {
                Log.w(TAG,"InboxPreview CacheUpdate ContactRepository checking if we have changes on the cachedAddress")
                val address = contactProvider.getContactAddressFromRecipientId(canonicalAddressId)
                if(address == null){
                    Log.w(TAG,"InboxPreview CacheUpdate ContactRepository getContact: $canonicalAddressId wasn't found in low level layer")
                    return@launch
                }
                val contactRow = contactProvider.getContact(address)
                val contactCache = ContactCache(
                    canonicalAddressId,
                    contactRow.phoneNumber,
                    contactRow.phoneType,
                    contactRow.first_name,
                    contactRow.middle_name,
                    contactRow.last_name,
                    contactRow.nickname,
                    contactRow.avatarUri?.path,
                )

                if(contactCache != cacheAddress){
                    Log.w(TAG,"InboxPreview CacheUpdate updating contact in cache!!! #${contactCache.canonical_address_id}")
                    coroutineScope.launch {
                        contactCacheDao.insert(contactCache)
                        //notify listener that we have a change on existing items
                        // Trigger repo contact list changed
                        Log.d(TAG, "Emitting contact change event")
                        mutContactListChanged.emit(Unit)
                        // Saving changes to be bridged to mautrix-imessage
                        // -> will be loaded by the bridge in the next sync window
                        pendingContactUpdateDao.insert(
                            PendingContactUpdate(
                                contactCache.canonical_address_id,
                                contactCache.first_name,
                                contactCache.last_name,
                                contactCache.nickname,
                                contactCache.avatarUri,
                                contactCache.phoneNumber
                            )
                        )
                    }
                }
            }
            contactObserver.registerObserver(cacheAddress.canonical_address_id)
            return cacheAddress
        }else{
            Log.d(TAG,"InboxPreview ContactRepository getContact: cache miss -> asking for low level layer")

            val address = contactProvider.getContactAddressFromRecipientId(canonicalAddressId)
            if(address == null){
                Log.e(TAG,"InboxPreview ContactRepository getContact: $canonicalAddressId wasn't found in low level layer")
                return null
            }
            val contactRow = contactProvider.getContact(address)
            
            val contactCache = ContactCache(
                canonicalAddressId,
                contactRow.phoneNumber,
                contactRow.phoneType,
                contactRow.first_name,
                contactRow.middle_name,
                contactRow.last_name,
                contactRow.nickname,
                contactRow.avatarUri?.path,
            )
            Log.d(TAG,"InboxPreview ContactRepository getContact: $canonicalAddressId being inserted in the cache")

            coroutineScope.launch {
                contactCacheDao.insert(contactCache)
            }

            contactObserver.registerObserver(canonicalAddressId)
            return ContactCache(
                canonicalAddressId,
                contactRow.phoneNumber,
                contactRow.phoneType,
                contactRow.first_name,
                contactRow.middle_name,
                contactRow.last_name,
                contactRow.nickname,
                contactRow.avatarUri?.path,
            )
        }
    }

    companion object {
        val TAG = "ContactRepository"
    }
}
