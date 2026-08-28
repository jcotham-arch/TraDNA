package com.tradna.APP.replay

import com.tradna.APP.market.Candle
import kotlin.math.round

enum class ReplayOutcome {
    TARGET_HIT,
    STOP_HIT,
    BOTH_HIT_SAME_CANDLE,
    ENTRY_NOT_TRIGGERED,
    OPEN_AT_END,
    NO_TRADE_PLAN
}

data class ReplayGrade(
    val outcome: ReplayOutcome,

    val entryTriggered: Boolean,
    val entryPrice: Double?,
    val entryCandleIndex: Int?,

    val stopPrice: Double?,
    val targetPrice: Double?,

    val exitPrice: Double?,
    val exitCandleIndex: Int?,

    val riskPerShare: Double?,
    val rewardPerShare: Double?,
    val plannedRiskReward: Double?,
    val realizedRMultiple: Double?,

    val maxFavorableExcursionPercent: Double?,
    val maxAdverseExcursionPercent: Double?,

    val candlesUntilEntry: Int?,
    val candlesInTrade: Int?,

    val score: Int,
    val notes: List<String>
)

object ReplayScoringEngine {

    fun grade(
        decision: ReplayDecision,
        allCandles: List<Candle>
    ): ReplayGrade {

        /*
         * Replay scoring v1 grades long BUY trades only.
         */
        if (decision.choice != ReplayChoice.BUY) {

            return noPlanGrade(
                decision = decision,
                note = "Only BUY trade plans are graded in Replay Scoring v1."
            )
        }

        /*
         * Convert nullable saved values into definite Double
         * values immediately.
         *
         * After these three lines, plannedEntry, plannedStop,
         * and plannedTarget are all NON-NULL Double values.
         */
        val plannedEntry: Double =
            decision.plannedEntry
                ?: return noPlanGrade(
                    decision = decision,
                    note = "No planned entry price was recorded."
                )

        val plannedStop: Double =
            decision.plannedStop
                ?: return noPlanGrade(
                    decision = decision,
                    note = "No planned stop price was recorded."
                )

        val plannedTarget: Double =
            decision.plannedTarget
                ?: return noPlanGrade(
                    decision = decision,
                    note = "No planned target price was recorded."
                )

        if (allCandles.isEmpty()) {

            return emptyGrade(
                entry = plannedEntry,
                stop = plannedStop,
                target = plannedTarget,
                note = "No replay candles were available."
            )
        }

        val decisionIndex =
            (decision.candleNumber - 1)
                .coerceAtLeast(0)

        if (decisionIndex >= allCandles.size) {

            return emptyGrade(
                entry = plannedEntry,
                stop = plannedStop,
                target = plannedTarget,
                note =
                    "The saved decision candle is outside the available replay history."
            )
        }

        /*
         * Validate long-trade structure.
         *
         * Entry
         *   ↓
         * Stop below entry
         *
         * Target above entry
         */
        val riskPerShare: Double =
            plannedEntry - plannedStop

        val rewardPerShare: Double =
            plannedTarget - plannedEntry

        if (
            riskPerShare <= 0.0 ||
            rewardPerShare <= 0.0
        ) {

            return emptyGrade(
                entry = plannedEntry,
                stop = plannedStop,
                target = plannedTarget,
                note =
                    "Invalid long trade plan. Stop must be below entry and target must be above entry."
            )
        }

        val plannedRiskReward: Double =
            rewardPerShare /
                    riskPerShare

        /*
         * Do not execute inside the candle where the
         * decision was made.
         *
         * Future evaluation begins on the NEXT candle.
         */
        val futureStart =
            decisionIndex + 1

        if (futureStart >= allCandles.size) {

            return ReplayGrade(
                outcome =
                    ReplayOutcome.ENTRY_NOT_TRIGGERED,

                entryTriggered =
                    false,

                entryPrice =
                    plannedEntry,

                entryCandleIndex =
                    null,

                stopPrice =
                    plannedStop,

                targetPrice =
                    plannedTarget,

                exitPrice =
                    null,

                exitCandleIndex =
                    null,

                riskPerShare =
                    riskPerShare,

                rewardPerShare =
                    rewardPerShare,

                plannedRiskReward =
                    plannedRiskReward,

                realizedRMultiple =
                    null,

                maxFavorableExcursionPercent =
                    null,

                maxAdverseExcursionPercent =
                    null,

                candlesUntilEntry =
                    null,

                candlesInTrade =
                    null,

                score =
                    0,

                notes =
                    listOf(
                        "No future candles were available after the decision."
                    )
            )
        }

        /*
         * Find the first future candle that trades
         * through the planned entry.
         */
        var foundEntryIndex: Int? =
            null

        for (
        index in futureStart until allCandles.size
        ) {

            val candle =
                allCandles[index]

            val entryTouched =
                plannedEntry >= candle.low &&
                        plannedEntry <= candle.high

            if (entryTouched) {

                foundEntryIndex =
                    index

                break
            }
        }

        /*
         * Entry never triggered.
         */
        if (foundEntryIndex == null) {

            return ReplayGrade(
                outcome =
                    ReplayOutcome.ENTRY_NOT_TRIGGERED,

                entryTriggered =
                    false,

                entryPrice =
                    plannedEntry,

                entryCandleIndex =
                    null,

                stopPrice =
                    plannedStop,

                targetPrice =
                    plannedTarget,

                exitPrice =
                    null,

                exitCandleIndex =
                    null,

                riskPerShare =
                    riskPerShare,

                rewardPerShare =
                    rewardPerShare,

                plannedRiskReward =
                    plannedRiskReward,

                realizedRMultiple =
                    null,

                maxFavorableExcursionPercent =
                    null,

                maxAdverseExcursionPercent =
                    null,

                candlesUntilEntry =
                    null,

                candlesInTrade =
                    null,

                score =
                    25,

                notes =
                    listOf(
                        "The planned entry price was never reached after the decision."
                    )
            )
        }

        /*
         * Force a definite Int from this point forward.
         */
        val entryIndex: Int =
            foundEntryIndex

        var highestPrice: Double =
            plannedEntry

        var lowestPrice: Double =
            plannedEntry

        var exitIndex: Int? =
            null

        var exitPrice: Double? =
            null

        var outcome =
            ReplayOutcome.OPEN_AT_END

        /*
         * Walk forward from the actual entry candle.
         */
        for (
        index in entryIndex until allCandles.size
        ) {

            val candle =
                allCandles[index]

            highestPrice =
                maxOf(
                    highestPrice,
                    candle.high
                )

            lowestPrice =
                minOf(
                    lowestPrice,
                    candle.low
                )

            val stopTouched =
                candle.low <=
                        plannedStop

            val targetTouched =
                candle.high >=
                        plannedTarget

            /*
             * OHLC alone cannot tell us which price was
             * reached first when both occur in one candle.
             */
            if (
                stopTouched &&
                targetTouched
            ) {

                outcome =
                    ReplayOutcome
                        .BOTH_HIT_SAME_CANDLE

                exitIndex =
                    index

                exitPrice =
                    null

                break
            }

            if (stopTouched) {

                outcome =
                    ReplayOutcome.STOP_HIT

                exitIndex =
                    index

                exitPrice =
                    plannedStop

                break
            }

            if (targetTouched) {

                outcome =
                    ReplayOutcome.TARGET_HIT

                exitIndex =
                    index

                exitPrice =
                    plannedTarget

                break
            }
        }

        /*
         * Maximum Favorable Excursion.
         */
        val mfePercent: Double? =
            if (plannedEntry != 0.0) {

                (
                        (
                                highestPrice -
                                        plannedEntry
                                ) /
                                plannedEntry
                        ) *
                        100.0

            } else {
                null
            }

        /*
         * Maximum Adverse Excursion.
         */
        val maePercent: Double? =
            if (plannedEntry != 0.0) {

                (
                        (
                                lowestPrice -
                                        plannedEntry
                                ) /
                                plannedEntry
                        ) *
                        100.0

            } else {
                null
            }

        /*
         * Realized result expressed in R.
         */
        val realizedR: Double? =
            when (outcome) {

                ReplayOutcome.TARGET_HIT ->

                    plannedRiskReward

                ReplayOutcome.STOP_HIT ->

                    -1.0

                ReplayOutcome.OPEN_AT_END -> {

                    val lastClose =
                        allCandles
                            .last()
                            .close

                    (
                            lastClose -
                                    plannedEntry
                            ) /
                            riskPerShare
                }

                ReplayOutcome
                    .BOTH_HIT_SAME_CANDLE ->

                    null

                ReplayOutcome
                    .ENTRY_NOT_TRIGGERED ->

                    null

                ReplayOutcome
                    .NO_TRADE_PLAN ->

                    null
            }

        val score =
            calculateScore(
                outcome =
                    outcome,

                plannedRR =
                    plannedRiskReward,

                realizedR =
                    realizedR
            )

        val notes =
            buildNotes(
                outcome =
                    outcome,

                plannedRR =
                    plannedRiskReward,

                mfePercent =
                    mfePercent,

                maePercent =
                    maePercent
            )

        /*
         * Calculate trade duration.
         *
         * Kotlin now only deals with definite Int values
         * for entryIndex.
         */
        val candlesInTrade: Int =
            if (exitIndex != null) {

                exitIndex -
                        entryIndex +
                        1

            } else {

                allCandles.size -
                        entryIndex
            }

        val candlesUntilEntry: Int =
            entryIndex -
                    decisionIndex

        return ReplayGrade(
            outcome =
                outcome,

            entryTriggered =
                true,

            entryPrice =
                plannedEntry,

            entryCandleIndex =
                entryIndex,

            stopPrice =
                plannedStop,

            targetPrice =
                plannedTarget,

            exitPrice =
                exitPrice,

            exitCandleIndex =
                exitIndex,

            riskPerShare =
                riskPerShare,

            rewardPerShare =
                rewardPerShare,

            plannedRiskReward =
                plannedRiskReward,

            realizedRMultiple =
                realizedR,

            maxFavorableExcursionPercent =
                mfePercent,

            maxAdverseExcursionPercent =
                maePercent,

            candlesUntilEntry =
                candlesUntilEntry,

            candlesInTrade =
                candlesInTrade,

            score =
                score,

            notes =
                notes
        )
    }

