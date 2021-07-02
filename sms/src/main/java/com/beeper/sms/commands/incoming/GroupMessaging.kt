package com.beeper.sms.commands.incoming

@Suppress("PropertyName")
interface GroupMessaging {
    var chat_guid: String

    val recipients: String
        get() = chat_guid.removePrefix()

    val recipientList: List<String>
        get() = recipients.split(" ")

    companion object {
        private val REGEX = "^SMS;[+-];".toRegex()
        private fun String.removePrefix() = replace(REGEX, "")
    }
}