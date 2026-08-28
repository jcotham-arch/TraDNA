package com.tradna.APP.replay

import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.market.Candle
import com.tradna.APP.market.TechnicalSignalEngine
import com.tradna.APP.market.TechnicalSnapshot
import kotlin.math.abs
import kotlin.math.max

data class HistoricalTradeAnalysis(
    val symbol: String,

    // Actual historical trade
    val actualEntryPrice: Double,
    val actualExitPrice: Double?,
    val actualReturnPercent: Double?,
    val actualRealizedPnl: Double,

    // Market position
    val entryCandleIndex: Int?,
    val exitCandleIndex: Int?,
    val entryMarketPrice: Double?,
    val exitMarketPrice: Double?,

    // Technical condition at entry
    val entryTechnicalScore: Int?,
    val entryVwap: Double?,
    val entryEma9: Double?,
    val entryEma20: Double?,
    val entryRelativeVolume: Double?,
    val entryDistanceFromVwapPercent: Double?,
    val entrySignals: List<String>,

    // Technical condition at exit
    val exitTechnicalScore: Int?,
    val exitVwap: Double?,
    val exitEma9: Double?,
    val exitEma20: Double?,
    val exitRelativeVolume: Double?,
    val exitSignals: List<String>,

    // Excursion analysis
    val highestPriceAfterEntry: Double?,
    val lowestPriceAfterEntry: Double?,
    val maximumFavorableExcursionPercent: Double?,
    val maximumAdverseExcursionPercent: Double?,

    // Efficiency analysis
    val entryEfficiencyScore: Int?,
    val exitEfficiencyScore: Int?,
    val tradeEfficiencyScore: Int?,

    // Post-exit opportunity
    val highestPriceAfterExit: Double?,
    val missedUpsidePercent: Double?,
    val missedUpsidePerShare: Double?,

    // Teaching layer
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<String>,
    val lessonTitle: String,
    val lessonSummary: String
)

object HistoricalTradeAnalysisEngine {

