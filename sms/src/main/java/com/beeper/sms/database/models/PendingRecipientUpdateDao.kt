package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface PendingRecipientUpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pendingRecipientUpdate: PendingRecipientUpdate)

    @Query("SELECT * FROM pendingrecipientupdate")
    fun getAll(): List<PendingRecipientUpdate>

    @Delete
    fun delete(pendingRecipientUpdate: PendingRecipientUpdate)

    @Query("DELETE FROM pendingrecipientupdate")
    fun clear()
}
