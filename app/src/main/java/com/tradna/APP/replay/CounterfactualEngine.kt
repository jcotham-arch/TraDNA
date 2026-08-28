package com.tradna.APP.replay

import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.market.Candle
import com.tradna.APP.market.TechnicalSignalEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class CounterfactualOutcome {
    PROFIT,
    LOSS,
    FLAT,
    NO_ENTRY,
    STILL_OPEN,
    AMBIGUOUS
}

data class CounterfactualScenario(
    val id: String,
    val title: String,
    val description: String,

    val entryPrice: Double?,
    val exitPrice: Double?,

    val stopPrice: Double?,
    val targetPrice: Double?,

    val entryCandleIndex: Int?,
    val exitCandleIndex: Int?,

    val returnPercent: Double?,
    val profitPerShare: Double?,
    val estimatedPnl: Double?,

    val improvementVsActualPercent: Double?,
    val improvementVsActualDollars: Double?,

    val realizedR: Double?,

    val outcome: CounterfactualOutcome,

    val lesson: String
)

data class CounterfactualReport(
    val symbol: String,

    val actualReturnPercent: Double?,
    val actualRealizedPnl: Double,

    val referenceRiskPerShare: Double?,
    val referenceStopPrice: Double?,

    val scenarios: List<CounterfactualScenario>,

    val bestScenario: CounterfactualScenario?,
    val worstScenario: CounterfactualScenario?,

    val bestImprovementPercent: Double?,
    val bestImprovementDollars: Double?,

    val summary: String
)

object CounterfactualEngine {

