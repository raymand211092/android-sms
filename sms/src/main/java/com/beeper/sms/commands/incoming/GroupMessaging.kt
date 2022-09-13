package com.beeper.sms.commands.incoming

import com.beeper.sms.provider.GuidProvider

@Suppress("PropertyName")
interface GroupMessaging {
    var chat_guid: String

    val recipients: String
        get() = chat_guid.removeSMSGuidPrefix()

    val recipientList: List<String>
        get()  = recipients.split(" ").map {
            GuidProvider.removeEscapingFromGuid(it)
        }


    companion object {
        private val REGEX = "^SMS;[+-];".toRegex()
        fun String.removeSMSGuidPrefix() = replace(REGEX, "")
    }
}