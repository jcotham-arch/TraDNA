package com.tradna.APP.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternLearningEngineTest {
    @Test
    fun `builds symbol and VWAP behavior from trained trades`() {
        val profile = PatternLearningEngine.analyze(
            listOf(
                record("ONDS", 8.0, 2.0, 120.0),
                record("ONDS", -1.0, 1.0, -20.0),
                record("NVDA", 3.0, 0.1, 60.0),
                record("RXT", 4.0, -1.5, 40.0)
            )
        )

        assertEquals(listOf("ONDS", "NVDA", "RXT"), profile.symbolPatterns.map { it.symbol })
        assertEquals(2, profile.symbolPatterns.first().trades)
        assertEquals(50.0, profile.symbolPatterns.first().profitableRatePercent!!, 0.001)
        assertEquals(100.0, profile.symbolPatterns.first().realizedPnl, 0.001)

        assertEquals(listOf("Below VWAP", "Near VWAP", "Above VWAP"), profile.vwapPatterns.map { it.title })
        assertEquals(1, profile.vwapPatterns.first().sampleSize)
        assertEquals(2, profile.vwapPatterns.last().sampleSize)
        assertTrue(profile.vwapPatterns.all { it.confidencePercent > 0 })
    }

    @Test
    fun `omits VWAP groups when entry context is unavailable`() {
        val profile = PatternLearningEngine.analyze(
            listOf(record("NVDA", 2.0, null, 10.0))
        )

        assertTrue(profile.vwapPatterns.isEmpty())
    }

    private fun record(
        symbol: String,
        returnPercent: Double,
        vwapDistance: Double?,
        pnl: Double
    ) = AgentTrainingRecord(
        id = "$symbol-$returnPercent-$vwapDistance",
        tradeId = "$symbol-$returnPercent",
        symbol = symbol,
        openDate = "1/1/2026",
        closeDate = "1/2/2026",
        actualEntryPrice = 100.0,
        actualExitPrice = 100.0 + returnPercent,
        actualReturnPercent = returnPercent,
        actualRealizedPnl = pnl,
        entryTechnicalScore = 60,
        entryVwap = vwapDistance?.let { 100.0 / (1.0 + it / 100.0) },
        entryEma9 = null,
        entryEma20 = null,
        entryRelativeVolume = null,
        entryDistanceFromVwapPercent = vwapDistance,
        entrySignals = emptyList(),
        entryEfficiencyScore = 60,
        exitEfficiencyScore = 60,
        totalEfficiencyScore = 60,
        maximumFavorableExcursionPercent = null,
        maximumAdverseExcursionPercent = null,
        missedUpsidePercent = null,
        bestAlternativeId = null,
        bestAlternativeTitle = null,
        bestAlternativeReturnPercent = null,
        bestAlternativeEstimatedPnl = null,
        improvementVsActualPercent = null,
        improvementVsActualDollars = null,
        strengths = emptyList(),
        weaknesses = emptyList(),
        recommendations = emptyList(),
        lessonTitle = "",
        lessonSummary = "",
        profitableTrade = pnl > 0.0,
        strongTechnicalEntry = false,
        efficientEntry = false,
        efficientExit = false,
        earlyExitCandidate = false,
        highAdverseExcursion = false,
        alternativeOutperformedActual = false
    )
}
