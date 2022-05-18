package com.beeper.sms.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest


@Composable
fun ContactAvatar(
    pictureInfo: PictureInfo,
    size: Dp = 28.dp, fontSize: TextUnit = 18.sp,
    backgroundBrush: Brush = Brush.linearGradient(
        listOf(
            Color(0xFFA042EA), Color(0xFF6152F2)
        )
    )
) {
    val avatarState = remember { mutableStateOf<AvatarState>(AvatarState.Loading) }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .size(size)
            .background(
                brush = backgroundBrush
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarState.value is AvatarState.Error) {
            Text(
                pictureInfo.firstLetterOfName.toString(),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize,
                ),
                color = Color.White, modifier = Modifier.align(Alignment.Center)
            )
        }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .diskCachePolicy(CachePolicy.DISABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .data(pictureInfo.pictureUri)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        ) { state ->
            if (state is AsyncImagePainter.State.Success) {
                avatarState.value = AvatarState.Loaded
                AsyncImageContent() // Draws the image.
            } else {
                if (state is AsyncImagePainter.State.Error) {
                    avatarState.value = AvatarState.Error
                } else {
                    avatarState.value = AvatarState.Loading
                }
            }
        }
    }
}