    fun analyze(
        trade: TradeEpisode,
        candles: List<Candle>,
        historicalAnalysis: HistoricalTradeAnalysis? = null
    ): CounterfactualReport {

        if (candles.isEmpty()) {

            return CounterfactualReport(
                symbol = trade.symbol,
                actualReturnPercent = null,
                actualRealizedPnl = trade.realizedPnl,
                referenceRiskPerShare = null,
                referenceStopPrice = null,
                scenarios = emptyList(),
                bestScenario = null,
                worstScenario = null,
                bestImprovementPercent = null,
                bestImprovementDollars = null,
                summary =
                    "Historical market data is required before alternative scenarios can be tested."
            )
        }

        val actualEntry =
            trade.averageEntryPrice

        val actualExit =
            trade.averageExitPrice

        val quantity =
            trade.totalSharesBought

        val actualReturn =
            if (
                actualExit != null &&
                actualEntry > 0.0
            ) {

                (
                        (
                                actualExit -
                                        actualEntry
                                ) /
                                actualEntry
                        ) *
                        100.0

            } else {
                null
            }

        val entryIndex =
            historicalAnalysis
                ?.entryCandleIndex
                ?: findEntryIndex(
                    candles = candles,
                    entryPrice = actualEntry
                )

        if (
            entryIndex == null ||
            entryIndex !in candles.indices
        ) {

            return CounterfactualReport(
                symbol = trade.symbol,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl,
                referenceRiskPerShare = null,
                referenceStopPrice = null,
                scenarios = emptyList(),
                bestScenario = null,
                worstScenario = null,
                bestImprovementPercent = null,
                bestImprovementDollars = null,
                summary =
                    "TraDNA could not confidently reconstruct the historical entry candle."
            )
        }

        /*
         * Build a repeatable reference stop.
         *
         * We use the recent swing low before entry.
         *
         * If that creates an unrealistic or zero risk level,
         * we fall back to 2% below the historical entry.
         */
        val referenceStop =
            calculateReferenceStop(
                candles = candles,
                entryIndex = entryIndex,
                entryPrice = actualEntry
            )

        val referenceRisk =
            actualEntry -
                    referenceStop

        val scenarios =
            mutableListOf<CounterfactualScenario>()

        /*
         * =====================================================
         * ACTUAL TRADE
         * =====================================================
         */

        scenarios.add(
            actualScenario(
                trade = trade,
                actualReturn = actualReturn
            )
        )

        /*
         * =====================================================
         * FIXED 2R
         * =====================================================
         */

        scenarios.add(
            fixedTargetScenario(
                id = "fixed_2r",
                title = "2:1 Risk / Reward",
                description =
                    "Use the reconstructed historical entry, a recent swing-low stop, and a target equal to two times the amount risked.",
                entryPrice = actualEntry,
                entryIndex = entryIndex,
                stopPrice = referenceStop,
                targetPrice =
                    actualEntry +
                            (
                                    referenceRisk *
                                            2.0
                                    ),
                candles = candles,
                quantity = quantity,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl
            )
        )

        /*
         * =====================================================
         * FIXED 3R
         * =====================================================
         */

        scenarios.add(
            fixedTargetScenario(
                id = "fixed_3r",
                title = "3:1 Risk / Reward",
                description =
                    "Use the reconstructed historical entry and swing-low stop while allowing the position to seek a three-times-risk target.",
                entryPrice = actualEntry,
                entryIndex = entryIndex,
                stopPrice = referenceStop,
                targetPrice =
                    actualEntry +
                            (
                                    referenceRisk *
                                            3.0
                                    ),
                candles = candles,
                quantity = quantity,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl
            )
        )

        /*
         * =====================================================
         * EMA 9 EXIT
         * =====================================================
         */

        scenarios.add(
            indicatorExitScenario(
                id = "ema9_exit",
                title = "EMA 9 Trailing Exit",
                description =
                    "Keep the historical entry but stay in the trade until a candle closes below EMA 9.",
                entryPrice = actualEntry,
                entryIndex = entryIndex,
                candles = candles,
                quantity = quantity,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl,
                exitType =
                    IndicatorExitType.EMA9
            )
        )

        /*
         * =====================================================
         * EMA 20 EXIT
         * =====================================================
         */

        scenarios.add(
            indicatorExitScenario(
                id = "ema20_exit",
                title = "EMA 20 Trend Exit",
                description =
                    "Hold the historical entry until price closes below EMA 20, allowing more room for the trend to develop.",
                entryPrice = actualEntry,
                entryIndex = entryIndex,
                candles = candles,
                quantity = quantity,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl,
                exitType =
                    IndicatorExitType.EMA20
            )
        )

        /*
         * =====================================================
         * VWAP EXIT
         * =====================================================
         */

        scenarios.add(
            indicatorExitScenario(
                id = "vwap_exit",
                title = "VWAP Exit",
                description =
                    "Keep the historical entry while price remains above VWAP and exit after a close below VWAP.",
                entryPrice = actualEntry,
                entryIndex = entryIndex,
                candles = candles,
                quantity = quantity,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl,
                exitType =
                    IndicatorExitType.VWAP
            )
        )

        /*
         * =====================================================
         * 3% TRAILING STOP
         * =====================================================
         */

        scenarios.add(
            trailingStopScenario(
                id = "trailing_3_percent",
                title = "3% Trailing Stop",
                description =
                    "Allow the historical position to run while protecting gains with a stop that trails 3% below the highest price reached.",
                entryPrice = actualEntry,
                entryIndex = entryIndex,
                candles = candles,
                quantity = quantity,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl,
                trailingPercent = 3.0
            )
        )

        /*
         * =====================================================
         * WAIT FOR RETEST
         * =====================================================
         */

        scenarios.add(
            retestScenario(
                trade = trade,
                candles = candles,
                originalEntryIndex = entryIndex,
                quantity = quantity,
                actualReturnPercent = actualReturn,
                actualRealizedPnl = trade.realizedPnl
            )
        )

        val comparable =
            scenarios.filter {
                it.returnPercent != null
            }

        val best =
            comparable.maxByOrNull {
                it.returnPercent
                    ?: Double.NEGATIVE_INFINITY
            }

        val worst =
            comparable.minByOrNull {
                it.returnPercent
                    ?: Double.POSITIVE_INFINITY
            }

        val bestImprovementPercent =
            best
                ?.improvementVsActualPercent

        val bestImprovementDollars =
            best
                ?.improvementVsActualDollars

        return CounterfactualReport(
            symbol =
                trade.symbol,

            actualReturnPercent =
                actualReturn,

            actualRealizedPnl =
                trade.realizedPnl,

            referenceRiskPerShare =
                referenceRisk,

            referenceStopPrice =
                referenceStop,

            scenarios =
                scenarios,

            bestScenario =
                best,

            worstScenario =
                worst,

            bestImprovementPercent =
                bestImprovementPercent,

            bestImprovementDollars =
                bestImprovementDollars,

            summary =
                buildSummary(
                    best = best,
                    actualReturn = actualReturn
                )
        )
    }

