package com.beeper.sms.commands.outgoing

data class Contact(
    var user_guid: String,
    var first_name: String? = null,
    var last_name: String? = null,
    var nickname: String? = null,
    var avatar: String? = null,
    var phones: List<String>? = null,
    var emails: List<String>? = null,
)