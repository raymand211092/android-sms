package com.beeper.sms.app.ui.components
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.beeper.sms.app.ui.theme.getTextTitleColor

@Composable
fun TopContactItem(contact: UIContact, isSelected: Boolean, onContactClicked: (UIContact) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                onContactClicked(contact)
            }
            .width(72.dp)) {
        val avatarSize = 56.dp
        val fontSize = 24.sp

        Box(Modifier.padding(bottom = 4.dp)) {

            Crossfade(isSelected){
                if (!it) {
                    ContactAvatar(
                        pictureInfo = PictureInfo(
                            contact.name[0],
                            contact.avatarUri
                        ),
                        size = avatarSize,
                        fontSize = fontSize
                    )
                }else{
                    Box(modifier = Modifier
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
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
        Text(text = contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp, color = getTextTitleColor()
        )

    }
}