    fun analyze(
        trade: TradeEpisode,
        candles: List<Candle>
    ): HistoricalTradeAnalysis {

        if (candles.isEmpty()) {
            return emptyAnalysis(
                trade = trade,
                reason =
                    "Historical market candles were not available for this trade."
            )
        }

        val entryPrice =
            trade.averageEntryPrice

        val exitPrice =
            trade.averageExitPrice

        /*
         * We first attempt to locate the candle containing
         * the historical entry price.
         *
         * This is intentionally price-based for the first
         * version because Robinhood activity imports may not
         * always provide execution timestamps with enough
         * precision to identify the exact intraday candle.
         */
        val entryIndex =
            findBestEntryCandle(
                candles = candles,
                entryPrice = entryPrice
            )

        val exitIndex =
            if (exitPrice != null) {

                findBestExitCandle(
                    candles = candles,
                    exitPrice = exitPrice,
                    minimumIndex =
                        entryIndex ?: 0
                )

            } else {
                null
            }

        val entrySnapshot =
            snapshotAt(
                candles = candles,
                index = entryIndex
            )

        val exitSnapshot =
            snapshotAt(
                candles = candles,
                index = exitIndex
            )

        val actualReturnPercent =
            if (
                exitPrice != null &&
                entryPrice != 0.0
            ) {

                (
                        (
                                exitPrice -
                                        entryPrice
                                ) /
                                entryPrice
                        ) *
                        100.0

            } else {
                null
            }

        /*
         * Analyze all candles from entry forward.
         */
        val afterEntry =
            if (
                entryIndex != null &&
                entryIndex in candles.indices
            ) {

                candles.subList(
                    entryIndex,
                    candles.size
                )

            } else {
                emptyList()
            }

        val highestAfterEntry =
            afterEntry
                .maxOfOrNull {
                    it.high
                }

        val lowestAfterEntry =
            afterEntry
                .minOfOrNull {
                    it.low
                }

        val mfePercent =
            if (
                highestAfterEntry != null &&
                entryPrice != 0.0
            ) {

                (
                        (
                                highestAfterEntry -
                                        entryPrice
                                ) /
                                entryPrice
                        ) *
                        100.0

            } else {
                null
            }

        val maePercent =
            if (
                lowestAfterEntry != null &&
                entryPrice != 0.0
            ) {

                (
                        (
                                lowestAfterEntry -
                                        entryPrice
                                ) /
                                entryPrice
                        ) *
                        100.0

            } else {
                null
            }

        /*
         * Post-exit analysis tells us how much further
         * price moved after the user actually sold.
         */
        val afterExit =
            if (
                exitIndex != null &&
                exitIndex in candles.indices
            ) {

                candles.subList(
                    exitIndex,
                    candles.size
                )

            } else {
                emptyList()
            }

        val highestAfterExit =
            afterExit
                .maxOfOrNull {
                    it.high
                }

        val missedUpsidePerShare =
            if (
                exitPrice != null &&
                highestAfterExit != null
            ) {

                max(
                    0.0,
                    highestAfterExit -
                            exitPrice
                )

            } else {
                null
            }

        val missedUpsidePercent =
            if (
                exitPrice != null &&
                exitPrice != 0.0 &&
                highestAfterExit != null
            ) {

                max(
                    0.0,
                    (
                            (
                                    highestAfterExit -
                                            exitPrice
                                    ) /
                                    exitPrice
                            ) *
                            100.0
                )

            } else {
                null
            }

        val entryEfficiency =
            calculateEntryEfficiency(
                entryPrice =
                    entryPrice,
                entryIndex =
                    entryIndex,
                candles =
                    candles
            )

        val exitEfficiency =
            calculateExitEfficiency(
                entryPrice =
                    entryPrice,
                exitPrice =
                    exitPrice,
                exitIndex =
                    exitIndex,
                candles =
                    candles
            )

        val tradeEfficiency =
            calculateTradeEfficiency(
                entryEfficiency =
                    entryEfficiency,
                exitEfficiency =
                    exitEfficiency
            )

        val strengths =
            buildStrengths(
                trade =
                    trade,
                entrySnapshot =
                    entrySnapshot,
                actualReturnPercent =
                    actualReturnPercent,
                mfePercent =
                    mfePercent
            )

        val weaknesses =
            buildWeaknesses(
                entryPrice =
                    entryPrice,
                exitPrice =
                    exitPrice,
                entrySnapshot =
                    entrySnapshot,
                actualReturnPercent =
                    actualReturnPercent,
                missedUpsidePercent =
                    missedUpsidePercent,
                maePercent =
                    maePercent
            )

        val recommendations =
            buildRecommendations(
                entrySnapshot =
                    entrySnapshot,
                exitSnapshot =
                    exitSnapshot,
                entryEfficiency =
                    entryEfficiency,
                exitEfficiency =
                    exitEfficiency,
                missedUpsidePercent =
                    missedUpsidePercent,
                maePercent =
                    maePercent
            )

        val lesson =
            buildLesson(
                actualReturnPercent =
                    actualReturnPercent,
                entryEfficiency =
                    entryEfficiency,
                exitEfficiency =
                    exitEfficiency,
                missedUpsidePercent =
                    missedUpsidePercent,
                entrySnapshot =
                    entrySnapshot
            )

        return HistoricalTradeAnalysis(
            symbol =
                trade.symbol,

            actualEntryPrice =
                entryPrice,

            actualExitPrice =
                exitPrice,

            actualReturnPercent =
                actualReturnPercent,

            actualRealizedPnl =
                trade.realizedPnl,

            entryCandleIndex =
                entryIndex,

            exitCandleIndex =
                exitIndex,

            entryMarketPrice =
                entryIndex
                    ?.let {
                        candles
                            .getOrNull(it)
                            ?.close
                    },

            exitMarketPrice =
                exitIndex
                    ?.let {
                        candles
                            .getOrNull(it)
                            ?.close
                    },

            entryTechnicalScore =
                entrySnapshot
                    ?.technicalScore,

            entryVwap =
                entrySnapshot
                    ?.vwap,

            entryEma9 =
                entrySnapshot
                    ?.ema9,

            entryEma20 =
                entrySnapshot
                    ?.ema20,

            entryRelativeVolume =
                entrySnapshot
                    ?.volumeRatio,

            entryDistanceFromVwapPercent =
                entrySnapshot
                    ?.distanceFromVwapPercent,

            entrySignals =
                entrySnapshot
                    ?.signals
                    ?: emptyList(),

            exitTechnicalScore =
                exitSnapshot
                    ?.technicalScore,

            exitVwap =
                exitSnapshot
                    ?.vwap,

            exitEma9 =
                exitSnapshot
                    ?.ema9,

            exitEma20 =
                exitSnapshot
                    ?.ema20,

            exitRelativeVolume =
                exitSnapshot
                    ?.volumeRatio,

            exitSignals =
                exitSnapshot
                    ?.signals
                    ?: emptyList(),

            highestPriceAfterEntry =
                highestAfterEntry,

            lowestPriceAfterEntry =
                lowestAfterEntry,

            maximumFavorableExcursionPercent =
                mfePercent,

            maximumAdverseExcursionPercent =
                maePercent,

            entryEfficiencyScore =
                entryEfficiency,

            exitEfficiencyScore =
                exitEfficiency,

            tradeEfficiencyScore =
                tradeEfficiency,

            highestPriceAfterExit =
                highestAfterExit,

            missedUpsidePercent =
                missedUpsidePercent,

            missedUpsidePerShare =
                missedUpsidePerShare,

            strengths =
                strengths,

            weaknesses =
                weaknesses,

            recommendations =
                recommendations,

            lessonTitle =
                lesson.first,

            lessonSummary =
                lesson.second
        )
    }