    private fun calculateScore(
        outcome: ReplayOutcome,
        plannedRR: Double,
        realizedR: Double?
    ): Int {

        var score =
            50

        /*
         * Reward good planned asymmetry.
         */
        when {

            plannedRR >= 3.0 ->
                score += 15

            plannedRR >= 2.0 ->
                score += 10

            plannedRR >= 1.5 ->
                score += 5

            plannedRR < 1.0 ->
                score -= 10
        }

        when (outcome) {

            ReplayOutcome.TARGET_HIT -> {

                score += 30
            }

            ReplayOutcome.STOP_HIT -> {

                score -= 20
            }

            ReplayOutcome
                .BOTH_HIT_SAME_CANDLE -> {

                score -= 5
            }

            ReplayOutcome
                .ENTRY_NOT_TRIGGERED -> {

                score -= 10
            }

            ReplayOutcome.OPEN_AT_END -> {

                if (realizedR != null) {

                    when {

                        realizedR >= 2.0 ->
                            score += 20

                        realizedR >= 1.0 ->
                            score += 10

                        realizedR > 0.0 ->
                            score += 5

                        realizedR < -0.5 ->
                            score -= 10
                    }
                }
            }

            ReplayOutcome.NO_TRADE_PLAN -> {

                score = 0
            }
        }

        return score.coerceIn(
            0,
            100
        )
    }

