package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface ContactCacheDao {
    @Query("SELECT * FROM contactcache")
    fun getAll(): List<ContactCache>

    @Query("SELECT * FROM contactcache WHERE canonical_address_id = :canonicalAddressId")
    fun getContact(canonicalAddressId: Long): ContactCache?

    @Query("SELECT * FROM contactcache LIMIT :limit")
    fun getBatch(limit: Int): List<ContactCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(inboxPreview: ContactCache)

    @Delete
    fun delete(inboxPreview: ContactCache)

    @Query("DELETE FROM ContactCache")
    fun clear()
}