package com.beeper.sms.app.ui.components
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.ui.screen.newchat.SelectedContact
import com.beeper.sms.app.ui.theme.getSelectedContactChipBorderColor

@Composable
fun SelectedContactChip(it: SelectedContact) {
    Column(
        Modifier
            .padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
            .border(
                width = 1.dp,
                brush = SolidColor(getSelectedContactChipBorderColor()),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        val contactName = it.contact?.name
        val phoneNumber = it.phoneNumber

        Text(contactName ?: phoneNumber)
        if (contactName != phoneNumber) {
            Text(phoneNumber, fontSize = 12.sp)
        }

    }
}