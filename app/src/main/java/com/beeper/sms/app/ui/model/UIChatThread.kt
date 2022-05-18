package com.beeper.sms.app.ui.model

import androidx.compose.ui.graphics.Color

data class UIChatThread(
        val id: String,
        val name: String,
        val lastMessage: CharSequence,
        val timestamp: CharSequence,
        val indicatorColor: Color,
        val firstLetter: String,
        val avatarUrl: String?,
        val isRead: Boolean,
)
