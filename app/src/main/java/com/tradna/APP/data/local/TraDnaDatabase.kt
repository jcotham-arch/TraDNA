package com.tradna.APP.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NormalizedTradeActivityEntity::class,
        ImportStateEntity::class,
        RobinhoodActivityEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class TraDnaDatabase : RoomDatabase() {

    abstract fun normalizedTradeActivityDao(): NormalizedTradeActivityDao
    abstract fun robinhoodActivityDao(): RobinhoodActivityDao

    companion object {
        private const val DATABASE_NAME = "tradna.db"

        @Volatile
        private var instance: TraDnaDatabase? = null

        fun getInstance(context: Context): TraDnaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TraDnaDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also {
                        instance = it
                    }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `robinhood_activities` (
                        `id` TEXT NOT NULL,
                        `occurrenceIndex` INTEGER NOT NULL,
                        `activitySortKey` INTEGER NOT NULL,
                        `activityDate` TEXT NOT NULL,
                        `processDate` TEXT NOT NULL,
                        `settleDate` TEXT NOT NULL,
                        `instrument` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `transCode` TEXT NOT NULL,
                        `quantity` TEXT NOT NULL,
                        `price` TEXT NOT NULL,
                        `amount` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_robinhood_activities_activitySortKey` " +
                            "ON `robinhood_activities` (`activitySortKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_robinhood_activities_instrument` " +
                            "ON `robinhood_activities` (`instrument`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_robinhood_activities_transCode` " +
                            "ON `robinhood_activities` (`transCode`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `robinhood_activities` " +
                            "ADD COLUMN `sourceOrder` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_robinhood_activities_activitySortKey_sourceOrder` " +
                            "ON `robinhood_activities` (`activitySortKey`, `sourceOrder`)"
                )
            }
        }
    }
}