    private fun buildNotes(
        outcome: ReplayOutcome,
        plannedRR: Double,
        mfePercent: Double?,
        maePercent: Double?
    ): List<String> {

        val notes =
            mutableListOf<String>()

        when (outcome) {

            ReplayOutcome.TARGET_HIT -> {

                notes.add(
                    "The planned target was reached before the stop."
                )
            }

            ReplayOutcome.STOP_HIT -> {

                notes.add(
                    "The planned stop was reached before the target."
                )
            }

            ReplayOutcome
                .BOTH_HIT_SAME_CANDLE -> {

                notes.add(
                    "Stop and target were both inside the same candle. Intrabar order cannot be determined from this timeframe."
                )
            }

            ReplayOutcome
                .ENTRY_NOT_TRIGGERED -> {

                notes.add(
                    "The planned entry was not triggered."
                )
            }

            ReplayOutcome.OPEN_AT_END -> {

                notes.add(
                    "Neither the stop nor target was reached before the replay data ended."
                )
            }

            ReplayOutcome.NO_TRADE_PLAN -> {

                notes.add(
                    "No complete trade plan was available."
                )
            }
        }

        if (plannedRR >= 2.0) {

            notes.add(
                "The planned risk/reward was at least 2:1."
            )
        }

        if (plannedRR < 1.0) {

            notes.add(
                "The planned reward was smaller than the amount risked."
            )
        }

        if (mfePercent != null) {

            notes.add(
                "Maximum favorable excursion: ${formatPercent(mfePercent)}."
            )
        }

        if (maePercent != null) {

            notes.add(
                "Maximum adverse excursion: ${formatPercent(maePercent)}."
            )
        }

        return notes
    }

