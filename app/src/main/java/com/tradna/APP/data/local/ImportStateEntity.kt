package com.tradna.APP.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_state")
data class ImportStateEntity(
    @PrimaryKey
    val storageKey: String,
    val lastFileName: String,
    val lastSource: String,
    val lastImportedAtEpochMillis: Long
) {
    companion object {
        const val NORMALIZED_HISTORY = "normalized_history"
        const val LEGACY_NORMALIZED_MIGRATION = "legacy_normalized_migration_v1"
    }
}
