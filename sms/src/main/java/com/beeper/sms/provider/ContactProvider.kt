package com.beeper.sms.provider

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.PhoneLookup
import com.beeper.sms.commands.incoming.GetContact
import com.beeper.sms.extensions.firstOrNull
import com.beeper.sms.extensions.getLong
import com.beeper.sms.extensions.getString
import com.beeper.sms.extensions.hasPermission

class ContactProvider constructor(private val context: Context) {
    private val cr = context.contentResolver

    fun getContacts(phones: List<String>) = phones.map { getContact(it) }

    fun getContact(phone: String): GetContact.Response = when {
        !context.hasPermission(Manifest.permission.READ_CONTACTS) -> phone.defaultResponse
        Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> phone.defaultResponse
        else ->
            cr.firstOrNull(phone.lookupUri) { ph ->
                cr.firstOrNull(
                    ContactsContract.Data.CONTENT_URI,
                    "${ContactsContract.Data.CONTACT_ID} = ${ph.getLong(PhoneLookup.CONTACT_ID)} AND ${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/name'"
                ) { contact ->
                    val givenName = contact.getString(StructuredName.GIVEN_NAME)
                    val familyName = contact.getString(StructuredName.FAMILY_NAME)
                    GetContact.Response(
                        first_name = givenName,
                        last_name = familyName,
                        nickname = contact.getString(StructuredName.DISPLAY_NAME)
                            ?: "$givenName $familyName".trim().takeIf { it.isNotBlank() }
                            ?: phone,
                        phones = listOf(phone)
                    )
                }
            }
                ?: phone.defaultResponse
    }

    companion object {
        private val String.defaultResponse: GetContact.Response
            get() = GetContact.Response(
                nickname = this,
                phones = listOf(this),
            )

        private val String.lookupUri: Uri
            get() = Uri.withAppendedPath(
                PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode("tel:${this}")
            )
    }
}