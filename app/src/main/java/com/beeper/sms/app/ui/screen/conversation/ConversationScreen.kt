package com.beeper.sms.app.ui.screen.conversation

import android.net.Uri
import android.text.SpannableStringBuilder
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.beeper.sms.app.ui.components.*
import com.beeper.sms.app.ui.components.external.MessageBubbleBackgroundType
import com.beeper.sms.app.ui.components.external.*
import com.beeper.sms.app.ui.theme.getIncomingMessageBackgroundColor
import com.beeper.sms.app.ui.util.formatMessageDate
import kotlinx.coroutines.launch

@ExperimentalFoundationApi
@Composable
fun ConversationScreen(
    conversationState: ConversationState,
    onBackClicked: () -> Unit,
    sendMessage: suspend (String) -> Unit,
    sendFile: suspend (Uri) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        when (conversationState) {
            ConversationState.Error -> {
                SMSAppBar(text = "Conversation", onBackButtonClicked = onBackClicked)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, true)
                ) {
                    Text(
                        "Error loading conversation",
                        modifier = Modifier.align(
                            Alignment.Center
                        )
                    )
                }
            }
            ConversationState.Loading -> {
                SMSAppBar(text = "", onBackButtonClicked = onBackClicked)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, true)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(
                            Alignment.Center
                        )
                    )
                }
            }
            is ConversationState.Loaded -> {
                val threadMemberNames = conversationState.thread.members.values.map {
                    it.nickname ?: it.phoneNumber ?: "Unknown contact"
                }
                val memberNamesConcat = if(threadMemberNames.isEmpty()){
                    "Unknown contact"
                }else{
                    threadMemberNames.reduce { acc, s -> "$acc, $s" }
                }
                SMSAppBar(text = memberNamesConcat, onBackButtonClicked = onBackClicked)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, true)
                ) {

                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        state = listState
                    ) {
                        items(
                            items = conversationState.messages,
                            key = { message -> message.guid }) { message ->

                            val isOutgoing = message.is_from_me
                            val isFirstFromThisSender = false
                            val isLastFromThisSender = true
                            val messageClusterType =
                                if (isLastFromThisSender && isFirstFromThisSender) {
                                    MessageClusterType.Single
                                } else {
                                    if (isFirstFromThisSender) {
                                        MessageClusterType.OnCluster.First
                                    } else {
                                        if (isLastFromThisSender) {
                                            MessageClusterType.OnCluster.Last
                                        } else {
                                            MessageClusterType.OnCluster.Middle
                                        }
                                    }
                                }

                            val shouldShowTimestamp = true

                            val messageType: TextMessageType = TextMessageType.DefaultMessage(
                                isOutgoing, messageClusterType
                            )

                            val messageText = if (shouldShowTimestamp) {
                                val rtlMarker = "\u200F"
                                val timestamp = message.timestamp
                                val specialSpacingSpannable = buildSpecialSpacingSpannable(
                                    formatMessageDate(
                                        timestamp.toMillis().toLong(),
                                        LocalContext.current
                                    )
                                )
                                val text = message.text
                                if (text.isBlank()) {
                                    SpannableStringBuilder(text)
                                } else {
                                    if (!isRTL(text)) {
                                        SpannableStringBuilder(text).append(
                                            specialSpacingSpannable
                                        )
                                    } else {
                                        if (text.length > 30) {
                                            SpannableStringBuilder(text).append("\n")
                                        } else {
                                            SpannableStringBuilder(rtlMarker).append(
                                                specialSpacingSpannable
                                            ).append(text)
                                        }
                                    }
                                }
                            } else {
                                SpannableStringBuilder(message.text)
                            }

                            val messageLayoutType =
                                if (message.is_from_me) {
                                    MessageLayoutType.DirectMessageLayoutType.OutgoingDirectMessageLayoutType
                                } else {
                                    MessageLayoutType.DirectMessageLayoutType.IngoingDirectMessageLayoutType
                                }


                            val bubbleBackgroundType = when (messageType) {
                                is TextMessageType.DefaultMessage -> {
                                    if (messageType.isOutgoing) {
                                        MessageBubbleBackgroundType.GradientBubble(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFFA042EA), Color(0xFF6152F2)
                                                ),
                                                start = Offset(0f, 0f),
                                                end = Offset(
                                                    Float.POSITIVE_INFINITY,
                                                    Float.POSITIVE_INFINITY
                                                )
                                            ),
                                        )
                                    } else {
                                        MessageBubbleBackgroundType.SolidBubble(
                                            color = getIncomingMessageBackgroundColor()
                                        )
                                    }
                                }
                                is TextMessageType.JumboEmoji -> {
                                    MessageBubbleBackgroundType.TransparentBubble
                                }
                            }
                            message.attachments?.onEach {
                                val mimetype = it.mime_type
                                if(mimetype == null ||
                                    !(mimetype.startsWith("image") ||
                                            mimetype.startsWith("video"))
                                        ){
                                    TextMessageItem(
                                        messageLayoutType,
                                        messageClusterType,
                                        isOutgoing,
                                        bubbleBackgroundType,
                                        "Attachment: ${it.file_name} $messageText",
                                        message.timestamp.toMillis().toLong()
                                    )
                                }else{
                                    MediaMessageItem(
                                        messageLayoutType,
                                        messageClusterType,
                                        isOutgoing,
                                        it,
                                        message.timestamp.toMillis().toLong()
                                    )
                                }
                            }
                            if (message.text.isNotBlank()) {
                                TextMessageItem(
                                    messageLayoutType,
                                    messageClusterType,
                                    isOutgoing,
                                    bubbleBackgroundType,
                                    messageText,
                                    message.timestamp.toMillis().toLong()
                                )
                            }
                        }
                    }
                    SideEffect {
                        val lastIndex = conversationState.messages.lastIndex
                        if (lastIndex >= 0) {
                            scope.launch {
                                listState.scrollToItem(
                                    lastIndex
                                )
                            }
                        }
                    }
                }
            }
        }
        ComposerArea(sendMessage = sendMessage, sendFile = sendFile)
    }
}
