package com.beeper.sms.repository

import com.beeper.sms.Log
import com.beeper.sms.commands.incoming.GroupMessaging.Companion.removeSMSGuidPrefix
import com.beeper.sms.database.models.*
import com.beeper.sms.observers.ContactObserver
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.ContactRow
import com.beeper.sms.provider.ContactInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


class RecipientRepository(
    private val recipientCacheDao: RecipientCacheDao,
    private val contactProvider: ContactProvider,
    private val contactObserver: ContactObserver,
    private val pendingRecipientUpdateDao: PendingRecipientUpdateDao,
    dispatcher : CoroutineDispatcher = Dispatchers.IO
) {
    private val coroutineScope = CoroutineScope(dispatcher)
    private val mutContactListChanged = MutableSharedFlow<Unit>(0, 500, BufferOverflow.DROP_OLDEST)
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
    ): List<ContactInfo> {
        return contactProvider.fetchContacts(limit)
    }

    fun getContacts(phones: List<String>) = contactProvider.getRecipients(phones)


    fun getContactBySenderGuid(sender_guid: String): ContactRow {
        val phone = sender_guid.removeSMSGuidPrefix()
        return contactProvider.getRecipientInfo(phone).first
    }


    fun getContact(phone: String): ContactRow = contactProvider.getRecipientInfo(phone).first

    suspend fun getContact(recipientId: Long): RecipientCache? {
        val loadedRecipient = recipientCacheDao.getContact(recipientId)
        if(loadedRecipient != null){
            Log.v(TAG,"InboxPreview ContactRepository getContact: returning cached cacheAddress:$recipientId")
            coroutineScope.launch {
                Log.v(TAG,"InboxPreview CacheUpdate ContactRepository checking if we have changes on the cachedAddress")
                val address = contactProvider.getAddressFromRecipientId(recipientId)
                if(address == null){
                    Log.w(TAG,"InboxPreview CacheUpdate ContactRepository getContact: $recipientId wasn't found in low level layer")
                    return@launch
                }
                val (contactRow, contactId) = contactProvider.getRecipientInfo(address)
                val recipientCache = RecipientCache(
                    recipientId,
                    contactId,
                    contactRow.phoneNumber,
                    contactRow.first_name,
                    contactRow.middle_name,
                    contactRow.last_name,
                    contactRow.nickname,
                )

                if(recipientCache != loadedRecipient){
                    Log.d(TAG,"InboxPreview CacheUpdate updating contact in cache!!! #${recipientCache.recipient_id}")
                    coroutineScope.launch {
                        recipientCacheDao.insert(recipientCache)
                        //notify listener that we have a change on existing items
                        // Trigger repo contact list changed
                        Log.d(TAG, "Emitting contact change event")
                        mutContactListChanged.tryEmit(Unit)
                        // Saving changes to be bridged to mautrix-imessage
                        // -> will be loaded by the bridge in the next sync window
                        pendingRecipientUpdateDao.insert(
                            PendingRecipientUpdate(
                                recipientCache.recipient_id,
                                contactId,
                                recipientCache.phone,
                                recipientCache.first_name,
                                recipientCache.middle_name,
                                recipientCache.last_name,
                                recipientCache.nickname,
                            )
                        )
                    }
                }
            }
            contactObserver.registerObserver(loadedRecipient.recipient_id)
            return loadedRecipient
        }else{
            Log.d(TAG,"InboxPreview ContactRepository getContact: cache miss -> asking for low level layer")

            val address = contactProvider.getAddressFromRecipientId(recipientId)
            if(address == null){
                Log.e(TAG,"InboxPreview ContactRepository getContact: $recipientId wasn't found in low level layer")
                return null
            }
            val (contactRow, contactId) = contactProvider.getRecipientInfo(address)
            
            val contactCache = RecipientCache(
                recipientId,
                contactId,
                contactRow.phoneNumber,
                contactRow.first_name,
                contactRow.middle_name,
                contactRow.last_name,
                contactRow.nickname,
            )
            Log.d(TAG,"InboxPreview ContactRepository getContact: $recipientId being inserted in the cache")

            coroutineScope.launch {
                recipientCacheDao.insert(contactCache)
            }

            contactObserver.registerObserver(recipientId)
            return RecipientCache(
                recipientId,
                contactId,
                contactRow.phoneNumber,
                contactRow.first_name,
                contactRow.middle_name,
                contactRow.last_name,
                contactRow.nickname,
            )
        }
    }

    companion object {
        val TAG = "RecipientRepository"
    }
}
