package com.tradna.APP.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NormalizedTradeActivityEntity::class,
        ImportStateEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TraDnaDatabase : RoomDatabase() {

    abstract fun normalizedTradeActivityDao(): NormalizedTradeActivityDao

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
                    .build()
                    .also {
                        instance = it
                    }
            }
        }
    }
}
