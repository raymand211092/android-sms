package com.beeper.sms.database.models

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InfiniteBackfillChatEntryDao {
    @Query("SELECT * FROM infinitebackfillchatentry order by newest_message_date desc")
    fun getAll(): List<InfiniteBackfillChatEntry>

    @Query("SELECT * FROM infinitebackfillchatentry")
    fun getStream(): Flow<List<InfiniteBackfillChatEntry>>

    @Query("SELECT * FROM infinitebackfillchatentry where backfill_finished = 0 order by newest_message_date desc")
    fun getPending(): List<InfiniteBackfillChatEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(infiniteBackfillChatEntry: InfiniteBackfillChatEntry)

    @Delete
    fun delete(infiniteBackfillChatEntry: InfiniteBackfillChatEntry)

    @Query("DELETE FROM infinitebackfillchatentry")
    fun clear()
}