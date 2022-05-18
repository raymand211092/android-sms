package com.beeper.sms.app.ui.screen.conversation

import com.beeper.sms.database.models.ChatThread
import com.beeper.sms.commands.outgoing.Message

sealed class ConversationState{
    object Loading : ConversationState()
    data class Loaded(val thread : ChatThread, val messages : List<Message>) : ConversationState()
    object Error : ConversationState()
}