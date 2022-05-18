package com.beeper.sms.app.ui.components
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.ui.model.UIContact
import com.beeper.sms.app.ui.theme.getTextSubtitleColor
import com.beeper.sms.app.ui.theme.getTextTitleColor

@Composable
fun ContactItem(
    contact: UIContact,
    isSelected: Boolean,
    onContactClicked: (UIContact) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onContactClicked(contact)
            }
            .padding(vertical = 8.dp)) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            val avatarSize = 48.dp
            val fontSize = 18.sp
            Crossfade(isSelected) {
                if (!it) {
                    ContactAvatar(
                        pictureInfo = PictureInfo(
                            contact.name[0],
                            contact.avatarUri
                        ),
                        size = avatarSize,
                        fontSize = fontSize
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(avatarSize)
                            .background(
                                color = MaterialTheme.colors.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = rememberVectorPainter(image = Icons.Rounded.Check),
                            contentDescription = null,
                            tint = Color.Black
                        )
                    }
                }
            }
        }
        Column {
            Text(
                text = contact.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 18.sp,
                color = getTextTitleColor()
            )
            Row{
                Text(
                    text = contact.phoneNumbers.firstOrNull() ?: "--",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    color = getTextSubtitleColor()
                )
                Spacer(modifier = Modifier.weight(1f,true))
                if(contact.phoneNumbers.size > 1) {
                    Text(
                        text = "Multiple",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 16.dp),
                        color = getTextSubtitleColor()
                    )
                }
            }
        }

    }
}
