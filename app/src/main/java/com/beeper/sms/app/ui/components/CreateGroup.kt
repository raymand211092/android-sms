package com.beeper.sms.app.ui.components
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.beeper.sms.app.R

@Composable
fun CreateGroup(onGroupModeClicked: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onGroupModeClicked() }, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier
            .padding(start = 24.dp, end = 16.dp)
            .height(24.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_group_add_48px),
                tint = MaterialTheme.colors.primary,
                contentDescription = null
            )
        }
        Text("Create group", Modifier.weight(1f,fill = true))
    }
}
