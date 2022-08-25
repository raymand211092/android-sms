package com.beeper.sms.repository

import com.beeper.sms.Log
import com.beeper.sms.database.models.*
import com.beeper.sms.observers.ContactObserver
import com.beeper.sms.provider.ContactProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


class ContactInfoRepository(
    private val contactInfoCacheDao: ContactInfoCacheDao,
    private val contactProvider: ContactProvider,
    private val contactObserver: ContactObserver,
    dispatcher : CoroutineDispatcher = Dispatchers.IO
) {
    private val coroutineScope = CoroutineScope(dispatcher)
    private val _contactInfoListChanged = MutableSharedFlow<Unit>(0,500, BufferOverflow.DROP_OLDEST)
    val contactInfoListChanged = _contactInfoListChanged.asSharedFlow()

    init{
        contactObserver.changes.onEach {
            observedContacts ->
            Log.d(TAG, "Contact list changed, refreshing cache")
            /*observedContacts.onEach {
                getContact(it)
            }*/
        }.launchIn(coroutineScope)
    }

    suspend fun fetchStarredContactIds() : List<Long>  = contactProvider.fetchAllContactIds(
        fetchOnlyStarred = true
    )

    suspend fun fetchAllContactIds() : List<Long>  = contactProvider.fetchAllContactIds()

    suspend fun fetchContact(contactId: Long) : ContactInfoCache? {
        Log.d(TAG,"ContactInfo Fetch Contact: $contactId")
            val cachedContactInfo = contactInfoCacheDao.getContact(contactId)
            if(cachedContactInfo != null){
                coroutineScope.launch {
                    val contactInfo = contactProvider.getContactInfo(contactId)
                    if(contactInfo == null){
                        Log.w(TAG,"ContactInfo wasn't found to update the cache")
                        return@launch
                    }
                    if(contactInfo != cachedContactInfo){
                        contactInfoCacheDao.insert(contactInfo)
                        _contactInfoListChanged.tryEmit(Unit)
                    }
                }
                return cachedContactInfo
            }else{
                val contactInfo = contactProvider.getContactInfo(contactId)
                if(contactInfo == null){
                    Log.e(TAG,"ContactInfo wasn't found to deliver contact")
                }else {
                    coroutineScope.launch {
                        contactInfoCacheDao.insert(contactInfo)
                        _contactInfoListChanged.tryEmit(Unit)
                    }
                    return contactInfo
                }
            }
        return null
    }


    suspend fun fetchAllContacts() : List<ContactInfoCache> {
        val contacts = mutableListOf<ContactInfoCache>()
        val ids = contactProvider.fetchAllContactIds()
        Log.d(TAG,"ContactInfo All ids were loaded")

        ids.onEach {
            contactId ->
            val cachedContactInfo = contactInfoCacheDao.getContact(contactId)
            if(cachedContactInfo != null){
                contacts.add(cachedContactInfo)
                coroutineScope.launch {
                    val contactInfo = contactProvider.getContactInfo(contactId)
                    if(contactInfo == null){
                        Log.w(TAG,"ContactInfo wasn't found to update the cache")
                        return@launch
                    }
                    if(contactInfo != cachedContactInfo){
                        contactInfoCacheDao.insert(contactInfo)
                        _contactInfoListChanged.tryEmit(Unit)
                    }
                }
            }else{
                val contactInfo = contactProvider.getContactInfo(contactId)
                if(contactInfo == null){
                    Log.e(TAG,"ContactInfo wasn't found to deliver contact")
                }else {
                    coroutineScope.launch {
                        contactInfoCacheDao.insert(contactInfo)
                        _contactInfoListChanged.tryEmit(Unit)
                    }
                    contacts.add(contactInfo)
                }
            }
        }
        return contacts
    }

    companion object {
        val TAG = "ContactInfoRepository"
    }
}
