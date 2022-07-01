package com.beeper.sms.app.ui.screen.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.beeper.sms.provider.ChatThreadProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch




class InboxViewModel(private val threadProvider: ChatThreadProvider) : ViewModel() {
    private val state_ = MutableStateFlow<InboxState>(InboxState.Loading)
    val state = state_.asStateFlow()

    init{
        viewModelScope.launch {
            loadThreads()
        }
    }

    private suspend fun loadThreads(){
        state_.value = InboxState.Loaded(threadProvider.fetchThreads(15))
    }

    class Factory(private val threadProvider: ChatThreadProvider) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return InboxViewModel(threadProvider) as T
        }
    }
}
