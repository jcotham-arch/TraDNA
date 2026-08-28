package com.tradna.APP.data.local

import androidx.room.withTransaction
import com.tradna.APP.data.NormalizedImportMergeResult
import com.tradna.APP.data.NormalizedTradeActivity
import com.tradna.APP.data.TradingPlatformSource

class NormalizedActivityRoomRepository(
    private val database: TraDnaDatabase
) {
    private val dao = database.normalizedTradeActivityDao()

    suspend fun loadActivities(): List<NormalizedTradeActivity> =
        dao.loadAll().map(NormalizedTradeActivityMapper::toDomain)

    suspend fun loadImportState(): ImportStateEntity? =
        dao.loadImportState(ImportStateEntity.NORMALIZED_HISTORY)

    suspend fun mergeActivities(
        incomingActivities: List<NormalizedTradeActivity>,
        source: TradingPlatformSource,
        fileName: String,
        importedAtEpochMillis: Long = System.currentTimeMillis()
    ): NormalizedImportMergeResult = database.withTransaction {
        val insertResults = dao.insertIgnoringDuplicates(
            incomingActivities.map(NormalizedTradeActivityMapper::toEntity)
        )
        val newCount = insertResults.count { it != -1L }

        dao.upsertImportState(
            ImportStateEntity(
                storageKey = ImportStateEntity.NORMALIZED_HISTORY,
                lastFileName = fileName,
                lastSource = source.name,
                lastImportedAtEpochMillis = importedAtEpochMillis
            )
        )

        val merged = loadActivities()

        NormalizedImportMergeResult(
            source = source,
            fileName = fileName,
            incomingRecordCount = incomingActivities.size,
            newRecordCount = newCount,
            duplicateRecordCount = incomingActivities.size - newCount,
            totalStoredCount = merged.size,
            mergedActivities = merged
        )
    }

    suspend fun clear() = database.withTransaction {
        dao.deleteAll()
        dao.upsertImportState(
            ImportStateEntity(
                storageKey = ImportStateEntity.NORMALIZED_HISTORY,
                lastFileName = "",
                lastSource = TradingPlatformSource.GENERIC_CSV.name,
                lastImportedAtEpochMillis = 0L
            )
        )
    }
}
