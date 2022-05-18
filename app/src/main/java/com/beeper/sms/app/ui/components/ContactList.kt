package com.beeper.sms.app.ui.components
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeper.sms.app.ui.model.UIContact
import com.beeper.sms.app.ui.screen.newchat.SelectedContacts
import com.beeper.sms.app.ui.screen.newchat.SelectionMode
import com.beeper.sms.app.ui.theme.getTextTitleColor
import com.beeper.sms.app.ui.theme.getToolbarBackgroundColor

@ExperimentalFoundationApi
@Composable
fun ContactList(
    topContacts: List<UIContact>,
    contactGroups: Map<Char, List<UIContact>>,
    selectionMode: SelectionMode,
    selectedContacts: SelectedContacts,
    onContactClicked: (UIContact) -> Unit,
    onGroupModeClicked: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ){
        if(selectionMode is SelectionMode.Single) {
            item {
                CreateGroup(onGroupModeClicked)
            }
        }
        if(topContacts.isNotEmpty()) {
            item {
                TopContactList(topContacts, selectedContacts, onContactClicked)
            }
        }
        contactGroups.onEach {
            stickyHeader {
                Box(modifier = Modifier
                    .background(
                        color = getToolbarBackgroundColor(),
                        shape = RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 48.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 48.dp
                        )
                    )
                    .padding(start = 16.dp)
                    .height(48.dp)
                    .width(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = it.key.toString(),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier,
                        color = getTextTitleColor()
                    )
                }
            }
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
            items(items = it.value,key= {item -> item.id}){
                    contact ->
                ContactItem(contact, selectedList.mapNotNull { selectedContact ->
                    selectedContact.contact }.contains(contact), onContactClicked)
            }

        }
    }
}
