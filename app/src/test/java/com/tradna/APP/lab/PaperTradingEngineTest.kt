package com.tradna.APP.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperTradingEngineTest {
    @Test
    fun `opens favorable recommendation at ten percent of paper equity`() {
        val result = PaperTradingEngine.openFromPrediction(PaperAccount(), prediction("one"))

        assertTrue(result.accepted)
        assertEquals(4_500.0, result.account.cash, 0.001)
        assertEquals(500.0, result.account.positions.single().marketValue, 0.001)
        assertEquals(5_000.0, result.account.equity, 0.001)
    }

    @Test
    fun `does not simulate the same frozen recommendation twice`() {
        val first = PaperTradingEngine.openFromPrediction(PaperAccount(), prediction("same"))
        val second = PaperTradingEngine.openFromPrediction(first.account, prediction("same"))

        assertFalse(second.accepted)
        assertEquals(1, second.account.positions.size)
    }

    @Test
    fun `wait recommendation cannot open a paper position`() {
        val result = PaperTradingEngine.openFromPrediction(
            PaperAccount(),
            prediction("wait").copy(decision = "WAIT")
        )

        assertFalse(result.accepted)
        assertEquals(5_000.0, result.account.cash, 0.001)
    }

    private fun prediction(id: String) = AgentPredictionRecord(
        id = id, createdAtEpochMillis = 1L, symbol = "QBTS", decision = "FAVORABLE",
        overallScore = 75, confidencePercent = 70, technicalScore = 70,
        historyMatchScore = 70, entryQualityScore = 70, riskQualityScore = 70,
        evidenceConfidenceScore = 70, marketPrice = 100.0, vwap = 99.0,
        ema9 = null, ema20 = null, relativeVolume = null,
        distanceFromVwapPercent = 1.0, currentSignals = emptyList(),
        matchedTradeCount = 5, historicalProfitableRatePercent = 60.0,
        historicalAverageReturnPercent = 2.0, historicalAverageMfePercent = null,
        historicalAverageMaePercent = null, preferredEntryMethod = "VWAP retest",
        preferredExitMethod = "Target", strengths = emptyList(), warnings = emptyList(),
        reasoning = emptyList(), proposedStopPrice = 95.0, proposedTargetPrice = 110.0
    )
}
