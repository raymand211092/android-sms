package com.beeper.sms.provider

import android.net.Uri

data class ExtendedContactRow(
    val id: Long,
    val name: String,
    val starred: Boolean = false,
    val avatarUri: Uri? = null,
    val phoneNumbers: List<String> = listOf(),
    val emails: List<String> = listOf())