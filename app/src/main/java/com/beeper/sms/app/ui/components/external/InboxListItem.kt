package com.beeper.sms.app.ui.components.external

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun InboxListItem(
        trailingIcon: @Composable BoxScope.() -> Unit,
        avatar: @Composable BoxScope.() -> Unit,
        topRow: @Composable ColumnScope.() -> Unit,
        bottomRow: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = Modifier
            .height(64.dp)
            .fillMaxWidth()
    ) {
        Box(modifier = Modifier
                .fillMaxHeight()
                .padding(end = 9.dp)
                .align(Alignment.CenterVertically),
                content = trailingIcon
        )
        Box(modifier = Modifier
                .align(Alignment.CenterVertically),
                content = avatar)
        Column(modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 12.dp)) {
            topRow()
            bottomRow()
        }
    }
}
