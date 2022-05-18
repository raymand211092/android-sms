package com.beeper.sms.app.ui.model

import android.net.Uri

data class UIContact(val id: String,
                     val name: String,
                     val favorite: Boolean = false,
                     val avatarUri: Uri? = null,
                     val phoneNumbers: Set<String> = setOf(),
                     val email: String? = null)