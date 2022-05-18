package com.beeper.sms.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.beeper.sms.app.ui.components.external.MessageComposer
import kotlinx.coroutines.launch

@Composable
fun ComposerArea(
    sendMessage: suspend (String) -> Unit,
    sendFile: suspend (Uri) -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { fileUri ->
        if (fileUri != null) {
            scope.launch {
                val result = sendFile(fileUri)
                if (!result) {
                    Toast.makeText(
                        context,
                        "Couldn't send the file. It is > 400KB or an error occurred",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    MessageComposer(
        text = text,
        onAddClicked = {
            pickFileLauncher.launch("*/*")
        },
        onTextChanged = { newText -> text = newText },
        onSendClicked = {
            scope.launch {
                sendMessage(text)
                text = ""
            }
        },
    )
}

