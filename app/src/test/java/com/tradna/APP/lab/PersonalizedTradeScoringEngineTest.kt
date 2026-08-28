package com.tradna.APP.lab

import com.tradna.APP.market.TechnicalSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizedTradeScoringEngineTest {

    private val emptyProfile = PatternLearningEngine.analyze(emptyList())

    @Test
    fun `weak technical state creates a high-risk avoid decision`() {
        val result = PersonalizedTradeScoringEngine.score(
            symbol = "AAPL",
            snapshot = snapshot(technicalScore = 35),
            records = emptyList(),
            patternProfile = emptyProfile
        )

        assertEquals(AgentTradeDecision.AVOID, result.decision)
        assertTrue(result.warnings.any { it.startsWith("HIGH RISK:") })
    }

    @Test
    fun `price extended over five percent above VWAP always avoids`() {
        val result = PersonalizedTradeScoringEngine.score(
            symbol = "AAPL",
            snapshot = snapshot(
                technicalScore = 90,
                price = 106.0,
                vwap = 100.0,
                distanceFromVwapPercent = 6.0
            ),
            records = emptyList(),
            patternProfile = emptyProfile,
            proposedEntryPrice = 106.0,
            proposedStopPrice = 104.0,
            proposedTargetPrice = 112.0
        )

        assertEquals(AgentTradeDecision.AVOID, result.decision)
        assertTrue(result.warnings.any { it.contains("extended more than 5%") })
    }

    @Test
    fun `three-to-one reward risk earns the top risk score with sensible stop distance`() {
        val result = PersonalizedTradeScoringEngine.score(
            symbol = "AAPL",
            snapshot = snapshot(technicalScore = 75),
            records = emptyList(),
            patternProfile = emptyProfile,
            proposedEntryPrice = 100.0,
            proposedStopPrice = 98.0,
            proposedTargetPrice = 106.0
        )

        assertEquals(100, result.scoreBreakdown.riskQualityScore)
    }

    @Test
    fun `invalid stop and target geometry receives low risk quality`() {
        val result = PersonalizedTradeScoringEngine.score(
            symbol = "AAPL",
            snapshot = snapshot(technicalScore = 75),
            records = emptyList(),
            patternProfile = emptyProfile,
            proposedEntryPrice = 100.0,
            proposedStopPrice = 101.0,
            proposedTargetPrice = 99.0
        )

        assertEquals(15, result.scoreBreakdown.riskQualityScore)
        assertTrue(result.warnings.any { it.contains("risk/reward") })
    }

    @Test
    fun `no history keeps evidence confidence low and prevents high conviction`() {
        val result = PersonalizedTradeScoringEngine.score(
            symbol = "AAPL",
            snapshot = snapshot(technicalScore = 95),
            records = emptyList(),
            patternProfile = emptyProfile,
            proposedEntryPrice = 100.0,
            proposedStopPrice = 98.0,
            proposedTargetPrice = 106.0
        )

        assertEquals(4, result.scoreBreakdown.evidenceConfidenceScore)
        assertNotEquals(AgentTradeDecision.HIGH_CONVICTION, result.decision)
        assertTrue(result.warnings.any { it.contains("similarity evidence is limited") })
    }

    private fun snapshot(
        technicalScore: Int,
        price: Double = 100.0,
        vwap: Double = 99.0,
        distanceFromVwapPercent: Double = 1.0
    ) = TechnicalSnapshot(
        timestamp = "2026-08-28T10:00:00Z",
        price = price,
        ema9 = 99.5,
        ema20 = 99.0,
        vwap = vwap,
        aboveEma9 = price > 99.5,
        aboveEma20 = price > 99.0,
        aboveVwap = price > vwap,
        volumeRatio = 1.5,
        breakout = false,
        failedBreakout = false,
        bullishLiquiditySweep = false,
        bearishLiquiditySweep = false,
        bullishStructure = true,
        bearishStructure = false,
        distanceFromVwapPercent = distanceFromVwapPercent,
        technicalScore = technicalScore,
        signals = listOf("Bullish structure")
    )
}
