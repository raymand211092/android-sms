package com.beeper.sms.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.beeper.sms.app.ui.components.external.*
import com.beeper.sms.app.ui.util.formatMessageDate
import com.beeper.sms.commands.outgoing.Message


@ExperimentalFoundationApi
@Composable
fun MediaMessageItem(
    messageLayoutType: MessageLayoutType.DirectMessageLayoutType,
    messageClusterType: MessageClusterType,
    isOutgoing: Boolean,
    attachment: Message.Attachment,
    timestamp: Long?
) {
    BeeperDMLayout(
        type = messageLayoutType,
        onMessageClicked = { },
        onMessageLongClicked = {
            //Long click
        }) {
        BeeperDMBubbleLayout(clusterType = messageClusterType) {
            InnerMessageLayout(type = messageLayoutType) {
                MessageBubble(
                    isOutgoing = isOutgoing,
                    clusterType = messageClusterType,
                    backgroundType = MessageBubbleBackgroundType.TransparentBubble
                ) {
                    DisplayingImageVideoMessageContent(
                        attachment.path_on_disk, {}, {}
                    )
                    if (timestamp != null) {
                        Box(
                            contentAlignment = Alignment.Center, modifier = Modifier
                                .padding(end = 5.dp, bottom = 2.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    color = Color(0xFF212128).copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 3.dp)
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


@ExperimentalFoundationApi
@Composable
fun DisplayingImageVideoMessageContent(
    filePath: String,
    onMediaClick: () -> Unit,
    onMediaLongClick: () -> Unit
) {

    Box {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .data(filePath)
                .memoryCacheKey(filePath)
                .build(),
            contentScale = ContentScale.Inside,
            contentDescription = null,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onMediaClick,
                    onLongClick = onMediaLongClick
                )
                .height(128.dp),
        ) { state ->
            if (state is AsyncImagePainter.State.Success) {
                AsyncImageContent()
            } else {
                if (state is AsyncImagePainter.State.Error) {
                    Text("Error loading SMS attachment")
                }
            }
        }
    }
}