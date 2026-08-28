package com.tradna.APP.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizedStockTradeReconstructorTest {

    @Test
    fun `long trade uses weighted average basis and subtracts all costs`() {
        val episodes = NormalizedStockTradeReconstructor.reconstruct(
            listOf(
                stock("buy-1", "8/1/2026", NormalizedTradeSide.BUY, 5.0, 10.0, commission = 0.50),
                stock("buy-2", "8/2/2026", NormalizedTradeSide.BUY, 5.0, 14.0, fees = 0.25),
                stock("sell", "8/3/2026", NormalizedTradeSide.SELL, 10.0, 15.0, commission = 0.50, fees = 0.25)
            )
        )

        with(episodes.single()) {
            assertEquals(NormalizedStockPositionDirection.LONG, direction)
            assertEquals(NormalizedStockTradeStatus.CLOSED, status)
            assertEquals(12.0, averageEntryPrice, TOLERANCE)
            assertEquals(15.0, averageExitPrice!!, TOLERANCE)
            assertEquals(28.5, realizedPnl, TOLERANCE)
            assertEquals(1.0, totalCommission, TOLERANCE)
            assertEquals(0.5, totalFees, TOLERANCE)
            assertEquals(0.0, remainingShares, TOLERANCE)
        }
    }

    @Test
    fun `short and cover calculate profit in the correct direction`() {
        val episodes = NormalizedStockTradeReconstructor.reconstruct(
            listOf(
                stock("short", "8/4/2026", NormalizedTradeSide.SHORT, 4.0, 20.0, fees = 0.20),
                stock("cover", "8/5/2026", NormalizedTradeSide.COVER, 4.0, 16.0, fees = 0.20)
            )
        )

        with(episodes.single()) {
            assertEquals(NormalizedStockPositionDirection.SHORT, direction)
            assertEquals(NormalizedStockTradeStatus.CLOSED, status)
            assertEquals(20.0, averageEntryPrice, TOLERANCE)
            assertEquals(16.0, averageExitPrice!!, TOLERANCE)
            assertEquals(15.6, realizedPnl, TOLERANCE)
            assertEquals(0.0, netShares, TOLERANCE)
        }
    }

    @Test
    fun `partial close retains basis and marks episode partial`() {
        val episodes = NormalizedStockTradeReconstructor.reconstruct(
            listOf(
                stock("buy", "8/6/2026", NormalizedTradeSide.BUY, 10.0, 25.0),
                stock("sell", "8/7/2026", NormalizedTradeSide.SELL, 3.0, 30.0)
            )
        )

        with(episodes.single()) {
            assertEquals(NormalizedStockTradeStatus.PARTIAL, status)
            assertEquals(7.0, netShares, TOLERANCE)
            assertEquals(7.0, remainingShares, TOLERANCE)
            assertEquals(15.0, realizedPnl, TOLERANCE)
            assertEquals(null, closeDate)
        }
    }

    @Test
    fun `oversell closes long and opens a new short episode`() {
        val episodes = NormalizedStockTradeReconstructor.reconstruct(
            listOf(
                stock("buy", "8/8/2026", NormalizedTradeSide.BUY, 10.0, 10.0),
                stock("reverse", "8/9/2026", NormalizedTradeSide.SELL, 15.0, 12.0)
            )
        )

        assertEquals(2, episodes.size)

        val closedLong = episodes.first { it.direction == NormalizedStockPositionDirection.LONG }
        val openShort = episodes.first { it.direction == NormalizedStockPositionDirection.SHORT }

        assertEquals(NormalizedStockTradeStatus.CLOSED, closedLong.status)
        assertEquals(20.0, closedLong.realizedPnl, TOLERANCE)
        assertEquals(10.0, closedLong.totalSharesClosed, TOLERANCE)

        assertEquals(NormalizedStockTradeStatus.OPEN, openShort.status)
        assertEquals(-5.0, openShort.netShares, TOLERANCE)
        assertEquals(5.0, openShort.totalSharesOpened, TOLERANCE)
        assertEquals(12.0, openShort.averageEntryPrice, TOLERANCE)
    }

    @Test
    fun `accounts with the same symbol remain separate`() {
        val episodes = NormalizedStockTradeReconstructor.reconstruct(
            listOf(
                stock("one", "8/10/2026", NormalizedTradeSide.BUY, 1.0, 10.0, accountId = "ACCOUNT-1"),
                stock("two", "8/10/2026", NormalizedTradeSide.BUY, 2.0, 10.0, accountId = "ACCOUNT-2")
            )
        )

        assertEquals(2, episodes.size)
        assertEquals(setOf("ACCOUNT-1", "ACCOUNT-2"), episodes.map { it.accountId }.toSet())
    }

    private fun stock(
        id: String,
        date: String,
        side: NormalizedTradeSide,
        quantity: Double,
        price: Double,
        commission: Double = 0.0,
        fees: Double = 0.0,
        accountId: String = "ACCOUNT-1"
    ) = NormalizedTradeActivity(
        id = id,
        source = TradingPlatformSource.GENERIC_CSV,
        accountId = accountId,
        assetClass = NormalizedAssetClass.STOCK,
        symbol = "XYZ",
        side = side,
        quantity = quantity,
        price = price,
        activityDate = date,
        commission = commission,
        fees = fees
    )

    private companion object {
        const val TOLERANCE = 0.000001
    }
}
