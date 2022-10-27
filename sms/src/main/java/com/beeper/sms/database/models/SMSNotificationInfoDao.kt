package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface SMSNotificationInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(smsNotificationInfo: SMSNotificationInfo)

    @Query("SELECT * FROM smsnotificationinfo")
    fun getAll(): List<SMSNotificationInfo>

    @Delete
    fun delete(smsNotificationInfo: SMSNotificationInfo)

    @Query("DELETE FROM smsnotificationinfo")
    fun clear()
}
