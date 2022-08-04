package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface PendingContactUpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pendingContactUpdate: PendingContactUpdate)

    @Query("SELECT * FROM pendingcontactupdate")
    fun getAll(): List<PendingContactUpdate>

    @Delete
    fun delete(pendingContactUpdate: PendingContactUpdate)

    @Query("DELETE FROM pendingcontactupdate")
    fun clear()
}
