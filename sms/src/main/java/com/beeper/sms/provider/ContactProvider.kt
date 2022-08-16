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
import android.provider.Telephony
import android.util.Base64
import com.beeper.sms.Log
import com.beeper.sms.database.models.ContactInfoCache
import com.beeper.sms.extensions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


class ContactProvider constructor(private val context: Context) {
    private val cr = context.contentResolver

    suspend fun fetchAllContactIds(fetchOnlyStarred : Boolean = false) : List<Long> {
        return withContext(Dispatchers.IO) {
            val contactProjection = arrayOf(
                Contacts._ID,
            )
            val result: MutableList<Long> = mutableListOf()
            val defaultSelection = "${StructuredName.HAS_PHONE_NUMBER} > 0 "
            val fetchOnlyStarredSelection = "AND ${StructuredName.STARRED} > 0"
            val selection = if(fetchOnlyStarred){
                defaultSelection + fetchOnlyStarredSelection
            }else{
                defaultSelection
            }
            cr.query(
                Contacts.CONTENT_URI,
                contactProjection,
                selection,
                null,
                Contacts.DISPLAY_NAME_PRIMARY + " ASC"
            )?.use {
                if (it.moveToFirst()) {
                    do {
                        val contactId = it.getLong(Contacts._ID)
                        result.add(contactId)
                    } while (it.moveToNext())
                }
            }
            result.toList()
        }
    }

    suspend fun getContactInfo(contactId: Long) : ContactInfoCache? {
        return withContext(Dispatchers.IO) {
            val contactProjection = arrayOf(
                Contacts._ID,
                StructuredName.DISPLAY_NAME_PRIMARY,
                StructuredName.STARRED
            )
            cr.query(
                Contacts.CONTENT_URI,
                contactProjection,
                "${Contacts._ID} = ?",
                arrayOf(contactId.toString()),
                Contacts.DISPLAY_NAME_PRIMARY + " ASC"
            )?.use {
                if (it.moveToFirst()) {
                        val favorite = it.getInt(StructuredName.STARRED) > 0
                        val phoneNumbers: List<String> = fetchPhoneNumbers(contactId)
                        return@withContext ContactInfoCache(
                            contact_id = contactId,
                            display_name  = it.getString(StructuredName.DISPLAY_NAME_PRIMARY)
                                ?: phoneNumbers.firstOrNull() ?: contactId.toString(),
                            starred = favorite,
                            phone_numbers = phoneNumbers.joinToString(";"),
                        )
                }
            }
            return@withContext null
        }
    }

