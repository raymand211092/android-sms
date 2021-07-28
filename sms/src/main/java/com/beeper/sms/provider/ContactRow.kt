package com.beeper.sms.provider

data class ContactRow(
    var first_name: String? = null,
    var last_name: String? = null,
    var nickname: String? = null,
    var phoneNumber: String? = null,
    var phoneType: String? = null,
    var avatar: String? = null,
) {
    val contactHash: Int
        get() = phoneNumber?.hashCode() ?: hashCode()

    val contactLetter: Char
        get() =
            listOf(nickname, first_name, last_name).firstNotNullOfOrNull { it?.get(0) } ?: ' '

    val displayName: String
        get() = nickname ?: "${first_name ?: ""} ${last_name ?: ""}".trim()
}