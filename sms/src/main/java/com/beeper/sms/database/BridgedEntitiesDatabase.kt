package com.beeper.sms.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.beeper.sms.database.models.*

@Database(entities = [
    BridgedChatThread::class, BridgedMessage::class, BridgedReadReceipt::class,
    PendingReadReceipt::class, InboxPreviewCache::class, ChatThreadMetadataCache::class,
    RecipientCache::class, PendingRecipientUpdate::class, ContactInfoCache::class],
    version = 5, autoMigrations = [
        AutoMigration (from = 1, to = 2),
        AutoMigration (from = 2, to = 3),
        AutoMigration (from = 3, to = 4),
        AutoMigration (from = 4, to = 5, spec = BridgedEntitiesDatabase.ContactCacheMigration::class),
    ],
    exportSchema = true
)
abstract class BridgedEntitiesDatabase : RoomDatabase() {
    @DeleteTable.Entries(DeleteTable(tableName = "ContactCache"),
        DeleteTable(tableName = "PendingContactUpdate"))
    class ContactCacheMigration : AutoMigrationSpec
    abstract fun bridgedChatThreadDao(): BridgedChatThreadDao
    abstract fun bridgedMessageDao(): BridgedMessageDao
    abstract fun bridgedReadReceiptDao(): BridgedReadReceiptDao
    abstract fun pendingReadReceiptDao(): PendingReadReceiptDao
    abstract fun inboxPreviewCacheDao(): InboxPreviewCacheDao
    abstract fun chatThreadMetadataCache(): ChatThreadMetadataCacheDao
    abstract fun recipientCacheDao(): RecipientCacheDao
    abstract fun pendingRecipientUpdateDao(): PendingRecipientUpdateDao
    abstract fun contactInfoCacheDao(): ContactInfoCacheDao

}

