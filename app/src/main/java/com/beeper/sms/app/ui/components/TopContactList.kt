package com.beeper.sms.app.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.ui.model.UIContact
import com.beeper.sms.app.ui.screen.newchat.SelectedContacts
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment

@Composable
fun TopContactList(topContacts: List<UIContact>,
                   selectedContacts: SelectedContacts,
                   onContactClicked: (UIContact) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
            .padding(16.dp)) {
        Text("Top contacts",
            modifier = Modifier.padding(bottom = 24.dp),
            style = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )
        FlowRow(mainAxisSpacing = 12.dp,
            crossAxisSpacing = 8.dp,
            mainAxisAlignment = MainAxisAlignment.SpaceAround,
            crossAxisAlignment = FlowCrossAxisAlignment.Center,
            lastLineMainAxisAlignment = MainAxisAlignment.Start,
            modifier = Modifier.fillMaxWidth()
        ){
            val selectedList = when(selectedContacts){
                is SelectedContacts.GroupSelection -> {
                    selectedContacts.selectedContacts
                }
                SelectedContacts.None -> {
                    listOf()
                }
                is SelectedContacts.SingleSelection -> {
                    listOf(selectedContacts.selectedContact)
                }
            }
            topContacts.onEach {
                TopContactItem(it,selectedList.mapNotNull { selectedContact ->
                    selectedContact.contact }.contains(it), onContactClicked)
            }

        }
    }
}
