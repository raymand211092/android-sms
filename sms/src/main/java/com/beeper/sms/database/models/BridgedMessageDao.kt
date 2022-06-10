package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface BridgedMessageDao {
    @Query("SELECT * FROM bridgedmessage")
    fun getAll(): List<BridgedMessage>

    @Query("SELECT EXISTS(SELECT message_id FROM bridgedmessage " +
            "WHERE message_id = :message_id)")
    fun isBridged(
        message_id : Long
    ): Boolean


    @Query("SELECT DISTINCT chat_guid FROM bridgedmessage")
    fun getBridgedChats(): List<String>?

    @Query("SELECT MAX(message_id) FROM bridgedmessage WHERE is_mms = 0")
    fun getLastBridgedSmsId(): Long?

    @Query("SELECT MAX(message_id) FROM bridgedmessage WHERE is_mms = 1")
    fun getLastBridgedMmsId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bridgedMessage: BridgedMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(bridgedMessage: List<BridgedMessage>)

    @Delete
    fun delete(bridgedMessage: BridgedMessage)

    @Query("DELETE FROM bridgedmessage")
    fun clear()
}