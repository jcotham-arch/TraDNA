package com.tradna.APP.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RobinhoodCsvParserTest {

    @Test
    fun `parser handles quoted commas and summarizes imported activity`() {
        val csv = """
            Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount
            4/3/2026,4/3/2026,4/6/2026,AAPL,"Apple, Inc.",Sell,2,$110.00,$220.00
            4/1/2026,4/1/2026,4/3/2026,AAPL,"Apple, Inc.",Buy,2,$100.00,($200.00)
        """.trimIndent()

        val summary = RobinhoodCsvParser.parse(csv, "account.csv")

        assertEquals(2, summary.activityCount)
        assertEquals(1, summary.buyCount)
        assertEquals(1, summary.sellCount)
        assertEquals(1, summary.instrumentCount)
        assertEquals("Apr 1, 2026", summary.startDate)
        assertEquals("Apr 3, 2026", summary.endDate)
        assertEquals("Apple, Inc.", summary.activities.first().description)
    }

    @Test
    fun `parser rejects a CSV with an unrelated schema`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RobinhoodCsvParser.parse("date,symbol,side\n4/1/2026,AAPL,buy", "other.csv")
        }

        assertEquals(
            "This does not appear to be a supported Robinhood Account Activity CSV.",
            error.message
        )
    }
}
