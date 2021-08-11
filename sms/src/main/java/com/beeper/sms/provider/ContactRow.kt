package com.beeper.sms.provider

data class ContactRow(
    var first_name: String? = null,
    var last_name: String? = null,
    var nickname: String? = null,
    var phoneNumber: String? = null,
    var phoneType: String? = null,
    var avatar: String? = null,
)