    /*
     * =========================================================
     * ACTUAL TRADE
     * =========================================================
     */

    private fun actualScenario(
        trade: TradeEpisode,
        actualReturn: Double?
    ): CounterfactualScenario {

        val outcome =
            when {

                actualReturn == null ->
                    CounterfactualOutcome.STILL_OPEN

                actualReturn > 0.0 ->
                    CounterfactualOutcome.PROFIT

                actualReturn < 0.0 ->
                    CounterfactualOutcome.LOSS

                else ->
                    CounterfactualOutcome.FLAT
            }

        return CounterfactualScenario(
            id =
                "actual",

            title =
                "Your Actual Trade",

            description =
                "The historical Robinhood trade as it actually occurred.",

            entryPrice =
                trade.averageEntryPrice,

            exitPrice =
                trade.averageExitPrice,

            stopPrice =
                null,

            targetPrice =
                null,

            entryCandleIndex =
                null,

            exitCandleIndex =
                null,

            returnPercent =
                actualReturn,

            profitPerShare =
                if (
                    trade.averageExitPrice != null
                ) {

                    trade.averageExitPrice -
                            trade.averageEntryPrice

                } else {
                    null
                },

            estimatedPnl =
                trade.realizedPnl,

            improvementVsActualPercent =
                0.0,

            improvementVsActualDollars =
                0.0,

            realizedR =
                null,

            outcome =
                outcome,

            lesson =
                "This is the baseline against which TraDNA compares repeatable alternatives."
        )
    }

    /*
     * =========================================================
     * FIXED R:R TARGET
     * =========================================================
     */

