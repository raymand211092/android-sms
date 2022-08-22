package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface PendingSendResponseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pendingSendResponse: PendingSendResponse)

    @Query("SELECT * FROM pendingsendresponse")
    fun getAll(): List<PendingSendResponse>

    @Delete
    fun delete(pendingSendResponse: PendingSendResponse)

    @Query("DELETE FROM pendingsendresponse")
    fun clear()
}
