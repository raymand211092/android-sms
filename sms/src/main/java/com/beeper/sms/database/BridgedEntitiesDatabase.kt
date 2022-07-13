package com.beeper.sms.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.beeper.sms.database.models.*

@Database(entities = [
    BridgedChatThread::class,
    BridgedMessage::class, BridgedReadReceipt::class, PendingReadReceipt::class],
    version = 3, autoMigrations = [
        AutoMigration (from = 1, to = 2),
        AutoMigration (from = 2, to = 3),
    ],
    exportSchema = true
)
abstract class BridgedEntitiesDatabase : RoomDatabase() {
    abstract fun bridgedChatThreadDao(): BridgedChatThreadDao
    abstract fun bridgedMessageDao(): BridgedMessageDao
    abstract fun bridgedReadReceiptDao(): BridgedReadReceiptDao
    abstract fun pendingReadReceiptDao(): PendingReadReceiptDao
}
