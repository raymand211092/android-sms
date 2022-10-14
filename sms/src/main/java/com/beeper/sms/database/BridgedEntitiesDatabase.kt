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
    RecipientCache::class, PendingRecipientUpdate::class, ContactInfoCache::class,
    PendingSendResponse::class, SmsThreadMatrixRoomRelation::class],
    version = 10, autoMigrations = [
        AutoMigration (from = 1, to = 2),
        AutoMigration (from = 2, to = 3),
        AutoMigration (from = 3, to = 4),
        AutoMigration (from = 4, to = 5, spec = BridgedEntitiesDatabase.ContactCacheMigration::class),
        AutoMigration (from = 5, to = 6),
        AutoMigration (from = 6, to = 7),
        AutoMigration (from = 7, to = 8),
        AutoMigration (from = 8, to = 9),
        AutoMigration (from = 9, to = 10)
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
    abstract fun pendingSendResponseDao(): PendingSendResponseDao
    abstract fun nativeThreadMatrixRoomRelationDao(): SmsThreadMatrixRoomRelationDao

}

