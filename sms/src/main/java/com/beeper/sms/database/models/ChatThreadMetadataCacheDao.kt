package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface ChatThreadMetadataCacheDao {
    @Query("SELECT * FROM chatthreadmetadatacache")
    fun getAll(): List<ChatThreadMetadataCache>

    @Query("SELECT * FROM chatthreadmetadatacache WHERE chat_guid = :chat_guid")
    fun getChatThreadMetadataCache(chat_guid: String): ChatThreadMetadataCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(ChatThreadMetadataCache: ChatThreadMetadataCache)

    @Delete
    fun delete(ChatThreadMetadataCache: ChatThreadMetadataCache)

    @Query("DELETE FROM chatthreadmetadatacache")
    fun clear()
}
