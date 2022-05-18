package com.beeper.sms.app.ui.components

sealed class AvatarState {
    object Loading : AvatarState()
    object Loaded : AvatarState()
    object Error : AvatarState()

}