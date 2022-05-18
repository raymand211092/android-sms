package com.beeper.sms.app.ui.components
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AppBarDefaults.TopAppBarElevation
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.R
import com.beeper.sms.app.ui.screen.newchat.CustomContactState
import com.beeper.sms.app.ui.screen.newchat.KeyboardMode
import com.beeper.sms.app.ui.screen.newchat.SelectedContacts
import com.beeper.sms.app.ui.theme.getTextTitleColor
import com.beeper.sms.app.ui.theme.getToolbarBackgroundColor
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowRow


@ExperimentalComposeUiApi
@Composable
fun RecipientChooser(
    text : String,
    keyboardMode: KeyboardMode,
    selectedContacts: SelectedContacts,
    customContactState: CustomContactState,
    onTextChanged : (String)->Unit,
    onKeyboardModeClicked : ()->Unit,
    onEmptyBackspacePressed : ()->Unit,
    onCustomNumberSelected : (String)->Unit
) {
    val focusRequester = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Surface(elevation = TopAppBarElevation, color = getToolbarBackgroundColor()) {
            Row(
                Modifier
                    .padding(top = 24.dp, bottom = 16.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "To", modifier = Modifier
                        .padding(start = 16.dp, end = 32.dp)
                        .align(Alignment.Top)
                )

                val keyboardType = when (keyboardMode) {
                    KeyboardMode.Default -> KeyboardType.Text
                    KeyboardMode.Dial -> KeyboardType.Phone
                }

                BasicTextField(value = text,
                    onValueChange = onTextChanged,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier
                        .align(Alignment.Top)
                        .weight(1f, true)
                        .onKeyEvent { event: KeyEvent ->
                            // handle backspace key
                            if (event.type == KeyEventType.KeyUp &&
                                event.key == Key.Backspace &&
                                text.isEmpty()
                            ) {
                                onEmptyBackspacePressed()
                                return@onKeyEvent true
                            }
                            false
                        }
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = getTextTitleColor(),
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(getTextTitleColor()),
                    decorationBox = { innerTextField ->
                        FlowRow(crossAxisAlignment = FlowCrossAxisAlignment.Center) {
                            when (selectedContacts) {
                                is SelectedContacts.GroupSelection -> {
                                    selectedContacts.selectedContacts.forEach {
                                        SelectedContactChip(it)
                                    }
                                }
                                is SelectedContacts.SingleSelection -> {
                                    SelectedContactChip(selectedContacts.selectedContact)
                                }
                                SelectedContacts.None -> {}
                            }
                            if (text.isEmpty() && selectedContacts is SelectedContacts.None) {
                                Box(Modifier.weight(1f, true)) {
                                    innerTextField()
                                    Text(
                                        "Type a name, phone number, or email",
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                innerTextField()
                            }
                        }
                    })
                IconButton(
                    onClick = onKeyboardModeClicked, modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(24.dp)
                        .align(Alignment.Top)
                ) {
                    val painterResource = when (keyboardMode) {
                        KeyboardMode.Default -> {
                            painterResource(id = R.drawable.ic_dialpad_48px)
                        }
                        KeyboardMode.Dial -> {
                            painterResource(id = R.drawable.ic_keyboard_48px)
                        }
                    }
                    Icon(
                        painter = painterResource,
                        contentDescription = null
                    )
                }
            }
        }
        Crossfade(targetState = customContactState) {
            when(it){
                CustomContactState.Hidden -> {}
                is CustomContactState.Showing -> {
                    CustomContactItem(it.number) {
                            number ->
                        onCustomNumberSelected(number)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}