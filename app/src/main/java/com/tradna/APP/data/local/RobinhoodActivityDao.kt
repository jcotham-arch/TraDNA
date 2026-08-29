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
        ORDER BY activitySortKey DESC, sourceOrder ASC
        """
    )
    suspend fun loadAll(): List<RobinhoodActivityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(
        activities: List<RobinhoodActivityEntity>
    ): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(
        activities: List<RobinhoodActivityEntity>
    )

    @Query("SELECT COUNT(*) FROM robinhood_activities")
    suspend fun count(): Int
}
