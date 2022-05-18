package com.beeper.sms.app.ui.components.external

import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.R
import com.beeper.sms.app.ui.theme.*


@Composable
fun MessageComposer(
    text: String,
    onAddClicked: () -> Unit,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
) {
    var isTextFieldFocused by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
                .background(getBeeperConversationBgColor())
                .padding(
                    start = 0.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = 0.dp
                ),
            verticalAlignment = CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.align(Alignment.Bottom)) {
                IconButton(
                    onClick = onAddClicked, modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(start = 6.dp, bottom = 12.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_beeper_plus),
                        tint = getBeeperConversationComposerOutlineColor(),
                        contentDescription = null, modifier = Modifier.size(24.dp)
                    )
                }
            }
            val textFieldStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = getBeeperConversationComposerOutlineColor()
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .clip(
                        RoundedCornerShape(16.dp)
                    )
                    .padding(bottom = 12.dp, top = 8.dp)
                    .border(
                        1.dp,
                        color = getBeeperConversationComposerOutlineBgColor(),
                        shape = RoundedCornerShape(16.dp)

                    )
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.heightIn(24.dp, 145.dp)
                ) {
                    val innerTextFieldAlpha = 1f
                    Box(
                        modifier = Modifier
                            .padding(
                                horizontal = 16.dp,
                                vertical = 8.dp
                            )
                            .weight(1.0f, fill = true)
                            .alpha(innerTextFieldAlpha)
                            .align(Alignment.Bottom)

                    ) {
                        if (text.isEmpty() && !isTextFieldFocused) {
                            Text("Send a message...", style = textFieldStyle)
                        }

                        BasicTextField(
                            value = text,
                            cursorBrush = SolidColor(BeeperAzure500Color),
                            onValueChange = { newText ->
                                onTextChanged(newText)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    isTextFieldFocused = it.isFocused
                                },
                            textStyle = TextStyle(
                                color = getBeeperContentPrimaryColor(),
                                fontSize = 15.sp,
                            )
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .padding(end = 0.dp, top = 0.dp, bottom = 2.dp)
                            .align(Alignment.Bottom)

                    ) {
                        Box(modifier = Modifier.height(32.dp), contentAlignment = BottomCenter) {

                            val enterAnimation =
                                if (text.isBlank()) fadeIn() else fadeIn() + expandHorizontally()
                            val exitAnimation =
                                if (text.isBlank()) fadeOut() else fadeOut() + shrinkHorizontally()
                            androidx.compose.animation.AnimatedVisibility(
                                enter = enterAnimation, exit = exitAnimation,
                                visible = (text.isNotBlank())
                            ) {

                                Box(
                                    modifier = Modifier
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }) {
                                            onSendClicked()
                                        }
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_send_message),
                                        tint = BeeperAzure500Color,
                                        contentDescription = null, modifier = Modifier
                                            .size(32.dp)
                                            .padding(0.dp)
                                            .align(BottomCenter)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }

        }
    }
}
