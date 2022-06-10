package com.beeper.sms.database.models

import androidx.room.*

@Dao
interface BridgedReadReceiptDao {
    @Query("SELECT * FROM bridgedreadreceipt")
    fun getAll(): List<BridgedReadReceipt>

    @Query("SELECT * FROM bridgedreadreceipt WHERE chat_guid = :chat_guid")
    fun getLastBridgedMessage(chat_guid : String): BridgedReadReceipt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bridgedReadReceipt: BridgedReadReceipt)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(bridgedReadReceipt: BridgedReadReceipt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(bridgedMessage: List<BridgedReadReceipt>)

    @Delete
    fun delete(bridgedReadReceipt: BridgedReadReceipt)

    @Query("DELETE FROM bridgedreadreceipt")
    fun clear()
}