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
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getLong
import com.beeper.sms.extensions.getString
import com.beeper.sms.extensions.hasPermission

class ContactProvider constructor(private val context: Context) {
    private val cr = context.contentResolver

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
                        where = "REPLACE(${Phone.NUMBER}, '-', '') == '$phone'"
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
            ContactRow(
                first_name = givenName,
                last_name = familyName,
                avatar = Contacts.openContactPhotoInputStream(cr, contactUri, true)
                    ?.let { Base64.encodeToString(it.readBytes(), Base64.DEFAULT) },
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
