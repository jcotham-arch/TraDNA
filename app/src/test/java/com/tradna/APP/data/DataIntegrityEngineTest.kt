package com.tradna.APP.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataIntegrityEngineTest {

    @Test
    fun `valid closed trade receives a clean integrity report`() {
        val activities = listOf(
            activity("9/2/2026", "AAPL", "Sell", "2", "$110.00", "$220.00"),
            activity("9/1/2026", "AAPL", "Buy", "2", "$100.00", "($200.00)")
        )

        val report = DataIntegrityEngine.analyze(
            activities,
            TradeReconstructor.reconstruct(activities)
        )

        assertEquals(2, report.totalRecords)
        assertEquals(1, report.closedTrades)
        assertEquals(0, report.warningCount)
        assertEquals(100, report.healthScore)
    }

    @Test
    fun `unmatched sell is detected without inventing a trade`() {
        val activities = listOf(
            activity("9/3/2026", "AAPL", "Sell", "3", "$110.00", "$330.00"),
            activity("9/1/2026", "AAPL", "Buy", "2", "$100.00", "($200.00)")
        )

        val report = DataIntegrityEngine.analyze(
            activities,
            TradeReconstructor.reconstruct(activities)
        )

        assertEquals(1, report.unmatchedSellRecords)
        assertEquals(97, report.healthScore)
        assertTrue(report.warnings.single().contains("sells exceed shares"))
    }

    @Test
    fun `cash activity may omit an instrument without penalty`() {
        val activities = listOf(
            activity("9/4/2026", "", "ACH", "", "", "$500.00")
        )

        val report = DataIntegrityEngine.analyze(activities, emptyList())

        assertEquals(0, report.blankInstrumentRecords)
        assertEquals(1, report.otherRecords)
        assertEquals(100, report.healthScore)
    }

    @Test
    fun `malformed execution fields reduce health deterministically`() {
        val activities = listOf(
            activity("", "", "Buy", "not-a-number", "bad-price", "bad-amount")
        )

        val report = DataIntegrityEngine.analyze(activities, emptyList())

        assertEquals(1, report.blankInstrumentRecords)
        assertEquals(1, report.blankDateRecords)
        assertEquals(1, report.invalidQuantityRecords)
        assertEquals(1, report.invalidPriceRecords)
        assertEquals(1, report.invalidAmountRecords)
        assertEquals(80, report.healthScore)
        assertEquals(5, report.warningCount)
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
        description = "$code $symbol",
        transCode = code,
        quantity = quantity,
        price = price,
        amount = amount
    )
}
