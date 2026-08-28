package com.tradna.APP.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalTradeImportRouterTest {

    @Test
    fun `blank input returns a useful unsupported result`() {
        val result = UniversalTradeImportRouter.importCsv("   ", "empty.csv")

        assertTrue(result is UniversalTradeImportResult.Unsupported)
        assertEquals("The selected trading-history file is empty.", (result as UniversalTradeImportResult.Unsupported).reason)
    }

    @Test
    fun `unrecognized schema is rejected instead of guessed`() {
        val result = UniversalTradeImportRouter.importCsv(
            "name,value\nexample,42",
            "unknown.csv"
        )

        assertTrue(result is UniversalTradeImportResult.Unsupported)
        assertEquals(TradingPlatformSource.GENERIC_CSV, result.source)
    }

    @Test
    fun `Robinhood activity is detected and parsed through the router`() {
        val csv = """
            Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount
            10/2/2026,10/2/2026,10/5/2026,MSFT,Microsoft Sell,Sell,1,$110.00,$110.00
            10/1/2026,10/1/2026,10/3/2026,MSFT,Microsoft Buy,Buy,1,$100.00,($100.00)
        """.trimIndent()

        val result = UniversalTradeImportRouter.importCsv(csv, "robinhood.csv")

        assertTrue(result is UniversalTradeImportResult.Robinhood)
        result as UniversalTradeImportResult.Robinhood
        assertEquals(TradingPlatformSource.ROBINHOOD, result.source)
        assertEquals(2, result.summary.activityCount)
    }
}
