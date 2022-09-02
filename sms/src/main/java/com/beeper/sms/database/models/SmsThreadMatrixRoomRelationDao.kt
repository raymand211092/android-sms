package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface SmsThreadMatrixRoomRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(relation: SmsThreadMatrixRoomRelation)

    @Query("SELECT * FROM smsthreadmatrixroomrelation")
    fun getAll(): List<SmsThreadMatrixRoomRelation>

    @Query("SELECT * FROM smsthreadmatrixroomrelation WHERE room_id = :roomId")
    fun getRelationsForRoom(roomId: String): List<SmsThreadMatrixRoomRelation>

    @Query("SELECT * FROM smsthreadmatrixroomrelation WHERE thread_id = :threadId")
    fun getRelationsForThread(threadId: Long): List<SmsThreadMatrixRoomRelation>

    @Delete
    fun delete(relation: SmsThreadMatrixRoomRelation)

    @Query("DELETE FROM SmsThreadMatrixRoomRelation")
    fun clear()
}
