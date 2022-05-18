package com.beeper.sms.app.ui.screen.conversation

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.beeper.sms.SmsMmsSender
import com.beeper.sms.provider.MessageProvider
import com.beeper.sms.provider.ChatThreadProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



class ConversationViewModel(
    private val threadId: String,
    private val threadProvider: ChatThreadProvider,
    private val messageProvider: MessageProvider,
    private val smsMmsSender: SmsMmsSender,
    ) : ViewModel() {

    private val state_ = MutableStateFlow<ConversationState>(ConversationState.Loading)
    val state = state_.asStateFlow()

    init{
        viewModelScope.launch {
            loadMessages()
        }
    }

    private suspend fun loadMessages(){
        withContext(Dispatchers.IO) {
            val messages = messageProvider.getRecentMessages(threadId.toLong(), 100)
            val thread = threadProvider.getThread(threadId.toLong())
            if(thread != null){
                state_.value =
                    ConversationState.Loaded(thread,messages)
            }else{
                state_.value = ConversationState.Error
            }
        }
    }

    suspend fun sendMessage(text:String){
        return withContext(Dispatchers.IO) {
            val currentState = state.value
            if(currentState is ConversationState.Loaded) {
                val thread = currentState.thread
                smsMmsSender.sendMessage(
                    text,
                    thread.members.values.mapNotNull {
                        it.phoneNumber
                    },
                    threadId.toLong()
                )
            }
        }
    }

    suspend fun sendMessage(fileUri: Uri) : Boolean{
        return withContext(Dispatchers.IO) {
                val currentState = state.value
                if (currentState is ConversationState.Loaded) {
                    val thread = currentState.thread
                    smsMmsSender.sendMessage(
                        String(),
                        thread.members.values.mapNotNull {
                            it.phoneNumber
                        },
                        fileUri,
                        threadId.toLong(), Bundle()
                    )
                }else{
                    false
                }
            }
    }

    class Factory(
        private val threadId: String,
        private val threadProvider: ChatThreadProvider,
        private val messageProvider: MessageProvider,
        private val smsMmsSender: SmsMmsSender,
        ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return ConversationViewModel(threadId,threadProvider,messageProvider,smsMmsSender) as T
        }
    }

}
