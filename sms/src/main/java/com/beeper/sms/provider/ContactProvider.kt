package com.beeper.sms.provider

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.PhoneLookup
import com.beeper.sms.extensions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream

class ContactProvider constructor(private val context: Context) {
    private val cr = context.contentResolver

    fun getContacts(phones: List<String>) = phones.map { getContact(it) }

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
                ?: phone.defaultResponse
    }

    suspend fun searchContacts(text: String): List<ContactRow> =
        withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(
                if (text.isBlank()) {
                    ContactsContract.Contacts.CONTENT_URI
                } else {
                    ContactsContract.Contacts.CONTENT_FILTER_URI
                },
                Uri.encode(text)
            )
            cr
                .flatMap(
                    uri,
                    where = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
                ) { c ->
                    val id = c.getLong(ContactsContract.Contacts._ID)
                    val contact = getContact(id) ?: return@flatMap emptyList<ContactRow>()
                    cr.map(
                        ContactsContract.Data.CONTENT_URI,
                        "${ContactsContract.Data.CONTACT_ID} = $id AND ${ContactsContract.Data.MIMETYPE} = '${Phone.CONTENT_ITEM_TYPE}'"
                    ) { ph ->
                        val phone = ph.getString(Phone.NORMALIZED_NUMBER) ?: return@map null
                        contact.copy(
                            phoneNumber = phone,
                            phoneType = Phone.getTypeLabel(
                                context.resources,
                                ph.getInt(Phone.TYPE),
                                ph.getString(Phone.LABEL)
                            ).toString(),
                            avatar = ph.getString(Phone.PHOTO_URI),
                        )
                    }
                }
                .filter { it.phoneNumber?.isNotBlank() == true }
                .distinctBy { it.phoneNumber }
                .sortedWith(compareBy({it.last_name ?: it.first_name ?: ""}, {it.first_name}))
        }

    private fun getContact(id: Long): ContactRow? =
        cr.firstOrNull(
            ContactsContract.Data.CONTENT_URI,
            "${ContactsContract.Data.CONTACT_ID} = $id AND ${ContactsContract.Data.MIMETYPE} = '${StructuredName.CONTENT_ITEM_TYPE}'"
        ) { contact ->
            val contactPhotoUri = contact.getString(StructuredName.PHOTO_URI)
            val contactPhoto = if(contactPhotoUri !== null) {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, Uri.parse(contactPhotoUri)))
                } else {
                    MediaStore.Images.Media.getBitmap(cr, Uri.parse(contactPhotoUri))
                }
                val byteArrayOutputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                    val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
                    Base64.encodeToString(byteArray,Base64.DEFAULT)
                } else {
                    null
                }
            val givenName = contact.getString(StructuredName.GIVEN_NAME)
            val familyName = contact.getString(StructuredName.FAMILY_NAME)
            ContactRow(
                first_name = givenName,
                last_name = familyName,
                avatar = contactPhoto,
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
