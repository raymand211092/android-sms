package com.beeper.sms.commands.incoming

data class SendMessage(
    var chat_guid: String,
    var text: String,
) {
    data class Response(
        var guid: String,
        var timestamp: Long,
    )

    val recipients: String
        get() = chat_guid.removePrefix()

    val isDirectMessage: Boolean
        get() = chat_guid.isDirectMessage

    companion object {
        private val REGEX = "^SMS;[+-];".toRegex()
        private const val DM_PREFIX = "SMS;-;"

        internal fun String.removePrefix() = replace(REGEX, "")

        internal val String.isDirectMessage: Boolean
            get() = startsWith(DM_PREFIX)
    }
}