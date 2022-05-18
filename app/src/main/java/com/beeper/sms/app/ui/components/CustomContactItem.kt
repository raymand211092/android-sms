package com.beeper.sms.app.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.ui.theme.getTextSubtitleColor
import com.beeper.sms.app.ui.theme.getTextTitleColor
import com.beeper.sms.app.ui.theme.getToolbarBackgroundColor

@Composable
fun CustomContactItem(
    number: String,
    onClicked: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClicked(number)
            }
            .padding(vertical = 8.dp)) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            val avatarSize = 48.dp
            Box(modifier = Modifier
                .clip(CircleShape)
                .size(avatarSize)
                .background(
                    color = getToolbarBackgroundColor()
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = rememberVectorPainter(image = Icons.Rounded.Person),
                    contentDescription = null,
                    tint = getTextTitleColor()
                )
            }
        }
        Column {
            Text(
                text = "Send to $number",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 18.sp,
                color = getTextTitleColor()
            )
            Text(
                text = number,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                color = getTextSubtitleColor()
            )

        }

    }
}