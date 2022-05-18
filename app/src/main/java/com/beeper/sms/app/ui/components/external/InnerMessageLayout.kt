package com.beeper.sms.app.ui.components.external

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InnerMessageLayout(
    type: MessageLayoutType,
    modifier: Modifier = Modifier,
    addTrailingSpace : Boolean = false,
    content: @Composable BoxScope.() -> Unit) {

    val horizontalPadding = when(type){
        is MessageLayoutType.DirectMessageLayoutType -> {
            PaddingValues(horizontal = 8.dp)
        }
        is MessageLayoutType.GroupMessageLayoutType -> {
            when(type){
                is MessageLayoutType.GroupMessageLayoutType.IngoingGroupMessageLayoutType -> {
                    PaddingValues(horizontal = 0.dp)
                }
                MessageLayoutType.GroupMessageLayoutType.OutgoingGroupMessageLayoutType -> {
                    PaddingValues(horizontal = 8.dp)

                }
            }
        }
    }

    when(type){
        MessageLayoutType.DirectMessageLayoutType.IngoingDirectMessageLayoutType -> {
            Row(modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontalPadding)) {
                Box {
                    content()
                }
                Spacer(modifier = Modifier.weight(1f, fill = true))
            }
        }
        MessageLayoutType.DirectMessageLayoutType.OutgoingDirectMessageLayoutType -> {
            Row(modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontalPadding)) {
                Spacer(modifier = Modifier.weight(1f, fill = true))
                Box {
                    content()
                }
            }
        }
        is MessageLayoutType.GroupMessageLayoutType.IngoingGroupMessageLayoutType -> {
            Row(modifier = modifier
                    .fillMaxWidth()) {
                if(addTrailingSpace) {
                    Spacer(
                            Modifier.padding(start = 16.dp).width(28.dp)
                    )
                }
                Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontalPadding)) {
                    Box {
                        content()
                    }
                    Spacer(modifier = Modifier.weight(1f, fill = true))
                }
            }
        }
        MessageLayoutType.GroupMessageLayoutType.OutgoingGroupMessageLayoutType -> {
            Row(modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontalPadding)) {
                Spacer(modifier = Modifier.weight(1f, fill = true))
                Box {
                    content()
                }
            }
        }
    }
}
