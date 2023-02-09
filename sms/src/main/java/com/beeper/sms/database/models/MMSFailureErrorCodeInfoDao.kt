package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface MMSFailureErrorCodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(mmsFailureErrorCode: MMSFailureErrorCode)

    @Query("SELECT * FROM mmsfailureerrorcode")
    fun getAll(): List<MMSFailureErrorCode>

    @Query("SELECT * FROM mmsfailureerrorcode WHERE msg_guid = :msg_guid")
    fun getByMessageId(msg_guid : String): MMSFailureErrorCode?

    @Query("DELETE FROM mmsfailureerrorcode")
    fun clear()
}
