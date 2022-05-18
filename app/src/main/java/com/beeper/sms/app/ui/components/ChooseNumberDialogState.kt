package com.beeper.sms.app.ui.components

import com.beeper.sms.app.ui.model.UIContact

sealed class ChooseNumberDialogState{
    object Closed : ChooseNumberDialogState()
    data class Open(val contact: UIContact) : ChooseNumberDialogState()
}