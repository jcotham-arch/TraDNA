package com.tradna.APP.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tradna.APP.data.NormalizedAssetClass
import com.tradna.APP.data.NormalizedTradeActivity
import com.tradna.APP.data.NormalizedTradeSide
import com.tradna.APP.data.TradingPlatformSource
import com.tradna.APP.data.UniversalTradingDataStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TraDnaDatabaseTest {
    /*
     * Use the instrumentation package context, not targetContext.
     * This keeps migration fixtures and cleanup isolated from the user's
     * installed TraDNA application data on a physical device.
     */
    private val context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var database: TraDnaDatabase

    @Before
    fun setUp() {
        UniversalTradingDataStorage.clear(context)
        database = Room.inMemoryDatabaseBuilder(
            context,
            TraDnaDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
        UniversalTradingDataStorage.clear(context)
    }

    @Test
    fun duplicateSafeInsertKeepsOneCanonicalExecution() = runBlocking {
        val entity = NormalizedTradeActivityMapper.toEntity(activity("same-id"))
        val dao = database.normalizedTradeActivityDao()

        val first = dao.insertIgnoringDuplicates(listOf(entity))
        val second = dao.insertIgnoringDuplicates(listOf(entity))

        assertTrue(first.single() != -1L)
        assertEquals(-1L, second.single())
        assertEquals(1, dao.count())
    }

    @Test
    fun legacyMigrationCopiesOnceAndPreservesRollbackPreferences() = runBlocking {
        UniversalTradingDataStorage.mergeActivities(
            context = context,
            incomingActivities = listOf(activity("legacy-id")),
            source = TradingPlatformSource.TRADINGVIEW,
            fileName = "legacy.csv"
        )

        val migration = LegacyNormalizedActivityMigration(context, database)
        val first = migration.migrateIfNeeded(migratedAtEpochMillis = 123L)
        val second = migration.migrateIfNeeded(migratedAtEpochMillis = 456L)

        assertFalse(first.alreadyCompleted)
        assertEquals(1, first.insertedRecordCount)
        assertTrue(second.alreadyCompleted)
        assertEquals(0, second.insertedRecordCount)
        assertEquals(1, second.databaseRecordCount)
        assertEquals(1, UniversalTradingDataStorage.loadActivities(context).size)

        val state = database.normalizedTradeActivityDao().loadImportState(
            ImportStateEntity.NORMALIZED_HISTORY
        )
        assertEquals("legacy.csv", state?.lastFileName)
        assertEquals(123L, state?.lastImportedAtEpochMillis)
    }

    private fun activity(id: String) = NormalizedTradeActivity(
        id = id,
        source = TradingPlatformSource.TRADINGVIEW,
        accountId = "account",
        assetClass = NormalizedAssetClass.STOCK,
        symbol = "AAPL",
        side = NormalizedTradeSide.BUY,
        quantity = 1.0,
        price = 100.0,
        activityDate = "2026-08-28"
    )
}
