package com.beeper.sms.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.beeper.sms.database.models.*

@Database(entities = [
    BridgedChatThread::class,
    BridgedMessage::class, BridgedReadReceipt::class],
    version = 2, autoMigrations = [
        AutoMigration (from = 1, to = 2)
    ])
abstract class BridgedEntitiesDatabase : RoomDatabase() {
    abstract fun bridgedChatThreadDao(): BridgedChatThreadDao
    abstract fun bridgedMessageDao(): BridgedMessageDao
    abstract fun bridgedReadReceiptDao(): BridgedReadReceiptDao
}
