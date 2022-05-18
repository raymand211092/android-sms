package com.beeper.sms.app.ui.components
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.beeper.sms.app.ui.theme.getTextTitleColor
import com.beeper.sms.app.ui.theme.getToolbarBackgroundColor


@Composable
fun SMSAppBar(text: String, onBackButtonClicked: ()->Unit) {
    TopAppBar(backgroundColor = getToolbarBackgroundColor(), contentColor = getTextTitleColor()) {
        IconButton(onClick = onBackButtonClicked) {
            Icon(
                painter = rememberVectorPainter(image = Icons.Rounded.ArrowBack),
                contentDescription = null
            )
        }
        Text(text = text, color = getTextTitleColor())
    }
}