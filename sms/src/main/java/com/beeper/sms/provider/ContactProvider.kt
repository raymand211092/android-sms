package com.beeper.sms.provider

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.PhoneLookup
import android.util.Base64
import com.beeper.sms.extensions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



class ContactProvider constructor(private val context: Context) {
    private val cr = context.contentResolver

    suspend fun fetchContacts(
        limit: Int = -1,
        offset: Int = -1
    ): List<ExtendedContactRow> {
        return withContext(Dispatchers.IO) {
            val contactProjection = arrayOf(
                Contacts._ID,
                Contacts.DISPLAY_NAME_PRIMARY,
                StructuredName.HAS_PHONE_NUMBER,
                StructuredName.STARRED
            )
            val result: MutableList<ExtendedContactRow> = mutableListOf()
            cr.query(
                Contacts.CONTENT_URI,
                contactProjection,
                null,
                null,
                if (limit > 0 && offset > -1) "${Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit OFFSET $offset"
                else Contacts.DISPLAY_NAME_PRIMARY + " ASC"
            )?.use {
                if (it.moveToFirst()) {
                    do {
                        val contactId = it.getLong(Contacts._ID)
                        val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
                        val hasPhoneNumber = it.getInt(StructuredName.HAS_PHONE_NUMBER)
                        val favorite = it.getInt(StructuredName.STARRED) > 0
                        val phoneNumbers: List<String> = if (hasPhoneNumber > 0) {
                            fetchPhoneNumbers(contactId)
                        } else mutableListOf()
                        result.add(ExtendedContactRow(
                            id = contactId,
                            name = it.getString(StructuredName.DISPLAY_NAME_PRIMARY)
                                ?: phoneNumbers.firstOrNull() ?: contactId.toString(),
                            starred = favorite,
                            avatarUri = Uri.withAppendedPath(
                                contactUri,
                                Contacts.Photo.CONTENT_DIRECTORY
                            ),
                            phoneNumbers = phoneNumbers,
                            emails = listOf()
                        ))
                    } while (it.moveToNext())
                }
            }
            result.filter {
                it.phoneNumbers.isNotEmpty()
            }.toList()
        }
    }


    @SuppressLint("Range")
    private suspend fun fetchPhoneNumbers(contactId: Long): List<String> {
        return withContext(Dispatchers.IO) {
            val result: MutableList<String> = mutableListOf()
            cr.query(
                Phone.CONTENT_URI,
                null,
                "${Phone.CONTACT_ID} =?",
                arrayOf(contactId.toString()),
                null
            )?.use {
                if (it.moveToFirst()) {
                    do {
                        result.add(it.getString(it.getColumnIndex(Phone.NUMBER)))
                    } while (it.moveToNext())
                }
            }
            result
        }
    }

    fun getContacts(phones: List<String>) = phones.map { getContact(it) }

    @SuppressLint("InlinedApi")
    fun getContact(phone: String): ContactRow = when {
        !context.hasPermission(Manifest.permission.READ_CONTACTS) -> phone.defaultResponse
        Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> phone.defaultResponse
        else ->
            cr
                .firstOrNull(phone.lookupUri) {
                    getContact(it.getLong(PhoneLookup.CONTACT_ID))?.apply {
                        phoneNumber = phone
                    }
                }
                ?: cr
                    .firstOrNull(
                        Phone.CONTENT_URI,
                        // hack to match short codes
                        where = "REPLACE(${Phone.NUMBER}, '-', '') == \"$phone\""
                    ) {
                        getContact(it.getLong(Phone.CONTACT_ID))?.apply {
                            phoneNumber = phone
                        }
                    }
                ?: phone.defaultResponse
    }

    private fun getContact(id: Long): ContactRow? =
        cr.firstOrNull(
            ContactsContract.Data.CONTENT_URI,
            "${ContactsContract.Data.CONTACT_ID} = $id AND ${ContactsContract.Data.MIMETYPE} = '${StructuredName.CONTENT_ITEM_TYPE}'"
        ) { contact ->
            val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id)
            val givenName = contact.getString(StructuredName.GIVEN_NAME)
            val familyName = contact.getString(StructuredName.FAMILY_NAME)
            val avatarUri = Uri.withAppendedPath(contactUri, Contacts.Photo.CONTENT_DIRECTORY)
            ContactRow(
                first_name = givenName,
                last_name = familyName,
                avatar = Contacts.openContactPhotoInputStream(cr, contactUri, true)
                    ?.let { Base64.encodeToString(it.readBytes(), Base64.DEFAULT) },
                avatarUri = avatarUri,
                nickname = contact.getString(StructuredName.DISPLAY_NAME)
                    ?: "$givenName $familyName".trim().takeIf { it.isNotBlank() }
            )
        }

    companion object {
        private val String.defaultResponse: ContactRow
            get() = ContactRow(nickname = this, phoneNumber = this)

        private val String.lookupUri: Uri
            get() = Uri.withAppendedPath(
                PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode("tel:${this}")
            )
    }
}
