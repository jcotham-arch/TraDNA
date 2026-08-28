package com.tradna.APP.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FuturesTradeReconstructorTest {

    @Test
    fun `MES long applies five dollar point value and trading costs`() {
        val episodes = FuturesTradeReconstructor.reconstruct(
            listOf(
                future("open", "8/11/2026", NormalizedTradeSide.BUY, 2.0, 6500.0, commission = 1.0, fees = 0.50),
                future("close", "8/11/2026 10:30:00", NormalizedTradeSide.SELL, 2.0, 6504.0, commission = 1.0, fees = 0.50)
            )
        )

        with(episodes.single()) {
            assertEquals(FuturePositionDirection.LONG, direction)
            assertEquals(FutureTradeStatus.CLOSED, status)
            assertEquals("MES", rootSymbol)
            assertEquals(5.0, pointValue, TOLERANCE)
            assertEquals(0.25, tickSize, TOLERANCE)
            assertEquals(1.25, tickValue, TOLERANCE)
            assertEquals(37.0, realizedPnl, TOLERANCE)
            assertEquals(4.0, grossPoints!!, TOLERANCE)
            assertEquals(40.0, grossDollarPnl!!, TOLERANCE)
        }
    }

    @Test
    fun `ES short profits when covered below entry`() {
        val episodes = FuturesTradeReconstructor.reconstruct(
            listOf(
                future("short", "8/12/2026", NormalizedTradeSide.SHORT, 1.0, 6550.0, symbol = "ESZ26"),
                future("cover", "8/12/2026 11:00:00", NormalizedTradeSide.COVER, 1.0, 6548.0, symbol = "ESZ26")
            )
        )

        with(episodes.single()) {
            assertEquals(FuturePositionDirection.SHORT, direction)
            assertEquals(FutureTradeStatus.CLOSED, status)
            assertEquals("ES", rootSymbol)
            assertEquals("Z26", expirationDate)
            assertEquals(100.0, realizedPnl, TOLERANCE)
        }
    }

    @Test
    fun `partial futures close retains remaining contracts`() {
        val episodes = FuturesTradeReconstructor.reconstruct(
            listOf(
                future("open", "8/13/2026", NormalizedTradeSide.BUY, 3.0, 100.0, pointValue = 10.0),
                future("partial", "8/14/2026", NormalizedTradeSide.SELL, 1.0, 102.0, pointValue = 10.0)
            )
        )

        with(episodes.single()) {
            assertEquals(FutureTradeStatus.PARTIAL, status)
            assertEquals(2.0, netContracts, TOLERANCE)
            assertEquals(1.0, totalContractsClosed, TOLERANCE)
            assertEquals(20.0, realizedPnl, TOLERANCE)
            assertEquals(null, closeDate)
        }
    }

    @Test
    fun `oversell closes long future and opens opposite episode`() {
        val episodes = FuturesTradeReconstructor.reconstruct(
            listOf(
                future("buy", "8/15/2026", NormalizedTradeSide.BUY, 1.0, 100.0, pointValue = 10.0),
                future("reverse", "8/16/2026", NormalizedTradeSide.SELL, 3.0, 102.0, pointValue = 10.0)
            )
        )

        assertEquals(2, episodes.size)

        val closedLong = episodes.first { it.direction == FuturePositionDirection.LONG }
        val openShort = episodes.first { it.direction == FuturePositionDirection.SHORT }

        assertEquals(FutureTradeStatus.CLOSED, closedLong.status)
        assertEquals(20.0, closedLong.realizedPnl, TOLERANCE)
        assertEquals(FutureTradeStatus.OPEN, openShort.status)
        assertEquals(-2.0, openShort.netContracts, TOLERANCE)
        assertEquals(102.0, openShort.averageEntryPrice, TOLERANCE)
    }

    @Test
    fun `contracts from different accounts remain isolated`() {
        val episodes = FuturesTradeReconstructor.reconstruct(
            listOf(
                future("one", "8/17/2026", NormalizedTradeSide.BUY, 1.0, 6500.0, accountId = "ONE"),
                future("two", "8/17/2026", NormalizedTradeSide.BUY, 2.0, 6500.0, accountId = "TWO")
            )
        )

        assertEquals(2, episodes.size)
        assertEquals(setOf(1.0, 2.0), episodes.map { it.totalContractsOpened }.toSet())
    }

    private fun future(
        id: String,
        date: String,
        side: NormalizedTradeSide,
        quantity: Double,
        price: Double,
        symbol: String = "MESZ26",
        commission: Double = 0.0,
        fees: Double = 0.0,
        pointValue: Double? = null,
        accountId: String = "ACCOUNT-1"
    ) = NormalizedTradeActivity(
        id = id,
        source = TradingPlatformSource.WEALTHCHARTS,
        accountId = accountId,
        assetClass = NormalizedAssetClass.FUTURE,
        symbol = symbol,
        side = side,
        quantity = quantity,
        price = price,
        activityDate = date,
        commission = commission,
        fees = fees,
        futuresPointValue = pointValue
    )

    private companion object {
        const val TOLERANCE = 0.000001
    }
}
