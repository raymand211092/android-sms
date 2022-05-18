package com.beeper.sms.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.beeper.sms.database.models.BridgedChatThread
import com.beeper.sms.database.models.BridgedChatThreadDao
import com.beeper.sms.database.models.BridgedMessage
import com.beeper.sms.database.models.BridgedMessageDao

@Database(entities = [BridgedChatThread::class, BridgedMessage::class], version = 1)
abstract class BridgedEntitiesDatabase : RoomDatabase() {
    abstract fun bridgedChatThreadDao(): BridgedChatThreadDao
    abstract fun bridgedMessageDao(): BridgedMessageDao
}