package com.beeper.sms.provider

import android.net.Uri

data class ContactRow(
    var first_name: String? = null,
    var middle_name: String? = null,
    var last_name: String? = null,
    var nickname: String? = null,
    var phoneNumber: String? = null,
    var phoneType: String? = null,
    var avatar: String? = null,
    var avatarUri: Uri? = null,
)