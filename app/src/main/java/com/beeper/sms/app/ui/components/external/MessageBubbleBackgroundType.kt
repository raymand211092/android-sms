package com.beeper.sms.app.ui.components.external

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

sealed class MessageBubbleBackgroundType {
        data class SolidBubble(val color: Color) : MessageBubbleBackgroundType()
        data class GradientBubble(val brush: Brush) : MessageBubbleBackgroundType()
        object TransparentBubble : MessageBubbleBackgroundType()
}