    /*
     * ENTRY CANDLE
     *
     * We prefer a candle whose range actually contains
     * the historical execution price.
     */
    private fun findBestEntryCandle(
        candles: List<Candle>,
        entryPrice: Double
    ): Int? {

        val directMatch =
            candles.indexOfFirst {
                entryPrice >= it.low &&
                        entryPrice <= it.high
            }

        if (directMatch >= 0) {
            return directMatch
        }

        /*
         * If no candle contains the execution price,
         * use the candle whose close is nearest.
         */
        return candles
            .indices
            .minByOrNull { index ->

                abs(
                    candles[index].close -
                            entryPrice
                )
            }
    }

    private fun findBestExitCandle(
        candles: List<Candle>,
        exitPrice: Double,
        minimumIndex: Int
    ): Int? {

        if (candles.isEmpty()) {
            return null
        }

        val safeStart =
            minimumIndex
                .coerceIn(
                    0,
                    candles.lastIndex
                )

        for (
        index in safeStart until candles.size
        ) {

            val candle =
                candles[index]

            if (
                exitPrice >= candle.low &&
                exitPrice <= candle.high
            ) {

                return index
            }
        }

        return (
                safeStart until candles.size
                )
            .minByOrNull { index ->

                abs(
                    candles[index].close -
                            exitPrice
                )
            }
    }

    /*
     * Generate a technical snapshot using ONLY information
     * that existed up to that candle.
     *
     * No future candles are passed into the technical
     * detector.
     */
    private fun snapshotAt(
        candles: List<Candle>,
        index: Int?
    ): TechnicalSnapshot? {

        if (
            index == null ||
            index !in candles.indices
        ) {
            return null
        }

        val visibleHistory =
            candles.subList(
                0,
                index + 1
            )

        return TechnicalSignalEngine
            .analyze(
                visibleHistory
            )
    }

