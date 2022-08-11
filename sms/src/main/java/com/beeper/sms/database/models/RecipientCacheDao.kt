package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface RecipientCacheDao {
    @Query("SELECT * FROM recipientcache")
    fun getAll(): List<RecipientCache>

    @Query("SELECT * FROM recipientcache WHERE recipient_id = :recipientId")
    fun getContact(recipientId: Long): RecipientCache?

    @Query("SELECT * FROM recipientcache LIMIT :limit")
    fun getBatch(limit: Int): List<RecipientCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(recipient: RecipientCache)

    @Delete
    fun delete(recipient: RecipientCache)

    @Query("DELETE FROM RecipientCache")
    fun clear()
}