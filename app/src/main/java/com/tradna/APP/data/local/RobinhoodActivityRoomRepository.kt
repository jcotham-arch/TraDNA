package com.tradna.APP.data.local

import androidx.room.withTransaction
import com.tradna.APP.data.ImportMergeResult
import com.tradna.APP.data.RobinhoodActivity
import com.tradna.APP.data.TradingPlatformSource

class RobinhoodActivityRoomRepository(
    private val database: TraDnaDatabase
) {
    private val activityDao = database.robinhoodActivityDao()
    private val stateDao = database.normalizedTradeActivityDao()

    suspend fun loadActivities(): List<RobinhoodActivity> =
        activityDao.loadAll().map(RobinhoodActivityMapper::toDomain)

    suspend fun mergeActivities(
        incomingActivities: List<RobinhoodActivity>,
        fileName: String,
        importedAtEpochMillis: Long = System.currentTimeMillis()
    ): ImportMergeResult = database.withTransaction {
        val beforeCount = activityDao.count()
        activityDao.replaceAll(
            RobinhoodActivityMapper.toEntities(incomingActivities)
        )
        val afterCount = activityDao.count()
        val newCount = (afterCount - beforeCount).coerceAtLeast(0)

        stateDao.upsertImportState(
            ImportStateEntity(
                storageKey = ImportStateEntity.ROBINHOOD_HISTORY,
                lastFileName = fileName,
                lastSource = TradingPlatformSource.ROBINHOOD.name,
                lastImportedAtEpochMillis = importedAtEpochMillis
            )
        )

        val merged = loadActivities()

        ImportMergeResult(
            reportRecordCount = incomingActivities.size,
            newRecordCount = newCount,
            duplicateRecordCount = (incomingActivities.size - newCount).coerceAtLeast(0),
            totalStoredCount = merged.size,
            mergedActivities = merged,
            fileName = fileName
        )
    }
}
