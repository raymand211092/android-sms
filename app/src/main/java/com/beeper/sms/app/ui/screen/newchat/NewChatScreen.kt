package com.beeper.sms.app.ui.screen.newchat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.beeper.sms.app.ui.components.*
import com.beeper.sms.app.ui.components.ChooseNumberDialogState


@ExperimentalComposeUiApi
@ExperimentalFoundationApi
@Composable
fun NewChatScreen(
    newChatState : NewChatState,
    text: String,
    keyboardMode: KeyboardMode,
    selectionMode: SelectionMode,
    selectedContacts: SelectedContacts,
    customContactState: CustomContactState,
    onTextChanged: (String) -> Unit,
    onContactSelected: (SelectedContact) -> Unit,
    onKeyboardModeClicked: () -> Unit,
    onGroupModeClicked: () -> Unit,
    onEmptyBackspacePressed : ()->Unit,
    onBackButtonClicked: () ->Unit,
    onNextClicked: () -> Unit
) {

    val chooseNumberDialogState = remember {
        mutableStateOf<ChooseNumberDialogState>(ChooseNumberDialogState.Closed) }

    ChooseNumberDialog(chooseNumberDialogState.value, {
        chooseNumberDialogState.value = ChooseNumberDialogState.Closed
    }) { number, contact ->
        onContactSelected(SelectedContact(number,contact))
        chooseNumberDialogState.value = ChooseNumberDialogState.Closed
    }


    Column(Modifier.fillMaxSize()) {
        val title = when(selectionMode){
            SelectionMode.Group -> "New group chat"
            SelectionMode.Single -> "New chat"
        }

        NewChatScreenAppBar(title,selectionMode, onBackButtonClicked, onNextClicked)

        when(newChatState){
            NewChatState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                    Text("Error loading contacts")
                }
            }
            NewChatState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                    CircularProgressIndicator()
                }
            }
            is NewChatState.Loaded -> {
                RecipientChooser(text,
                    keyboardMode,
                    selectedContacts,
                    customContactState,
                    onTextChanged,
                    onKeyboardModeClicked,
                    onEmptyBackspacePressed,
                    onCustomNumberSelected = {
                        onContactSelected(SelectedContact(it))
                    })

                Box(Modifier.weight(1f,true)) {
                    if(newChatState.showFilterContacts) {
                        ContactFilteredList(
                            newChatState.filteredContacts,
                            onContactClicked =  {
                                    contact ->
                                if (contact.phoneNumbers.size > 1) {
                                    chooseNumberDialogState.value =
                                        ChooseNumberDialogState.Open(contact)
                                }else{
                                    val number = contact.phoneNumbers.firstOrNull()
                                    if(number!=null){
                                        onContactSelected(SelectedContact(number,contact))
                                    }
                                }
                            }
                        )
                    }else {
                        ContactList(
                            newChatState.topContacts,
                            newChatState.contacts,
                            selectionMode,
                            selectedContacts,
                            onContactClicked = {
                                    contact ->
                                if (contact.phoneNumbers.size > 1) {
                                    chooseNumberDialogState.value =
                                        ChooseNumberDialogState.Open(contact)
                                }else{
                                    val number = contact.phoneNumbers.firstOrNull()
                                    if(number!=null){
                                        onContactSelected(SelectedContact(number,contact))
                                    }
                                }
                            },
                            onGroupModeClicked
                        )
                    }
                }
            }
        }
    }
}






