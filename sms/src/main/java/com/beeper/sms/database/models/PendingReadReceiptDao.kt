package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface PendingReadReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pendingReadReceipt: PendingReadReceipt)

    @Query("SELECT * FROM PendingReadReceipt")
    fun getAll(): List<PendingReadReceipt>

    @Delete
    fun delete(pendingReadReceipt: PendingReadReceipt)

    @Query("DELETE FROM pendingreadreceipt")
    fun clear()
}