    private fun noPlanGrade(
        decision: ReplayDecision,
        note: String
    ): ReplayGrade {

        return ReplayGrade(
            outcome =
                ReplayOutcome.NO_TRADE_PLAN,

            entryTriggered =
                false,

            entryPrice =
                decision.plannedEntry,

            entryCandleIndex =
                null,

            stopPrice =
                decision.plannedStop,

            targetPrice =
                decision.plannedTarget,

            exitPrice =
                null,

            exitCandleIndex =
                null,

            riskPerShare =
                null,

            rewardPerShare =
                null,

            plannedRiskReward =
                null,

            realizedRMultiple =
                null,

            maxFavorableExcursionPercent =
                null,

            maxAdverseExcursionPercent =
                null,

            candlesUntilEntry =
                null,

            candlesInTrade =
                null,

            score =
                0,

            notes =
                listOf(note)
        )
    }

    private fun emptyGrade(
        entry: Double,
        stop: Double,
        target: Double,
        note: String
    ): ReplayGrade {

        return ReplayGrade(
            outcome =
                ReplayOutcome.NO_TRADE_PLAN,

            entryTriggered =
                false,

            entryPrice =
                entry,

            entryCandleIndex =
                null,

            stopPrice =
                stop,

            targetPrice =
                target,

            exitPrice =
                null,

            exitCandleIndex =
                null,

            riskPerShare =
                null,

            rewardPerShare =
                null,

            plannedRiskReward =
                null,

            realizedRMultiple =
                null,

            maxFavorableExcursionPercent =
                null,

            maxAdverseExcursionPercent =
                null,

            candlesUntilEntry =
                null,

            candlesInTrade =
                null,

            score =
                0,

            notes =
                listOf(note)
        )
    }

    private fun formatPercent(
        value: Double
    ): String {

        val rounded =
            round(
                value * 100.0
            ) /
                    100.0

        return if (rounded > 0.0) {
            "+$rounded%"
        } else {
            "$rounded%"
        }
    }
}