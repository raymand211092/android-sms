
package com.beeper.sms.app.ui.components.external

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.beeper.sms.app.R

@Composable
fun MessageBubble(
    isOutgoing: Boolean,
    clusterType: MessageClusterType,
    backgroundType: MessageBubbleBackgroundType,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val clippedShape = when (clusterType) {
        MessageClusterType.Single -> {
            RoundedCornerShape(12.dp)
        }
        MessageClusterType.OnCluster.First -> {
            if (isOutgoing) {
                RoundedCornerShape(12.dp, 12.dp, 6.dp, 12.dp)
            } else {
                RoundedCornerShape(12.dp, 12.dp, 12.dp, 6.dp)
            }
        }
        MessageClusterType.OnCluster.Middle -> {
            if (isOutgoing) {
                RoundedCornerShape(12.dp, 6.dp, 6.dp, 12.dp)
            } else {
                RoundedCornerShape(6.dp, 12.dp, 12.dp, 6.dp)
            }
        }
        MessageClusterType.OnCluster.Last -> {
            if (isOutgoing) {
                RoundedCornerShape(12.dp, 6.dp, 12.dp, 12.dp)
            } else {
                RoundedCornerShape(6.dp, 12.dp, 12.dp, 12.dp)
            }
        }
    }

    val clippedModifier = modifier
            .padding(horizontal = 4.dp)
            .clip(clippedShape)
    val modifierToApply = when (backgroundType) {
        is MessageBubbleBackgroundType.GradientBubble -> clippedModifier.background(
                brush = backgroundType.brush
        )
        is MessageBubbleBackgroundType.SolidBubble -> clippedModifier.background(
                color = backgroundType.color
        )
        MessageBubbleBackgroundType.TransparentBubble -> clippedModifier
    }

    Box(Modifier.padding(horizontal = 0.dp)) {
        if (backgroundType is MessageBubbleBackgroundType.SolidBubble &&
                (clusterType is MessageClusterType.Single || clusterType is MessageClusterType.OnCluster.Last)) {
            Icon(
                    painter = painterResource(id = R.drawable.ic_tail_rot),
                    contentDescription = null,
                    tint = backgroundType.color,
                    modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 1.dp, start = 0.dp)
                            .scale(scaleX = -1f, scaleY = 1f)
            )
        }
        if (backgroundType is MessageBubbleBackgroundType.GradientBubble &&
                (clusterType is MessageClusterType.Single || clusterType is MessageClusterType.OnCluster.Last)) {
            Icon(
                    painter = painterResource(id = R.drawable.ic_tail_rot),
                    contentDescription = null,
                    tint = Color(0xFF6152F2),
                    modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 1.dp, end = 0.dp)
            )
        }
        Box(modifier = modifierToApply, content = content)
    }
}
