package com.tradna.APP.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface NormalizedTradeActivityDao {

    @Query(
        """
        SELECT * FROM normalized_trade_activities
        ORDER BY activityDate DESC, id ASC
        """
    )
    suspend fun loadAll(): List<NormalizedTradeActivityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(
        activities: List<NormalizedTradeActivityEntity>
    ): List<Long>

    @Query("SELECT COUNT(*) FROM normalized_trade_activities")
    suspend fun count(): Int

    @Query("DELETE FROM normalized_trade_activities")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertImportState(state: ImportStateEntity)

    @Query("SELECT * FROM import_state WHERE storageKey = :storageKey LIMIT 1")
    suspend fun loadImportState(storageKey: String): ImportStateEntity?
}
