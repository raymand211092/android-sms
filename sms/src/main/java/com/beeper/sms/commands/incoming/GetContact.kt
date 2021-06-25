package com.beeper.sms.commands.incoming

data class GetContact(var user_guid: String) {
    data class Response(
        var first_name: String? = null,
        var last_name: String? = null,
        var nickname: String? = null,
        var avatar: String? = null,
        var phones: List<String>? = null,
        var emails: List<String>? = null,
    )
}
