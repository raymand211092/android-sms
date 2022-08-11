package com.beeper.sms.app.ui.screen.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.beeper.sms.app.ui.model.UIContact
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.GuidProvider.Companion.normalizeAddress
import com.beeper.sms.provider.ChatThreadProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class NewChatViewModel(
    private val contactProvider: ContactProvider,
    private val threadProvider: ChatThreadProvider
) : ViewModel() {

    val newChatState = MutableStateFlow<NewChatState>(NewChatState.Loading)
    private val contacts = MutableStateFlow<List<UIContact>>(listOf())
    private val topContacts = MutableStateFlow<List<UIContact>>(listOf())

    private val filterText_ = MutableStateFlow(String())
    val filterText = filterText_.asStateFlow()

    private val keyboardMode_ = MutableStateFlow<KeyboardMode>(KeyboardMode.Default)
    val keyboardMode = keyboardMode_.asStateFlow()

    private val selectionMode_ = MutableStateFlow<SelectionMode>(SelectionMode.Single)
    val selectionMode = selectionMode_.asStateFlow()

    private val selectedContacts_ = MutableStateFlow<SelectedContacts>(SelectedContacts.None)
    val selectedContacts = selectedContacts_.asStateFlow()

    private val customContactState_ =
        MutableStateFlow<CustomContactState>(CustomContactState.Hidden)
    val customContactState = customContactState_.asStateFlow()

    init {
        viewModelScope.launch {
            loadContacts()
        }
        subscribeToFilterText()
    }

    private fun subscribeToFilterText() {
        filterText.onEach { newFilterText ->
            filterContacts(contacts.value, newFilterText)
            if (newFilterText.matches(android.util.Patterns.PHONE.toRegex())) {
                customContactState_.value = CustomContactState.Showing(newFilterText)
            } else {
                customContactState_.value = CustomContactState.Hidden
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun loadContacts() {
        contacts.value = contactProvider.fetchContacts().map {
            UIContact(
                it.contactId.toString(),
                it.name,
                it.starred,
                it.avatarUri,
                it.phoneNumbers.toSet(),
            )
        }

        loadTopContacts()

        newChatState.value = NewChatState.Loaded(
            contacts.value.map {
                it.copy(name = it.name.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                })
            }.sortedBy {
                it.name
            }.groupBy {
                if (it.name[0].isLetter()) {
                    it.name[0]
                } else {
                    '#'
                }
            },
            topContacts.value,
            listOf()
        )
    }

    private fun loadTopContacts() {
        topContacts.value = contacts.value.filter {
            it.favorite
        }
    }

    private suspend fun filterContacts(contacts: List<UIContact>, text: String) {
        withContext(Dispatchers.Default) {

            val newChatStateValue = newChatState.value
            if (newChatStateValue is NewChatState.Loaded) {
                val currentSelectedContacts = when (val current = selectedContacts.value) {
                    is SelectedContacts.GroupSelection -> current.selectedContacts
                    is SelectedContacts.SingleSelection -> listOf(current.selectedContact)
                    SelectedContacts.None -> listOf()
                }

                val filteredContacts = contacts.filter { it ->
                    val searchableString = it.name.lowercase() +
                            it.phoneNumbers.reduce { acc, s -> acc + s } + it.email
                    searchableString.contains(text.lowercase()) && !currentSelectedContacts.mapNotNull {
                        it.contact
                    }.contains(it)
                }
                if (filteredContacts.isNotEmpty() && text.isNotBlank()) {
                    newChatState.value = newChatStateValue.copy(
                        filteredContacts = filteredContacts, showFilterContacts = true
                    )
                } else {
                    newChatState.value = newChatStateValue.copy(
                        filteredContacts = listOf(), showFilterContacts = false
                    )
                }
            }
        }
    }

    fun onTextChanged(text: String) {
        filterText_.value = text
    }

    fun onKeyboardModeChanged() {
        when (keyboardMode_.value) {
            KeyboardMode.Default -> {
                keyboardMode_.value = KeyboardMode.Dial
            }
            KeyboardMode.Dial -> {
                keyboardMode_.value = KeyboardMode.Default
            }
        }
    }

    fun onGroupModeClicked() {
        selectionMode_.value = SelectionMode.Group
    }

    fun onEmptyBackspacePressed() {
        when (val currentSelectedContacts = selectedContacts.value) {
            is SelectedContacts.GroupSelection -> {
                val contacts = currentSelectedContacts.selectedContacts
                if (contacts.isEmpty()) {
                    return
                }
                selectedContacts_.value =
                    SelectedContacts.GroupSelection(contacts.minus(contacts.last()))
            }
            SelectedContacts.None -> {
                return
            }
            is SelectedContacts.SingleSelection -> {
                selectedContacts_.value = SelectedContacts.None
            }
        }
    }

    private fun removeSelectionIfExists(selectedContact: SelectedContact): Boolean {
        return when (val currentSelection = selectedContacts.value) {
            is SelectedContacts.GroupSelection -> {
                if (currentSelection.selectedContacts.contains(selectedContact)) {
                    selectedContacts_.value = SelectedContacts.GroupSelection(
                        currentSelection.selectedContacts.minus(selectedContact).toSet()
                    )
                    true
                } else {
                    false
                }
            }
            is SelectedContacts.SingleSelection -> {
                if (currentSelection.selectedContact == selectedContact) {
                    selectedContacts_.value = SelectedContacts.None
                    true
                } else {
                    false
                }
            }
            SelectedContacts.None -> {
                false
            }
        }
    }

    fun onContactSelected(selectedContact: SelectedContact) {
        val removed = removeSelectionIfExists(selectedContact)
        if (!removed) {
            if (selectionMode.value is SelectionMode.Single) {
                selectedContacts_.value = SelectedContacts.SingleSelection(selectedContact)
            } else {
                when (val currentSelection = selectedContacts_.value) {
                    is SelectedContacts.GroupSelection -> {
                        filterText_.value = String()
                        selectedContacts_.value =
                            currentSelection.copy(
                                currentSelection.selectedContacts.plus(
                                    selectedContact
                                )
                            )
                    }
                    SelectedContacts.None -> {
                        filterText_.value = String()
                        selectedContacts_.value =
                            SelectedContacts.GroupSelection(setOf(selectedContact))
                    }
                    is SelectedContacts.SingleSelection -> {
                        filterText_.value = String()
                        selectedContacts_.value = SelectedContacts.GroupSelection(
                            setOf(
                                currentSelection.selectedContact,
                                selectedContact
                            )
                        )
                    }
                }
            }
            clearText()
        }
    }

    fun shouldNavigateAfterContactSelected(): String? {
        val mode = selectionMode.value
        val selected = selectedContacts.value
        if (mode is SelectionMode.Single &&
            selected is SelectedContacts.SingleSelection
        ) {
            return selected.selectedContact.phoneNumber
        }
        return null
    }

    fun shouldNavigateAfterGroupSelected(): Set<String> {
        val mode = selectionMode.value
        val selected = selectedContacts.value
        if (mode is SelectionMode.Group &&
            selected is SelectedContacts.GroupSelection
        ) {
            return selected.selectedContacts.map {
                it.phoneNumber
            }.toSet()
        }
        return setOf()
    }

    private fun clearText() {
        filterText_.value = String()
    }

    fun onBackPressed(): Boolean {
        if (selectionMode.value is SelectionMode.Group) {
            selectionMode_.value = SelectionMode.Single
            selectedContacts_.value = SelectedContacts.None
            return true
        }
        return false
    }

    fun getThreadId(recipients: Set<String>): Long {
        return threadProvider.getOrCreateThreadId(recipients.map{
            normalizeAddress(it)
        }.toSet())
    }

    class Factory(
        private val contactProvider: ContactProvider,
        private val threadProvider: ChatThreadProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return NewChatViewModel(contactProvider, threadProvider) as T
        }
    }

}
