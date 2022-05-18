package com.beeper.sms.app.ui.components.external

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.beeper.sms.app.R
import com.beeper.sms.app.ui.model.UIChatThread
import com.beeper.sms.app.ui.theme.*

@ExperimentalFoundationApi
@Composable
fun SMSThreadsSection(
    threads: List<UIChatThread>,
    onClick: (UIChatThread) -> Unit,
    onLongClick: (UIChatThread) -> Unit,
) {
    val isNotEmpty = threads.isNotEmpty()
    if (isNotEmpty) {
        Box(modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) {}
            .background(color = getFavoritesItemBackgroundColor())
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = getFavoritesItemBackgroundColor(),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(items = threads, key = {it.id}) { thread ->
                        Box(
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        onClick(thread)
                                    },
                                    onLongClick = {
                                        onLongClick(thread)
                                    },
                                )
                                .padding(start = 7.dp, end = 8.dp)
                        ) {
                            InboxListItem(
                                trailingIcon = {
                                    if (thread.isRead) {
                                        Box(Modifier
                                            .size(10.dp)
                                            .align(Alignment.Center)
                                            .background(
                                                shape = CircleShape,
                                                color = thread.indicatorColor
                                            )
                                        ){}
                                    } else {
                                        Spacer(Modifier.size(10.dp))
                                    }
                                },
                                avatar = {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .data(thread.avatarUrl)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    ) { state ->
                                        if (state is AsyncImagePainter.State.Success) {
                                            AsyncImageContent() // Draws the image.
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .size(40.dp)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                Color(0xff949598),
                                                                Color(0xff68686D),

                                                                )
                                                        )
                                                    )
                                                    .align(Alignment.Center)
                                            ) {
                                                Text(
                                                    thread.firstLetter,
                                                    style = TextStyle(
                                                        fontFamily = FontFamily.SansSerif,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 18.sp,
                                                        lineHeight = 20.sp,
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier.align(Alignment.Center)
                                                )
                                            }
                                        }
                                    }
                                },
                                topRow = {
                                    Row(
                                        modifier = Modifier.padding(bottom = 2.dp),
                                    ) {
                                        Text(
                                            thread.name,
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .weight(1f),
                                            maxLines = 1,
                                            style = MaterialTheme.typography.h2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = getBeeperContentPrimaryColor()
                                        )
                                        Text(
                                            thread.timestamp.toString(),
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically),
                                            maxLines = 1,
                                            style = MaterialTheme.typography.body2,
                                            color = getBeeperContentSecondaryColor()
                                        )
                                    }
                                },
                                bottomRow = {
                                    Row {
                                        Icon(
                                            painter = painterResource(
                                                R.drawable.ic_network_android_sms
                                            ),
                                            tint = BeeperBase500DarkColor,
                                            contentDescription = "SMS",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.CenterVertically)
                                        )

                                        Box(
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                thread.lastMessage.toString(),
                                                modifier = Modifier
                                                    .padding(start = 0.dp),
                                                maxLines = 1,
                                                style = MaterialTheme.typography.body1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = getBeeperContentSecondaryColor()
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}



