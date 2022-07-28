package com.beeper.sms.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.beeper.sms.database.models.*

@Database(entities = [
    BridgedChatThread::class, BridgedMessage::class, BridgedReadReceipt::class,
    PendingReadReceipt::class, InboxPreviewCache::class, ChatThreadMetadataCache::class,
    ContactCache::class, PendingContactUpdate::class],
    version = 4, autoMigrations = [
        AutoMigration (from = 1, to = 2),
        AutoMigration (from = 2, to = 3),
        AutoMigration (from = 3, to = 4),
    ],
    exportSchema = true
)
abstract class BridgedEntitiesDatabase : RoomDatabase() {
    abstract fun bridgedChatThreadDao(): BridgedChatThreadDao
    abstract fun bridgedMessageDao(): BridgedMessageDao
    abstract fun bridgedReadReceiptDao(): BridgedReadReceiptDao
    abstract fun pendingReadReceiptDao(): PendingReadReceiptDao
    abstract fun inboxPreviewCacheDao(): InboxPreviewCacheDao
    abstract fun chatThreadMetadataCache(): ChatThreadMetadataCacheDao
    abstract fun contactCacheDao(): ContactCacheDao
    abstract fun pendingContactUpdateDao(): PendingContactUpdateDao
}

