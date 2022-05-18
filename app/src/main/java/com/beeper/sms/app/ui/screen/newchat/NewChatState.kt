package com.beeper.sms.app.ui.screen.newchat

import com.beeper.sms.app.ui.model.UIContact

sealed class NewChatState {
    object Loading : NewChatState()
    object Error : NewChatState()
    data class Loaded(
        val contacts: Map<Char, List<UIContact>>,
        val topContacts: List<UIContact>,
        val filteredContacts: List<UIContact>,
        val showFilterContacts: Boolean = false
    ) : NewChatState()
}

sealed class KeyboardMode {
    object Default : KeyboardMode()
    object Dial : KeyboardMode()
}

sealed class SelectionMode {
    object Single : SelectionMode()
    object Group : SelectionMode()
}

data class SelectedContact(
    val phoneNumber: String,
    val contact: UIContact? = null
)

sealed class SelectedContacts {
    data class SingleSelection(val selectedContact: SelectedContact) : SelectedContacts()
    data class GroupSelection(val selectedContacts: Set<SelectedContact>) : SelectedContacts()
    object None : SelectedContacts()
}

sealed class CustomContactState {
    object Hidden : CustomContactState()
    data class Showing(val number: String) : CustomContactState()
}