    private fun fixedTargetScenario(
        id: String,
        title: String,
        description: String,

        entryPrice: Double,
        entryIndex: Int,

        stopPrice: Double,
        targetPrice: Double,

        candles: List<Candle>,

        quantity: Double,

        actualReturnPercent: Double?,
        actualRealizedPnl: Double
    ): CounterfactualScenario {

        var exitPrice: Double? =
            null

        var exitIndex: Int? =
            null

        var outcome =
            CounterfactualOutcome.STILL_OPEN

        val futureStart =
            (entryIndex + 1)
                .coerceAtMost(
                    candles.size
                )

        for (
        index in futureStart until candles.size
        ) {

            val candle =
                candles[index]

            val stopTouched =
                candle.low <=
                        stopPrice

            val targetTouched =
                candle.high >=
                        targetPrice

            if (
                stopTouched &&
                targetTouched
            ) {

                outcome =
                    CounterfactualOutcome.AMBIGUOUS

                exitIndex =
                    index

                break
            }

            if (stopTouched) {

                exitPrice =
                    stopPrice

                exitIndex =
                    index

                outcome =
                    CounterfactualOutcome.LOSS

                break
            }

            if (targetTouched) {

                exitPrice =
                    targetPrice

                exitIndex =
                    index

                outcome =
                    CounterfactualOutcome.PROFIT

                break
            }
        }

        if (
            exitPrice == null &&
            outcome != CounterfactualOutcome.AMBIGUOUS
        ) {

            exitPrice =
                candles
                    .last()
                    .close

            exitIndex =
                candles.lastIndex

            outcome =
                outcomeForReturn(
                    calculateReturnPercent(
                        entryPrice =
                            entryPrice,
                        exitPrice =
                            exitPrice
                    )
                )
        }

        val returnPercent =
            exitPrice
                ?.let {
                    calculateReturnPercent(
                        entryPrice =
                            entryPrice,
                        exitPrice =
                            it
                    )
                }

        val profitPerShare =
            exitPrice
                ?.let {
                    it -
                            entryPrice
                }

        val estimatedPnl =
            profitPerShare
                ?.let {
                    it *
                            quantity
                }

        val risk =
            entryPrice -
                    stopPrice

        val realizedR =
            if (
                risk > 0.0 &&
                exitPrice != null
            ) {

                (
                        exitPrice -
                                entryPrice
                        ) /
                        risk

            } else {
                null
            }

        return CounterfactualScenario(
            id =
                id,

            title =
                title,

            description =
                description,

            entryPrice =
                entryPrice,

            exitPrice =
                exitPrice,

            stopPrice =
                stopPrice,

            targetPrice =
                targetPrice,

            entryCandleIndex =
                entryIndex,

            exitCandleIndex =
                exitIndex,

            returnPercent =
                returnPercent,

            profitPerShare =
                profitPerShare,

            estimatedPnl =
                estimatedPnl,

            improvementVsActualPercent =
                improvementPercent(
                    scenarioReturn =
                        returnPercent,
                    actualReturn =
                        actualReturnPercent
                ),

            improvementVsActualDollars =
                improvementDollars(
                    scenarioPnl =
                        estimatedPnl,
                    actualPnl =
                        actualRealizedPnl
                ),

            realizedR =
                realizedR,

            outcome =
                outcome,

            lesson =
                when (outcome) {

                    CounterfactualOutcome.PROFIT ->
                        "A predefined risk/reward framework would have produced a profitable exit under this historical path."

                    CounterfactualOutcome.LOSS ->
                        "The structured stop would have limited the loss to the predefined risk level."

                    CounterfactualOutcome.AMBIGUOUS ->
                        "Stop and target occurred inside the same candle, so this timeframe cannot establish which happened first."

                    else ->
                        "The fixed target was not reached before the available historical data ended."
                }
        )
    }

    /*
     * =========================================================
     * INDICATOR-BASED EXITS
     * =========================================================
     */

    private enum class IndicatorExitType {
        EMA9,
        EMA20,
        VWAP
    }

