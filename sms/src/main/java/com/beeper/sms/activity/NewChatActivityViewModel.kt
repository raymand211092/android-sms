package com.beeper.sms.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.beeper.sms.Bridge
import com.beeper.sms.commands.Command
import com.beeper.sms.commands.outgoing.Chat
import com.beeper.sms.provider.ContactProvider
import com.beeper.sms.provider.ContactRow
import com.beeper.sms.provider.ThreadProvider.Companion.chatGuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

class NewChatActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val contactProvider = ContactProvider(application.applicationContext)
    val contacts = MutableLiveData(emptyList<ContactRow>())
    var text = MutableLiveData("")
    var dialpad = MutableLiveData(false)

    fun searchContacts(text: String) {
        this.text.value = text
        viewModelScope.launch(Dispatchers.IO) {
            contacts.postValue(contactProvider.searchContacts(text))
        }
    }

    fun createChatRoom(phoneNumber: String): Job {
        return viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            val numbers = listOfNotNull(phoneNumber)
            val room =
                contactProvider
                    .getContacts(numbers)
                    .map { contact -> contact.nickname }
                    .joinToString()
            Bridge.INSTANCE.send(
                Command(
                    command = "chat",
                    data = Chat(chat_guid = numbers.chatGuid, title = room, members = numbers)
                )
            )
        }
    }

    fun toggleDialpad() {
        dialpad.value = dialpad.value?.not()
    }

    init {
        searchContacts("")
    }
}