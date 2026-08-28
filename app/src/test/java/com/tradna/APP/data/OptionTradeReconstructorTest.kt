package com.tradna.APP.data

import com.tradna.APP.market.OptionRight
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionTradeReconstructorTest {

    @Test
    fun `long option close applies the contract multiplier and fees`() {
        val episodes = OptionTradeReconstructor.reconstruct(
            listOf(
                option("open", "5/1/2026", OptionTransactionType.BUY_TO_OPEN, 2.0, 1.25, fees = 1.0),
                option("close", "5/4/2026", OptionTransactionType.SELL_TO_CLOSE, 2.0, 2.00, fees = 1.0)
            )
        )

        assertEquals(1, episodes.size)

        with(episodes.single()) {
            assertEquals(OptionTradeStatus.CLOSED, status)
            assertEquals("5/1/2026", openDate)
            assertEquals("5/4/2026", closeDate)
            assertEquals(0.0, netContracts, TOLERANCE)
            assertEquals(1.25, averageEntryPremium, TOLERANCE)
            assertEquals(2.00, averageExitPremium!!, TOLERANCE)
            assertEquals(148.0, realizedPnl, TOLERANCE)
        }
    }

    @Test
    fun `short option expiring worthless realizes collected premium less fees`() {
        val episodes = OptionTradeReconstructor.reconstruct(
            listOf(
                option("open", "6/1/2026", OptionTransactionType.SELL_TO_OPEN, 1.0, 0.80, fees = 0.50),
                option("expire", "6/19/2026", OptionTransactionType.EXPIRATION, 1.0, 0.0, fees = 0.50)
            )
        )

        with(episodes.single()) {
            assertEquals(OptionTradeStatus.CLOSED, status)
            assertEquals(0.0, averageExitPremium!!, TOLERANCE)
            assertEquals(79.0, realizedPnl, TOLERANCE)
        }
    }

    @Test
    fun `partial option close leaves an open episode with remaining contracts`() {
        val episodes = OptionTradeReconstructor.reconstruct(
            listOf(
                option("open", "7/1/2026", OptionTransactionType.BUY_TO_OPEN, 3.0, 1.00),
                option("partial", "7/2/2026", OptionTransactionType.SELL_TO_CLOSE, 1.0, 1.50)
            )
        )

        with(episodes.single()) {
            assertEquals(OptionTradeStatus.OPEN, status)
            assertEquals(2.0, netContracts, TOLERANCE)
            assertEquals(50.0, realizedPnl, TOLERANCE)
            assertEquals(null, closeDate)
        }
    }

    @Test
    fun `reconstructing the same option history produces a stable episode id`() {
        val history = listOf(
            option("open-stable", "7/3/2026", OptionTransactionType.BUY_TO_OPEN, 1.0, 1.00),
            option("close-stable", "7/4/2026", OptionTransactionType.SELL_TO_CLOSE, 1.0, 1.50)
        )

        val firstId = OptionTradeReconstructor.reconstruct(history).single().id
        val secondId = OptionTradeReconstructor.reconstruct(history).single().id

        assertEquals(firstId, secondId)
    }

    private fun option(
        id: String,
        date: String,
        type: OptionTransactionType,
        contracts: Double,
        premium: Double,
        fees: Double = 0.0
    ) = RawOptionActivity(
        id = id,
        contractSymbol = "AAPL260821C00200000",
        underlyingSymbol = "AAPL",
        expirationDate = "8/21/2026",
        strikePrice = 200.0,
        right = OptionRight.CALL,
        transactionType = type,
        contracts = contracts,
        premium = premium,
        activityDate = date,
        fees = fees,
        contractMultiplier = 100.0
    )

    private companion object {
        const val TOLERANCE = 0.000001
    }
}
