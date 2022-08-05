package com.beeper.sms.work.startstop

sealed class SyncWindowState{
    object Running : SyncWindowState()

    object Stopping : SyncWindowState()

    object Stopped : SyncWindowState()
}