    suspend fun fetchContacts(
        limit: Int = -1,
        offset: Int = -1
    ): List<ContactInfo> {
        return withContext(Dispatchers.IO) {
            val contactProjection = arrayOf(
                Contacts._ID,
                Contacts.DISPLAY_NAME_PRIMARY,
                StructuredName.HAS_PHONE_NUMBER,
                StructuredName.STARRED
            )
            val result: MutableList<ContactInfo> = mutableListOf()
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
                        result.add(ContactInfo(
                            contactId = contactId,
                            name = it.getString(StructuredName.DISPLAY_NAME_PRIMARY)
                                ?: phoneNumbers.firstOrNull() ?: contactId.toString(),
                            starred = favorite,
                            avatarUri = Uri.withAppendedPath(
                                contactUri,
                                Contacts.Photo.CONTENT_DIRECTORY
                            ),
                            phoneNumbers = phoneNumbers,
                        ))
                    } while (it.moveToNext())
                }
            }
            result.filter {
                it.phoneNumbers.isNotEmpty()
            }.toList()
        }
    }

    fun getAvatar(contactUri: Uri) : String?{
        return Contacts.openContactPhotoInputStream(cr, contactUri, true)
            ?.let { Base64.encodeToString(it.readBytes(), Base64.DEFAULT) }
    }


    @SuppressLint("Range")
    private suspend fun fetchPhoneNumbers(contactId: Long): List<String> {
        return withContext(Dispatchers.IO) {
            val result: MutableList<String> = mutableListOf()
            val phoneNumberProjection = arrayOf(
                Phone.NUMBER,
            )
            cr.query(
                Phone.CONTENT_URI,
                phoneNumberProjection,
                "${Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use {
                if (it.moveToFirst()) {
                    do {
                        try {
                            val numberColumnIndex = it.getColumnIndex(Phone.NUMBER)
                            Log.d(TAG,"NumberColumnIndex: $numberColumnIndex, " +
                                    "contactId: $contactId")
                            val number = it.getString(numberColumnIndex)
                            Log.d(TAG,"is number null: ${number == null}," +
                                    " is number blank: ${number.isNullOrBlank()}")
                            Log.d(TAG,"Fetched a phone number for contactId: $contactId")
                            result.add(number)
                        } catch(e : NullPointerException){
                            Log.e(TAG,"Couldn't fetch phone number for contactId: $contactId")
                        }
                    } while (it.moveToNext())
                }
            }
            result
        }
    }

    fun getRecipients(phones: List<String>) = phones.map { getRecipientInfo(it) }

    @SuppressLint("InlinedApi")
    fun getRecipientInfo(phone: String): Pair<ContactRow, Long?> = when {
        !context.hasPermission(Manifest.permission.READ_CONTACTS) -> Pair(phone.defaultResponse, null)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> Pair(phone.defaultResponse, null)
        else ->
            cr
                .firstOrNull(phone.lookupUri, projection = arrayOf(
                    PhoneLookup.CONTACT_ID,
                )) {
                    val contactId = it.getLong(PhoneLookup.CONTACT_ID)
                    Pair(getRecipientInfo(contactId)?.apply {
                        phoneNumber = phone
                    } ?: phone.defaultResponse, contactId)
                }
                ?: cr
                    .firstOrNull(
                        Phone.CONTENT_URI,
                        projection = arrayOf(
                            Phone.CONTACT_ID,
                        ),
                        // hack to match short codes
                        where = "REPLACE(${Phone.NUMBER}, '-', '') == \"$phone\""
                    ) {
                        val contactId = it.getLong(Phone.CONTACT_ID)
                        Pair(
                        getRecipientInfo(contactId)?.apply {
                            phoneNumber = phone
                        } ?: phone.defaultResponse, contactId)
                    }
                ?: Pair(phone.defaultResponse, null)
    }

    fun getRecipientInfo(id: Long): ContactRow? =
        cr.firstOrNull(
            ContactsContract.Data.CONTENT_URI,
            "${ContactsContract.Data.CONTACT_ID} = $id AND ${ContactsContract.Data.MIMETYPE} = '${StructuredName.CONTENT_ITEM_TYPE}'",
            projection = arrayOf(
                Contacts._ID,
                StructuredName.GIVEN_NAME,
                StructuredName.MIDDLE_NAME,
                StructuredName.FAMILY_NAME,
                StructuredName.DISPLAY_NAME
            )
        ) { contact ->
            val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id)
            val givenName = contact.getString(StructuredName.GIVEN_NAME)
            val middleName = contact.getString(StructuredName.MIDDLE_NAME)
            val familyName = contact.getString(StructuredName.FAMILY_NAME)
            val avatarUri = Uri.withAppendedPath(contactUri, Contacts.Photo.CONTENT_DIRECTORY)

            val displayName = contact.getString(StructuredName.DISPLAY_NAME)
            val alternativeDisplayName = if(givenName != null
                && givenName.isNotBlank()
                && familyName != null
                && familyName.isNotBlank()
            ){
                "$givenName $familyName".trim()
            }else{
                givenName
            }

            ContactRow(
                first_name = givenName,
                middle_name = middleName,
                last_name = familyName,
                /*avatar = Contacts.openContactPhotoInputStream(cr, contactUri, true)
                    ?.let { Base64.encodeToString(it.readBytes(), Base64.DEFAULT) }

                 */
                avatar = null,
                avatarUri = avatarUri,
                nickname = displayName
                    ?: alternativeDisplayName
            )
        }



    fun getAddressFromRecipientId(recipientId: Long): String? {
        val uri = Uri.withAppendedPath(Telephony.MmsSms.CONTENT_URI, "canonical-addresses")
        val projection = arrayOf(
            Telephony.Mms.Addr.ADDRESS
        )

        val selection = "${Telephony.Mms._ID} = ?"
        val selectionArgs = arrayOf(recipientId.toString())
        try {
            val cursor =
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    return cursor.getString(Telephony.Mms.Addr.ADDRESS)
                }
            }
        } catch (e: Exception) {
            Timber.e("Error getting phone number")
        }
        return null
    }

    companion object {
        private const val TAG = "ContactProvider"

        private val String.defaultResponse: ContactRow
            get() = ContactRow(nickname = this, phoneNumber = this)

        private val String.lookupUri: Uri
            get() = Uri.withAppendedPath(
                PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode("tel:${this}")
            )
    }
}
