package com.beeper.sms.app.ui.components.external

sealed class TextMessageType {
        abstract val isOutgoing: Boolean
        abstract val groupType: MessageClusterType

        data class JumboEmoji(
            override val isOutgoing: Boolean,
            override val groupType: MessageClusterType,
        ) : TextMessageType()

        data class DefaultMessage(
            override val isOutgoing: Boolean,
            override val groupType: MessageClusterType,
        ) : TextMessageType()
    }
