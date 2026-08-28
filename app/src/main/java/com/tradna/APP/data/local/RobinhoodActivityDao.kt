package com.tradna.APP.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RobinhoodActivityDao {
    @Query(
        """
        SELECT * FROM robinhood_activities
        ORDER BY activitySortKey DESC, occurrenceIndex ASC, id ASC
        """
    )
    suspend fun loadAll(): List<RobinhoodActivityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(
        activities: List<RobinhoodActivityEntity>
    ): List<Long>

    @Query("SELECT COUNT(*) FROM robinhood_activities")
    suspend fun count(): Int
}