    private fun indicatorExitScenario(
        id: String,
        title: String,
        description: String,

        entryPrice: Double,
        entryIndex: Int,

        candles: List<Candle>,

        quantity: Double,

        actualReturnPercent: Double?,
        actualRealizedPnl: Double,

        exitType: IndicatorExitType
    ): CounterfactualScenario {

        var exitPrice: Double? =
            null

        var exitIndex: Int? =
            null

        /*
         * Require at least one candle after entry.
         */
        for (
        index in
        (entryIndex + 1) until candles.size
        ) {

            val history =
                candles.subList(
                    0,
                    index + 1
                )

            val snapshot =
                TechnicalSignalEngine
                    .analyze(
                        history
                    )
                    ?: continue

            val current =
                candles[index]

            val shouldExit =
                when (exitType) {

                    IndicatorExitType.EMA9 -> {

                        snapshot.ema9
                            ?.let {
                                current.close < it
                            }
                            ?: false
                    }

                    IndicatorExitType.EMA20 -> {

                        snapshot.ema20
                            ?.let {
                                current.close < it
                            }
                            ?: false
                    }

                    IndicatorExitType.VWAP -> {

                        snapshot.vwap
                            ?.let {
                                current.close < it
                            }
                            ?: false
                    }
                }

            if (shouldExit) {

                exitPrice =
                    current.close

                exitIndex =
                    index

                break
            }
        }

        if (exitPrice == null) {

            exitPrice =
                candles
                    .last()
                    .close

            exitIndex =
                candles.lastIndex
        }

        val returnPercent =
            calculateReturnPercent(
                entryPrice =
                    entryPrice,
                exitPrice =
                    exitPrice
            )

        val profitPerShare =
            exitPrice -
                    entryPrice

        val estimatedPnl =
            profitPerShare *
                    quantity

        return CounterfactualScenario(
            id =
                id,

            title =
                title,

            description =
                description,

            entryPrice =
                entryPrice,

            exitPrice =
                exitPrice,

            stopPrice =
                null,

            targetPrice =
                null,

            entryCandleIndex =
                entryIndex,

            exitCandleIndex =
                exitIndex,

            returnPercent =
                returnPercent,

            profitPerShare =
                profitPerShare,

            estimatedPnl =
                estimatedPnl,

            improvementVsActualPercent =
                improvementPercent(
                    scenarioReturn =
                        returnPercent,
                    actualReturn =
                        actualReturnPercent
                ),

            improvementVsActualDollars =
                improvementDollars(
                    scenarioPnl =
                        estimatedPnl,
                    actualPnl =
                        actualRealizedPnl
                ),

            realizedR =
                null,

            outcome =
                outcomeForReturn(
                    returnPercent
                ),

            lesson =
                when (exitType) {

                    IndicatorExitType.EMA9 ->
                        "This tests whether allowing short-term trend structure to determine the exit would have captured more or less of the move."

                    IndicatorExitType.EMA20 ->
                        "This tests a slower trend-following exit that gives the position more room before declaring the move finished."

                    IndicatorExitType.VWAP ->
                        "This tests whether VWAP could have served as a repeatable intraday trend invalidation level."
                }
        )
    }

    /*
     * =========================================================
     * TRAILING STOP
     * =========================================================
     */

    private fun trailingStopScenario(
        id: String,
        title: String,
        description: String,

        entryPrice: Double,
        entryIndex: Int,

        candles: List<Candle>,

        quantity: Double,

        actualReturnPercent: Double?,
        actualRealizedPnl: Double,

        trailingPercent: Double
    ): CounterfactualScenario {

        var highestPrice =
            entryPrice

        var currentStop =
            entryPrice *
                    (
                            1.0 -
                                    trailingPercent /
                                    100.0
                            )

        var exitPrice: Double? =
            null

        var exitIndex: Int? =
            null

        for (
        index in
        (entryIndex + 1) until candles.size
        ) {

            val candle =
                candles[index]

            highestPrice =
                max(
                    highestPrice,
                    candle.high
                )

            currentStop =
                max(
                    currentStop,
                    highestPrice *
                            (
                                    1.0 -
                                            trailingPercent /
                                            100.0
                                    )
                )

            if (
                candle.low <=
                currentStop
            ) {

                exitPrice =
                    currentStop

                exitIndex =
                    index

                break
            }
        }

        if (exitPrice == null) {

            exitPrice =
                candles
                    .last()
                    .close

            exitIndex =
                candles.lastIndex
        }

        val returnPercent =
            calculateReturnPercent(
                entryPrice =
                    entryPrice,
                exitPrice =
                    exitPrice
            )

        val profitPerShare =
            exitPrice -
                    entryPrice

        val estimatedPnl =
            profitPerShare *
                    quantity

        return CounterfactualScenario(
            id =
                id,

            title =
                title,

            description =
                description,

            entryPrice =
                entryPrice,

            exitPrice =
                exitPrice,

            stopPrice =
                currentStop,

            targetPrice =
                null,

            entryCandleIndex =
                entryIndex,

            exitCandleIndex =
                exitIndex,

            returnPercent =
                returnPercent,

            profitPerShare =
                profitPerShare,

            estimatedPnl =
                estimatedPnl,

            improvementVsActualPercent =
                improvementPercent(
                    scenarioReturn =
                        returnPercent,
                    actualReturn =
                        actualReturnPercent
                ),

            improvementVsActualDollars =
                improvementDollars(
                    scenarioPnl =
                        estimatedPnl,
                    actualPnl =
                        actualRealizedPnl
                ),

            realizedR =
                null,

            outcome =
                outcomeForReturn(
                    returnPercent
                ),

            lesson =
                "This tests whether a mechanical trailing stop would have protected gains while allowing the position to continue moving in your favor."
        )
    }

