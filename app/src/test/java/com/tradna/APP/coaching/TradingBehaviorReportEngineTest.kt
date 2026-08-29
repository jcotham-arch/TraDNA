package com.tradna.APP.coaching

import com.tradna.APP.data.RobinhoodActivity
import com.tradna.APP.data.StockExecution
import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.data.TradeStatus
import com.tradna.APP.data.OptionTradeEpisode
import com.tradna.APP.data.OptionTradeStatus
import com.tradna.APP.market.OptionInstrument
import com.tradna.APP.market.OptionRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingBehaviorReportEngineTest {
    @Test
    fun `calculates completed trade baseline without counting open trades`() {
        val report = TradingBehaviorReportEngine.analyze(
            listOf(
                trade("A", 1, "1/1/2026", "1/3/2026", 100.0, 40.0),
                trade("B", 1, "1/4/2026", "1/8/2026", 200.0, -20.0),
                trade("C", 1, "1/9/2026", "1/9/2026", 150.0, 0.0),
                trade("OPEN", 1, "1/10/2026", null, 999.0, 500.0)
            )
        )

        assertEquals(3, report.completedTrades)
        assertEquals(1, report.winningTrades)
        assertEquals(1, report.losingTrades)
        assertEquals(1, report.breakevenTrades)
        assertEquals(33.333, report.winRatePercent!!, 0.001)
        assertEquals(20.0, report.realizedPnl, 0.001)
        assertEquals(2.0, report.payoffRatio!!, 0.001)
        assertEquals(2.0, report.profitFactor!!, 0.001)
        assertEquals(2.0, report.averageHoldingDays!!, 0.001)
    }

    @Test
    fun `emits evidence-backed concentration and sizing warnings`() {
        val trades = (1..5).map { index ->
            trade(
                symbol = if (index <= 4) "FOCUS" else "OTHER",
                sequence = index,
                open = "2/$index/2026",
                close = "2/${index + 1}/2026",
                cost = if (index == 5) 500.0 else 100.0,
                pnl = if (index % 2 == 0) 5.0 else -10.0
            )
        }

        val report = TradingBehaviorReportEngine.analyze(trades)

        assertEquals("FOCUS", report.topSymbol)
        assertEquals(80.0, report.topSymbolTradeSharePercent!!, 0.001)
        assertTrue(report.signals.any { it.title == "Symbol concentration" })
        assertTrue(report.signals.any { it.title == "Inconsistent position size" })
        assertTrue(report.signals.all { it.evidence.isNotBlank() })
    }

    @Test
    fun `combines only closed option pnl with completed stock pnl`() {
        val stock = trade("STOCK", 1, "3/1/2026", "3/2/2026", 100.0, 7_106.72)
        val closedOption = optionTrade("CLOSED", OptionTradeStatus.CLOSED, -333.88)
        val openOption = optionTrade("OPEN", OptionTradeStatus.OPEN, 500.0)

        val report = TradingBehaviorReportEngine.analyze(
            trades = listOf(stock),
            optionTrades = listOf(closedOption, openOption)
        )

        assertEquals(7_106.72, report.stockRealizedPnl, 0.001)
        assertEquals(-333.88, report.optionRealizedPnl, 0.001)
        assertEquals(6_772.84, report.realizedPnl, 0.001)
        assertEquals(1, report.completedOptionTrades)
    }

    private fun trade(
        symbol: String,
        sequence: Int,
        open: String,
        close: String?,
        cost: Double,
        pnl: Double
    ): TradeEpisode {
        val source = RobinhoodActivity(open, open, open, symbol, symbol, "Buy", "1", "$$cost", "($$cost)")
        return TradeEpisode(
            id = "$symbol-$sequence",
            symbol = symbol,
            sequenceNumber = sequence,
            openDate = open,
            closeDate = close,
            totalSharesBought = 1.0,
            totalSharesSold = if (close == null) 0.0 else 1.0,
            remainingShares = if (close == null) 1.0 else 0.0,
            totalBuyCost = cost,
            totalSellProceeds = cost + pnl,
            averageEntryPrice = cost,
            averageExitPrice = close?.let { cost + pnl },
            realizedPnl = pnl,
            status = if (close == null) TradeStatus.OPEN else TradeStatus.CLOSED,
            executions = listOf(StockExecution(open, symbol, "Buy", 1.0, cost, cost, source))
        )
    }

    private fun optionTrade(
        symbol: String,
        status: OptionTradeStatus,
        pnl: Double
    ) = OptionTradeEpisode(
        id = symbol,
        contract = OptionInstrument(
            symbol = symbol,
            underlyingSymbol = "TEST",
            expirationDate = "12/18/2026",
            strikePrice = 100.0,
            right = OptionRight.CALL
        ),
        status = status,
        openDate = "3/1/2026",
        closeDate = if (status == OptionTradeStatus.CLOSED) "3/2/2026" else null,
        netContracts = if (status == OptionTradeStatus.CLOSED) 0.0 else 1.0,
        averageEntryPremium = 1.0,
        averageExitPremium = if (status == OptionTradeStatus.CLOSED) 0.0 else null,
        realizedPnl = pnl,
        executions = emptyList()
    )
}
