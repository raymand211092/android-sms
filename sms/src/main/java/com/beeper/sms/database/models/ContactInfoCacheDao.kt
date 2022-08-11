package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface ContactInfoCacheDao {
    @Query("SELECT * FROM contactinfocache")
    fun getAll(): List<ContactInfoCache>

    @Query("SELECT * FROM contactinfocache WHERE contact_id = :contactId")
    fun getContact(contactId: Long): ContactInfoCache?

    @Query("SELECT * FROM contactinfocache LIMIT :limit")
    fun getBatch(limit: Int): List<ContactInfoCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(contactInfo: ContactInfoCache)

    @Delete
    fun delete(contactInfo: ContactInfoCache)

    @Query("DELETE FROM ContactInfoCache")
    fun clear()
}