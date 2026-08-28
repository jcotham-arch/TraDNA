package com.tradna.APP.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TradeReconstructorTest {

    @Test
    fun `partial exit uses FIFO cost basis and leaves the episode open`() {
        val trades = TradeReconstructor.reconstruct(
            listOf(
                activity("1/4/2026", "XYZ", "Sell", "6", "$15.00", "$90.00"),
                activity("1/3/2026", "XYZ", "Buy", "5", "$12.00", "($60.00)"),
                activity("1/2/2026", "XYZ", "Buy", "5", "$10.00", "($50.00)")
            )
        )

        assertEquals(1, trades.size)

        with(trades.single()) {
            assertEquals(TradeStatus.PARTIAL, status)
            assertEquals(10.0, totalSharesBought, TOLERANCE)
            assertEquals(6.0, totalSharesSold, TOLERANCE)
            assertEquals(4.0, remainingShares, TOLERANCE)
            assertEquals(110.0, totalBuyCost, TOLERANCE)
            assertEquals(90.0, totalSellProceeds, TOLERANCE)
            assertEquals(28.0, realizedPnl, TOLERANCE)
            assertEquals(11.0, averageEntryPrice, TOLERANCE)
            assertEquals(15.0, averageExitPrice!!, TOLERANCE)
            assertEquals(null, closeDate)
        }
    }

    @Test
    fun `closing then buying again creates two independent episodes`() {
        val trades = TradeReconstructor.reconstruct(
            listOf(
                activity("2/4/2026", "ABC", "Sell", "2", "$25.00", "$50.00"),
                activity("2/3/2026", "ABC", "Buy", "2", "$20.00", "($40.00)"),
                activity("2/2/2026", "ABC", "Sell", "3", "$12.00", "$36.00"),
                activity("2/1/2026", "ABC", "Buy", "3", "$10.00", "($30.00)")
            )
        )

        assertEquals(2, trades.size)

        with(trades[0]) {
            assertEquals(2, sequenceNumber)
            assertEquals("2/3/2026", openDate)
            assertEquals("2/4/2026", closeDate)
            assertEquals(TradeStatus.CLOSED, status)
            assertEquals(10.0, realizedPnl, TOLERANCE)
        }

        with(trades[1]) {
            assertEquals(1, sequenceNumber)
            assertEquals("2/1/2026", openDate)
            assertEquals("2/2/2026", closeDate)
            assertEquals(TradeStatus.CLOSED, status)
            assertEquals(6.0, realizedPnl, TOLERANCE)
        }
    }

    @Test
    fun `sell without an open position is ignored`() {
        val trades = TradeReconstructor.reconstruct(
            listOf(
                activity("3/1/2026", "NONE", "Sell", "1", "$10.00", "$10.00")
            )
        )

        assertEquals(emptyList<TradeEpisode>(), trades)
    }

    private fun activity(
        date: String,
        symbol: String,
        code: String,
        quantity: String,
        price: String,
        amount: String
    ) = RobinhoodActivity(
        activityDate = date,
        processDate = date,
        settleDate = date,
        instrument = symbol,
        description = "$symbol transaction",
        transCode = code,
        quantity = quantity,
        price = price,
        amount = amount
    )

    private companion object {
        const val TOLERANCE = 0.000001
    }
}
