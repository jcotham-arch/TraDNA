package com.tradna.APP.data.local

import android.content.Context
import androidx.room.withTransaction
import com.tradna.APP.data.TraDnaStorage
import com.tradna.APP.data.TradingPlatformSource

data class LegacyRobinhoodMigrationResult(
    val alreadyCompleted: Boolean,
    val legacyRecordCount: Int,
    val insertedRecordCount: Int,
    val databaseRecordCount: Int
)

class LegacyRobinhoodActivityMigration(
    private val context: Context,
    private val database: TraDnaDatabase
) {
    suspend fun migrateIfNeeded(
        migratedAtEpochMillis: Long = System.currentTimeMillis()
    ): LegacyRobinhoodMigrationResult {
        val activityDao = database.robinhoodActivityDao()
        val stateDao = database.normalizedTradeActivityDao()
        val existingMarker = stateDao.loadImportState(
            ImportStateEntity.LEGACY_ROBINHOOD_MIGRATION
        )

        val legacyActivities = TraDnaStorage.loadActivities(context)
        val legacyFileName = TraDnaStorage.loadFileName(context)

        return database.withTransaction {
            val insertResults = activityDao.insertIgnoringDuplicates(
                RobinhoodActivityMapper.toEntities(legacyActivities)
            )
            val insertedCount = insertResults.count { it != -1L }

            if (legacyActivities.isNotEmpty() && existingMarker == null) {
                stateDao.upsertImportState(
                    ImportStateEntity(
                        storageKey = ImportStateEntity.ROBINHOOD_HISTORY,
                        lastFileName = legacyFileName,
                        lastSource = TradingPlatformSource.ROBINHOOD.name,
                        lastImportedAtEpochMillis = migratedAtEpochMillis
                    )
                )
            }

            if (existingMarker == null) {
                stateDao.upsertImportState(
                    ImportStateEntity(
                        storageKey = ImportStateEntity.LEGACY_ROBINHOOD_MIGRATION,
                        lastFileName = "completed",
                        lastSource = TradingPlatformSource.ROBINHOOD.name,
                        lastImportedAtEpochMillis = migratedAtEpochMillis
                    )
                )
            }

            LegacyRobinhoodMigrationResult(
                alreadyCompleted = existingMarker != null,
                legacyRecordCount = legacyActivities.size,
                insertedRecordCount = insertedCount,
                databaseRecordCount = activityDao.count()
            )
        }
    }
}
