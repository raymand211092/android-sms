package com.beeper.sms.provider

import android.net.Uri

data class ContactInfo(
    val contactId: Long,
    val name: String,
    val starred: Boolean = false,
    val avatarUri: Uri? = null,
    val phoneNumbers: List<String> = listOf(),
    val phoneTypes: List<String> = listOf()
)
