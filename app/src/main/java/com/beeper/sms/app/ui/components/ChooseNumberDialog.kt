package com.beeper.sms.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.ui.model.UIContact
import com.beeper.sms.app.ui.theme.getTextTitleColor


@Composable
fun ChooseNumberDialog(state: ChooseNumberDialogState, onDismissed: ()-> Unit, onNumberSelected: (String, UIContact)->Unit){
    when(state) {
        ChooseNumberDialogState.Closed -> {}
        is ChooseNumberDialogState.Open -> {
            AlertDialog(
                shape = RoundedCornerShape(8.dp),
                onDismissRequest = onDismissed,
                text = {
                    Column {
                        Text(text = "Select destination",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            color = getTextTitleColor()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = state.contact.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 18.sp,
                            )
                        }
                    }
                },
                buttons = {
                    Column(verticalArrangement = Arrangement.Center){
                        state.contact.phoneNumbers.onEach {
                                number ->
                            Text(number,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable { onNumberSelected(number, state.contact) }
                                    .padding(horizontal = 24.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            )
        }
    }
}