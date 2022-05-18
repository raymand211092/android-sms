package com.beeper.sms.app.ui.components
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.beeper.sms.app.ui.model.UIContact


@Composable
fun ContactFilteredList(filteredContacts: List<UIContact>, onContactClicked: (UIContact) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ){
        items(items = filteredContacts,key= {item -> item.id}){
                contact ->
            ContactItem(contact, false, onContactClicked)
        }
    }
}