    /*
     * =========================================================
     * WAIT FOR RETEST
     * =========================================================
     */

    private fun retestScenario(
        trade: TradeEpisode,
        candles: List<Candle>,
        originalEntryIndex: Int,

        quantity: Double,

        actualReturnPercent: Double?,
        actualRealizedPnl: Double
    ): CounterfactualScenario {

        val searchEnd =
            min(
                candles.size,
                originalEntryIndex +
                        12
            )

        var retestIndex: Int? =
            null

        var retestPrice: Double? =
            null

        /*
         * Look for a future candle that interacts with EMA 9
         * or VWAP and still closes above that level.
         *
         * This represents a simplified confirmation/retest model.
         */
        for (
        index in
        (originalEntryIndex + 1) until searchEnd
        ) {

            val history =
                candles.subList(
                    0,
                    index + 1
                )

            val snapshot =
                TechnicalSignalEngine
                    .analyze(
                        history
                    )
                    ?: continue

            val candle =
                candles[index]

            val ema9 =
                snapshot.ema9

            val vwap =
                snapshot.vwap

            val emaRetest =
                ema9 != null &&
                        candle.low <= ema9 &&
                        candle.close >= ema9

            val vwapRetest =
                vwap != null &&
                        candle.low <= vwap &&
                        candle.close >= vwap

            if (
                emaRetest ||
                vwapRetest
            ) {

                retestIndex =
                    index

                retestPrice =
                    candle.close

                break
            }
        }

        if (
            retestIndex == null ||
            retestPrice == null
        ) {

            return CounterfactualScenario(
                id =
                    "wait_for_retest",

                title =
                    "Wait for Retest",

                description =
                    "Delay entry until price retests EMA 9 or VWAP and closes back above the level.",

                entryPrice =
                    null,

                exitPrice =
                    null,

                stopPrice =
                    null,

                targetPrice =
                    null,

                entryCandleIndex =
                    null,

                exitCandleIndex =
                    null,

                returnPercent =
                    null,

                profitPerShare =
                    null,

                estimatedPnl =
                    null,

                improvementVsActualPercent =
                    null,

                improvementVsActualDollars =
                    null,

                realizedR =
                    null,

                outcome =
                    CounterfactualOutcome.NO_ENTRY,

                lesson =
                    "No qualifying retest occurred shortly after the historical entry. Waiting for confirmation would have kept TraDNA out of this trade."
            )
        }

        val retestStop =
            calculateReferenceStop(
                candles =
                    candles,
                entryIndex =
                    retestIndex,
                entryPrice =
                    retestPrice
            )

        val risk =
            retestPrice -
                    retestStop

        val target =
            retestPrice +
                    (
                            risk *
                                    2.0
                            )

        return fixedTargetScenario(
            id =
                "wait_for_retest",

            title =
                "Wait for Retest",

            description =
                "Delay entry until price retests EMA 9 or VWAP, then use a reconstructed swing-low stop and 2:1 target.",

            entryPrice =
                retestPrice,

            entryIndex =
                retestIndex,

            stopPrice =
                retestStop,

            targetPrice =
                target,

            candles =
                candles,

            quantity =
                quantity,

            actualReturnPercent =
                actualReturnPercent,

            actualRealizedPnl =
                actualRealizedPnl
        )
    }

    /*
     * =========================================================
     * REFERENCE STOP
     * =========================================================
     */

