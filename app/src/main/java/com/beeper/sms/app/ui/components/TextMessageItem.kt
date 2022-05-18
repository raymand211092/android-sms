package com.beeper.sms.app.ui.components

import android.content.res.Resources
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.beeper.sms.app.ui.components.DimensionUtils.px
import com.beeper.sms.app.ui.components.external.*
import com.beeper.sms.app.ui.theme.getMessageTextColor
import com.beeper.sms.app.ui.util.formatMessageDate

@ExperimentalFoundationApi
@Composable
fun TextMessageItem(
    messageLayoutType: MessageLayoutType.DirectMessageLayoutType,
    messageClusterType: MessageClusterType,
    isOutgoing: Boolean,
    bubbleBackgroundType: MessageBubbleBackgroundType,
    text: CharSequence,
    timestamp: Long?
) {
    BeeperDMLayout(type = messageLayoutType, { }, {
        //onLongClick
    }) {
        BeeperDMBubbleLayout(clusterType = messageClusterType) {
            InnerMessageLayout(type = messageLayoutType) {
                MessageBubble(
                    isOutgoing = isOutgoing,
                    clusterType = messageClusterType,
                    backgroundType = bubbleBackgroundType
                ) {
                    TextMessage(
                        text = text,
                        modifier = Modifier.padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = 4.dp,
                            bottom = 6.dp
                        ), textColor = getMessageTextColor().toArgb(),
                        linkTextColor = getMessageTextColor().toArgb(),
                        isClickable = false,
                        onClick = {},
                        onLongClick = {}
                    )

                    if (timestamp != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 2.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Timestamp(
                                time = formatMessageDate(timestamp, LocalContext.current),
                                isOutgoing
                            )
                        }
                    }
                }
            }
        }
    }
}

fun buildSpecialSpacingSpannable(timestamp: String): SpannableStringBuilder {
    val digitSpace = "\u2007"
    val noBreakingSpace = "\u00A0"
    val builder = StringBuilder(digitSpace)
    timestamp.onEach {
        // digit space char
        builder.append(digitSpace)
    }
    // special space character to avoid trimming the content
    builder.append(noBreakingSpace)
    return SpannableStringBuilder(builder).apply {
        setSpan(
            AbsoluteSizeSpan(TIMESTAMP_FONT_SIZE.px),
            0,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}

fun isRTL(text: String): Boolean {
    if (text.isBlank()) {
        return false
    }
    val character = text[0]
    val directionality = Character.getDirectionality(character)
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
}

object DimensionUtils {
    val Int.px: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
}