    /*
     * Entry efficiency is NOT "did you buy the absolute low?"
     *
     * We examine a limited window around the entry to avoid
     * rewarding impossible hindsight.
     */
    private fun calculateEntryEfficiency(
        entryPrice: Double,
        entryIndex: Int?,
        candles: List<Candle>
    ): Int? {

        if (
            entryIndex == null ||
            entryIndex !in candles.indices ||
            entryPrice <= 0.0
        ) {
            return null
        }

        val start =
            (entryIndex - 3)
                .coerceAtLeast(0)

        val end =
            (entryIndex + 4)
                .coerceAtMost(
                    candles.size
                )

        val window =
            candles.subList(
                start,
                end
            )

        val localLow =
            window
                .minOfOrNull {
                    it.low
                }
                ?: return null

        val localHigh =
            window
                .maxOfOrNull {
                    it.high
                }
                ?: return null

        val range =
            localHigh -
                    localLow

        if (range <= 0.0) {
            return 100
        }

        /*
         * For a long trade, lower entries are more efficient.
         */
        val position =
            (
                    entryPrice -
                            localLow
                    ) /
                    range

        return (
                100.0 -
                        (
                                position *
                                        100.0
                                )
                )
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun calculateExitEfficiency(
        entryPrice: Double,
        exitPrice: Double?,
        exitIndex: Int?,
        candles: List<Candle>
    ): Int? {

        if (
            exitPrice == null ||
            exitIndex == null ||
            exitIndex !in candles.indices ||
            entryPrice <= 0.0
        ) {
            return null
        }

        /*
         * Limited post-exit window.
         *
         * We intentionally avoid comparing the exit against
         * the highest price days or weeks later.
         */
        val end =
            (exitIndex + 7)
                .coerceAtMost(
                    candles.size
                )

        val window =
            candles.subList(
                exitIndex,
                end
            )

        val bestReasonableExit =
            window
                .maxOfOrNull {
                    it.high
                }
                ?: return null

        val availableMove =
            bestReasonableExit -
                    entryPrice

        if (availableMove <= 0.0) {

            return if (
                exitPrice >= entryPrice
            ) {
                100
            } else {
                50
            }
        }

        val capturedMove =
            exitPrice -
                    entryPrice

        return (
                (
                        capturedMove /
                                availableMove
                        ) *
                        100.0
                )
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun calculateTradeEfficiency(
        entryEfficiency: Int?,
        exitEfficiency: Int?
    ): Int? {

        val values =
            listOfNotNull(
                entryEfficiency,
                exitEfficiency
            )

        if (values.isEmpty()) {
            return null
        }

        return values
            .average()
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun buildStrengths(
        trade: TradeEpisode,
        entrySnapshot: TechnicalSnapshot?,
        actualReturnPercent: Double?,
        mfePercent: Double?
    ): List<String> {

        val strengths =
            mutableListOf<String>()

        if (
            actualReturnPercent != null &&
            actualReturnPercent > 0.0
        ) {

            strengths.add(
                "The trade produced a positive realized return."
            )
        }

        if (
            trade.realizedPnl > 0.0
        ) {

            strengths.add(
                "The position generated positive realized P&L."
            )
        }

        if (
            entrySnapshot != null
        ) {

            if (
                entrySnapshot.technicalScore >= 70
            ) {

                strengths.add(
                    "The entry occurred during a relatively strong technical environment."
                )
            }

            val price =
                entrySnapshot.price

            val vwap =
                entrySnapshot.vwap

            if (
                vwap != null &&
                price > vwap
            ) {

                strengths.add(
                    "Price was above VWAP at the reconstructed entry."
                )
            }

            val ema9 =
                entrySnapshot.ema9

            val ema20 =
                entrySnapshot.ema20

            if (
                ema9 != null &&
                ema20 != null &&
                price > ema9 &&
                ema9 > ema20
            ) {

                strengths.add(
                    "Short-term EMA structure was bullish at entry."
                )
            }

            if (
                entrySnapshot.volumeRatio != null &&
                entrySnapshot.volumeRatio >= 1.5
            ) {

                strengths.add(
                    "Relative volume was elevated at entry."
                )
            }

            if (
                entrySnapshot.signals.isNotEmpty()
            ) {

                strengths.add(
                    "TraDNA detected technical confirmation near the entry."
                )
            }
        }

        if (
            mfePercent != null &&
            mfePercent >= 5.0
        ) {

            strengths.add(
                "The trade developed meaningful favorable movement after entry."
            )
        }

        if (strengths.isEmpty()) {

            strengths.add(
                "This trade provides useful evidence for improving the decision process."
            )
        }

        return strengths
    }

    private fun buildWeaknesses(
        entryPrice: Double,
        exitPrice: Double?,
        entrySnapshot: TechnicalSnapshot?,
        actualReturnPercent: Double?,
        missedUpsidePercent: Double?,
        maePercent: Double?
    ): List<String> {

        val weaknesses =
            mutableListOf<String>()

        if (
            actualReturnPercent != null &&
            actualReturnPercent < 0.0
        ) {

            weaknesses.add(
                "The historical trade closed below the average entry price."
            )
        }

        if (
            entrySnapshot != null
        ) {

            if (
                entrySnapshot.technicalScore <= 40
            ) {

                weaknesses.add(
                    "The reconstructed entry occurred during a weak technical environment."
                )
            }

            val vwap =
                entrySnapshot.vwap

            if (
                vwap != null &&
                entryPrice < vwap
            ) {

                weaknesses.add(
                    "The entry was below VWAP, reducing immediate long-side confirmation."
                )
            }

            val distance =
                entrySnapshot
                    .distanceFromVwapPercent

            if (
                distance != null &&
                distance > 3.0
            ) {

                weaknesses.add(
                    "The entry was extended more than 3% above VWAP."
                )
            }
        }

        if (
            maePercent != null &&
            maePercent <= -5.0
        ) {

            weaknesses.add(
                "The position experienced substantial adverse movement after entry."
            )
        }

        if (
            exitPrice != null &&
            missedUpsidePercent != null &&
            missedUpsidePercent >= 5.0
        ) {

            weaknesses.add(
                "Price continued materially higher after the historical exit."
            )
        }

        if (weaknesses.isEmpty()) {

            weaknesses.add(
                "No major weakness was identified by the current rule set."
            )
        }

        return weaknesses
    }

    private fun buildRecommendations(
        entrySnapshot: TechnicalSnapshot?,
        exitSnapshot: TechnicalSnapshot?,
        entryEfficiency: Int?,
        exitEfficiency: Int?,
        missedUpsidePercent: Double?,
        maePercent: Double?
    ): List<String> {

        val recommendations =
            mutableListOf<String>()

        if (
            entryEfficiency != null &&
            entryEfficiency < 50
        ) {

            recommendations.add(
                "Practice waiting for a better-priced pullback or retest instead of entering after price becomes extended."
            )
        }

        if (
            entrySnapshot != null &&
            entrySnapshot.technicalScore < 60
        ) {

            recommendations.add(
                "Require additional technical confirmation before taking similar long entries."
            )
        }

        if (
            entrySnapshot?.volumeRatio != null &&
            entrySnapshot.volumeRatio < 1.0
        ) {

            recommendations.add(
                "Compare future entries with relative volume before committing capital."
            )
        }

        if (
            exitEfficiency != null &&
            exitEfficiency < 50
        ) {

            recommendations.add(
                "Test structure-based exits instead of closing the entire position immediately."
            )
        }

        if (
            missedUpsidePercent != null &&
            missedUpsidePercent >= 5.0
        ) {

            recommendations.add(
                "Replay this trade using an EMA 9, VWAP, or trailing-stop exit to study whether more of the move could have been captured systematically."
            )
        }

        if (
            exitSnapshot != null &&
            exitSnapshot.ema9 != null &&
            exitSnapshot.price >
            exitSnapshot.ema9
        ) {

            recommendations.add(
                "The reconstructed exit occurred while price remained above EMA 9. Test whether waiting for an EMA 9 failure improves similar exits."
            )
        }

        if (
            maePercent != null &&
            maePercent <= -5.0
        ) {

            recommendations.add(
                "Define the invalidation level before entry so adverse movement is controlled by a repeatable rule."
            )
        }

        if (recommendations.isEmpty()) {

            recommendations.add(
                "Preserve the process used on this trade and compare it with additional historical examples before changing the strategy."
            )
        }

        return recommendations
    }

    private fun buildLesson(
        actualReturnPercent: Double?,
        entryEfficiency: Int?,
        exitEfficiency: Int?,
        missedUpsidePercent: Double?,
        entrySnapshot: TechnicalSnapshot?
    ): Pair<String, String> {

        if (
            missedUpsidePercent != null &&
            missedUpsidePercent >= 5.0 &&
            exitEfficiency != null &&
            exitEfficiency < 60
        ) {

            return Pair(
                "Study Exit Discipline",
                "This trade continued materially after the historical exit. Practice rule-based trailing exits and compare them with the actual result."
            )
        }

        if (
            entryEfficiency != null &&
            entryEfficiency < 50
        ) {

            return Pair(
                "Improve Entry Location",
                "The reconstructed entry was relatively inefficient inside its local price range. Practice waiting for pullbacks, retests, or clearer confirmation."
            )
        }

        if (
            entrySnapshot != null &&
            entrySnapshot.technicalScore < 50
        ) {

            return Pair(
                "Demand More Confirmation",
                "The technical environment around entry was relatively weak. Focus on alignment between trend, VWAP, moving averages, and volume."
            )
        }

        if (
            actualReturnPercent != null &&
            actualReturnPercent > 0.0
        ) {

            return Pair(
                "Identify What Was Repeatable",
                "The trade was profitable. The next objective is separating repeatable process quality from a favorable outcome."
            )
        }

        return Pair(
            "Reconstruct the Decision",
            "Use this trade to identify which observable conditions supported the decision and which conditions should change your behavior next time."
        )
    }

    private fun emptyAnalysis(
        trade: TradeEpisode,
        reason: String
    ): HistoricalTradeAnalysis {

        return HistoricalTradeAnalysis(
            symbol =
                trade.symbol,

            actualEntryPrice =
                trade.averageEntryPrice,

            actualExitPrice =
                trade.averageExitPrice,

            actualReturnPercent =
                null,

            actualRealizedPnl =
                trade.realizedPnl,

            entryCandleIndex =
                null,

            exitCandleIndex =
                null,

            entryMarketPrice =
                null,

            exitMarketPrice =
                null,

            entryTechnicalScore =
                null,

            entryVwap =
                null,

            entryEma9 =
                null,

            entryEma20 =
                null,

            entryRelativeVolume =
                null,

            entryDistanceFromVwapPercent =
                null,

            entrySignals =
                emptyList(),

            exitTechnicalScore =
                null,

            exitVwap =
                null,

            exitEma9 =
                null,

            exitEma20 =
                null,

            exitRelativeVolume =
                null,

            exitSignals =
                emptyList(),

            highestPriceAfterEntry =
                null,

            lowestPriceAfterEntry =
                null,

            maximumFavorableExcursionPercent =
                null,

            maximumAdverseExcursionPercent =
                null,

            entryEfficiencyScore =
                null,

            exitEfficiencyScore =
                null,

            tradeEfficiencyScore =
                null,

            highestPriceAfterExit =
                null,

            missedUpsidePercent =
                null,

            missedUpsidePerShare =
                null,

            strengths =
                emptyList(),

            weaknesses =
                listOf(reason),

            recommendations =
                listOf(
                    "Load historical market data before generating a complete trade review."
                ),

            lessonTitle =
                "Market Data Required",

            lessonSummary =
                reason
        )
    }
}