    private fun calculateReferenceStop(
        candles: List<Candle>,
        entryIndex: Int,
        entryPrice: Double
    ): Double {

        val start =
            (
                    entryIndex -
                            5
                    )
                .coerceAtLeast(
                    0
                )

        val end =
            (
                    entryIndex +
                            1
                    )
                .coerceAtMost(
                    candles.size
                )

        val recent =
            candles.subList(
                start,
                end
            )

        val swingLow =
            recent
                .minOfOrNull {
                    it.low
                }

        val fallback =
            entryPrice *
                    0.98

        if (
            swingLow == null ||
            swingLow >= entryPrice
        ) {

            return fallback
        }

        /*
         * Avoid an excessively large historical stop.
         *
         * If the swing low is more than 8% beneath entry,
         * use the simpler 2% fallback instead.
         */
        val riskPercent =
            (
                    (
                            entryPrice -
                                    swingLow
                            ) /
                            entryPrice
                    ) *
                    100.0

        return if (
            riskPercent >
            8.0
        ) {
            fallback
        } else {
            swingLow
        }
    }

    /*
     * =========================================================
     * ENTRY RECONSTRUCTION
     * =========================================================
     */

    private fun findEntryIndex(
        candles: List<Candle>,
        entryPrice: Double
    ): Int? {

        val direct =
            candles.indexOfFirst {

                entryPrice >=
                        it.low &&
                        entryPrice <=
                        it.high
            }

        if (direct >= 0) {

            return direct
        }

        return candles
            .indices
            .minByOrNull {
                    index ->

                abs(
                    candles[index]
                        .close -
                            entryPrice
                )
            }
    }

    /*
     * =========================================================
     * BASIC CALCULATIONS
     * =========================================================
     */

    private fun calculateReturnPercent(
        entryPrice: Double,
        exitPrice: Double
    ): Double {

        if (entryPrice == 0.0) {
            return 0.0
        }

        return (
                (
                        exitPrice -
                                entryPrice
                        ) /
                        entryPrice
                ) *
                100.0
    }

    private fun improvementPercent(
        scenarioReturn: Double?,
        actualReturn: Double?
    ): Double? {

        if (
            scenarioReturn == null ||
            actualReturn == null
        ) {

            return null
        }

        return scenarioReturn -
                actualReturn
    }

    private fun improvementDollars(
        scenarioPnl: Double?,
        actualPnl: Double
    ): Double? {

        if (scenarioPnl == null) {
            return null
        }

        return scenarioPnl -
                actualPnl
    }

    private fun outcomeForReturn(
        returnPercent: Double
    ): CounterfactualOutcome {

        return when {

            returnPercent > 0.0001 ->
                CounterfactualOutcome.PROFIT

            returnPercent < -0.0001 ->
                CounterfactualOutcome.LOSS

            else ->
                CounterfactualOutcome.FLAT
        }
    }

    /*
     * =========================================================
     * TEACHING SUMMARY
     * =========================================================
     */

    private fun buildSummary(
        best: CounterfactualScenario?,
        actualReturn: Double?
    ): String {

        if (
            best == null ||
            best.returnPercent == null
        ) {

            return "TraDNA did not find enough comparable historical information to rank alternative strategies."
        }

        if (
            best.id ==
            "actual"
        ) {

            return "Your historical execution performed as well as or better than the current standardized alternatives tested by TraDNA."
        }

        val improvement =
            if (
                actualReturn != null
            ) {

                best.returnPercent -
                        actualReturn

            } else {
                null
            }

        return if (
            improvement != null &&
            improvement > 0.0
        ) {

            "${best.title} produced the strongest historical result, improving the modeled return by ${formatPercentForSummary(improvement)} versus the actual trade."

        } else {

            "${best.title} produced the strongest result among the alternatives tested."
        }
    }

    private fun formatPercentForSummary(
        value: Double
    ): String {

        return if (
            value >= 0.0
        ) {

            String.format(
                java.util.Locale.US,
                "+%.2f%%",
                value
            )

        } else {

            String.format(
                java.util.Locale.US,
                "%.2f%%",
                value
            )
        }
    }
}