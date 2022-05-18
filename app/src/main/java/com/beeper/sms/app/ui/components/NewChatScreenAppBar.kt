package com.beeper.sms.app.ui.components
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.beeper.sms.app.ui.screen.newchat.SelectionMode
import com.beeper.sms.app.ui.theme.getTextTitleColor
import com.beeper.sms.app.ui.theme.getToolbarBackgroundColor


@Composable
fun NewChatScreenAppBar(text: String, selectionMode: SelectionMode,
                        onBackButtonClicked: ()->Unit, onNextClicked: () -> Unit) {
    TopAppBar(backgroundColor = getToolbarBackgroundColor(), contentColor = getTextTitleColor()) {
        IconButton(onClick = onBackButtonClicked) {
            Icon(
                painter = rememberVectorPainter(image = Icons.Rounded.ArrowBack),
                contentDescription = null
            )
        }
        Text(text = text, color = getTextTitleColor())
        if(selectionMode is SelectionMode.Group) {
            Spacer(Modifier.weight(1f, true))
            TextButton(onClick = onNextClicked) {
                Text("Next")
            }
        }
    }
}
