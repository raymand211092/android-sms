package com.beeper.sms.app.ui.screen.inbox

import com.beeper.sms.database.models.ChatThread

sealed class InboxState{
    object Loading : InboxState()
    data class Loaded(val chats : List<ChatThread>) : InboxState()
    object Error : InboxState()
}