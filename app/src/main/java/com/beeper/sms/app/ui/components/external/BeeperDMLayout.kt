package com.beeper.sms.app.ui.components.external

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@ExperimentalFoundationApi
@Composable
fun BeeperDMLayout(
    type: MessageLayoutType.DirectMessageLayoutType,
    onMessageClicked: () -> Unit,
    onMessageLongClicked: () -> Unit,
    content: @Composable() (ColumnScope.() -> Unit),
) {
    val padding = when (type) {
        MessageLayoutType.DirectMessageLayoutType.IngoingDirectMessageLayoutType -> PaddingValues(end = 36.dp)
        MessageLayoutType.DirectMessageLayoutType.OutgoingDirectMessageLayoutType -> PaddingValues(start = 36.dp)
    }
    Column(modifier = Modifier
            .combinedClickable(
                    onClick = {
                        onMessageClicked()
                    },
                    onLongClick = {
                        onMessageLongClicked()
                    }
            )
            .padding(padding)
            .heightIn(min = 24.dp)
    ) {
        content()
    }
}


@Composable
fun BeeperDMBubbleLayout(
    clusterType: MessageClusterType,
    modifier : Modifier = Modifier,
    content: @Composable() (ColumnScope.() -> Unit),
) {
    val topPadding: Dp = when (clusterType) {
        MessageClusterType.OnCluster.Last,
        MessageClusterType.OnCluster.Middle -> {
            2.dp
        }
        MessageClusterType.OnCluster.First, MessageClusterType.Single -> {
            4.dp
        }
    }

    Box(modifier = modifier
            .padding(top = topPadding)
    ) {
        Box(Modifier
                .fillMaxWidth()) {
            Column {
                content()
            }
        }
    }
}
