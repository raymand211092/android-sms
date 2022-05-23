package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface BridgedChatThreadDao {
    @Query("SELECT * FROM bridgedchatthread")
    fun getAll(): List<BridgedChatThread>

    @Query("SELECT MAX(chat_guid) FROM bridgedchatthread")
    fun getLastBridgedChatThreadId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chatThread: BridgedChatThread)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg chatThreads: BridgedChatThread)

    @Delete
    fun delete(chatThreads: BridgedChatThread)

    @Query("DELETE FROM bridgedchatthread")
    fun clear()
}