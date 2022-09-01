package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface InboxPreviewCacheDao {
    @Query("SELECT * FROM inboxpreviewcache")
    fun getAll(): List<InboxPreviewCache>

    @Query("SELECT * FROM inboxpreviewcache LIMIT :limit")
    fun getFirst(limit: Int): List<InboxPreviewCache>

    @Query("SELECT * FROM inboxpreviewcache WHERE timestamp > :timestamp")
    fun getChatsAfter(timestamp: Long): List<InboxPreviewCache>

    @Query("SELECT * FROM inboxpreviewcache WHERE timestamp < :timestamp  LIMIT :limit")
    fun getChatsBefore(timestamp: Long, limit: Int): List<InboxPreviewCache>

    @Query("SELECT * FROM inboxpreviewcache WHERE chat_guid = :chat_guid")
    fun getPreviewForChat(chat_guid: String): InboxPreviewCache?

    @Query("SELECT * FROM inboxpreviewcache WHERE message_guid = :message_guid")
    fun getPreviewForMessage(message_guid: String): InboxPreviewCache?

    @Query("SELECT * FROM inboxpreviewcache WHERE thread_id = :threadId")
    fun getPreviewForChatByThreadId(threadId: Long): InboxPreviewCache?

    @Query("SELECT * FROM inboxpreviewcache WHERE chat_guid = :chatGuid")
    fun getPreviewForChatByChatGuid(chatGuid: String): InboxPreviewCache?

    @Query("UPDATE inboxpreviewcache SET is_read=1 WHERE chat_guid = :chat_guid")
    fun markPreviewAsRead(chat_guid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(inboxPreview: InboxPreviewCache)

    @Delete
    fun delete(inboxPreview: InboxPreviewCache)

    @Query("DELETE FROM inboxpreviewcache")
    fun clear()
}