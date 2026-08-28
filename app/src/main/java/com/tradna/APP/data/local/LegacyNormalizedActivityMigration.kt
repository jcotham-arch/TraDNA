package com.tradna.APP.data.local

import android.content.Context
import androidx.room.withTransaction
import com.tradna.APP.data.TradingPlatformSource
import com.tradna.APP.data.UniversalTradingDataStorage

data class LegacyNormalizedMigrationResult(
    val alreadyCompleted: Boolean,
    val legacyRecordCount: Int,
    val insertedRecordCount: Int,
    val databaseRecordCount: Int
)

class LegacyNormalizedActivityMigration(
    private val context: Context,
    private val database: TraDnaDatabase
) {
    suspend fun migrateIfNeeded(
        migratedAtEpochMillis: Long = System.currentTimeMillis()
    ): LegacyNormalizedMigrationResult {
        val dao = database.normalizedTradeActivityDao()

        val existingMarker = dao.loadImportState(
            ImportStateEntity.LEGACY_NORMALIZED_MIGRATION
        )

        /*
         * Read legacy preferences before opening the database transaction.
         * Preferences remain untouched after a successful copy so an older
         * application build can still recover the user's local history.
         */
        val legacyActivities = UniversalTradingDataStorage.loadActivities(context)
        val legacyFileName = UniversalTradingDataStorage.loadFileName(context)
        val legacySource = UniversalTradingDataStorage.loadLastSource(context)
            ?: TradingPlatformSource.GENERIC_CSV

        return database.withTransaction {
            val insertResults = dao.insertIgnoringDuplicates(
                legacyActivities.map(NormalizedTradeActivityMapper::toEntity)
            )
            val insertedCount = insertResults.count { it != -1L }

            if (
                legacyActivities.isNotEmpty() &&
                existingMarker == null
            ) {
                dao.upsertImportState(
                    ImportStateEntity(
                        storageKey = ImportStateEntity.NORMALIZED_HISTORY,
                        lastFileName = legacyFileName,
                        lastSource = legacySource.name,
                        lastImportedAtEpochMillis = migratedAtEpochMillis
                    )
                )
            }

            if (existingMarker == null) {
                dao.upsertImportState(
                    ImportStateEntity(
                        storageKey = ImportStateEntity.LEGACY_NORMALIZED_MIGRATION,
                        lastFileName = "completed",
                        lastSource = legacySource.name,
                        lastImportedAtEpochMillis = migratedAtEpochMillis
                    )
                )
            }

            LegacyNormalizedMigrationResult(
                alreadyCompleted = existingMarker != null,
                legacyRecordCount = legacyActivities.size,
                insertedRecordCount = insertedCount,
                databaseRecordCount = dao.count()
            )
        }
    }
}
