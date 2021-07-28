package com.beeper.sms.commands.incoming

import com.beeper.sms.provider.ContactRow

data class GetContact(var user_guid: String) {
    data class Response(
        var first_name: String? = null,
        var last_name: String? = null,
        var nickname: String? = null,
        var avatar: String? = null,
        var phones: List<String>? = null,
        var emails: List<String>? = null,
    ) {
        companion object {
            val ContactRow.asResponse: Response
                get() = Response(
                    first_name = first_name,
                    last_name = last_name,
                    nickname = nickname,
                    avatar = avatar,
                    phones = listOfNotNull(phoneNumber),
                )
        }
    }
}
