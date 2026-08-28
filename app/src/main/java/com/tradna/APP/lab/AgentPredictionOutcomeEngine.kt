package com.tradna.APP.lab

import android.content.Context
import com.tradna.APP.data.TradeEpisode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PredictionLinkResult(
    val examinedPredictions: Int,
    val newlyLinkedPredictions: Int,
    val alreadyLinkedPredictions: Int,
    val unresolvedPredictions: Int
)

object AgentPredictionOutcomeEngine {

    private val tradeDateFormatter =
        DateTimeFormatter.ofPattern(
            "M/d/yyyy",
            Locale.US
        )

    /*
     * Match saved Agent predictions to later reconstructed trades.
     *
     * Rules v1:
     * - same symbol
     * - trade opens on or after the prediction date
     * - trade opens within 7 calendar days
     * - trade must have a closed outcome
     * - one trade can satisfy only one prediction during a pass
     *
     * We intentionally keep these rules conservative so TraDNA does
     * not claim an Agent prediction caused or matched an unrelated trade.
     */
    fun linkOutcomes(
        context: Context,
        trades: List<TradeEpisode>
    ): PredictionLinkResult {

        val predictions =
            AgentPredictionJournal.loadPredictions(
                context
            )

        if (
            predictions.isEmpty()
        ) {

            return PredictionLinkResult(
                examinedPredictions = 0,
                newlyLinkedPredictions = 0,
                alreadyLinkedPredictions = 0,
                unresolvedPredictions = 0
            )
        }

        val alreadyUsedTradeIds =
            predictions
                .mapNotNull {
                    it.linkedTradeId
                }
                .toMutableSet()

        var linked =
            0

        var alreadyLinked =
            0

        var unresolved =
            0

        predictions.forEach {
                prediction ->

            if (
                prediction.outcomeLinked
            ) {

                alreadyLinked++
                return@forEach
            }

            val predictionDate =
                predictionDate(
                    prediction.createdAtEpochMillis
                )

            val candidate =
                trades
                    .asSequence()
                    .filter {
                        it.id !in
                                alreadyUsedTradeIds
                    }
                    .filter {
                        it.symbol.equals(
                            prediction.symbol,
                            ignoreCase = true
                        )
                    }
                    .filter {
                        !it.closeDate.isNullOrBlank() &&
                                it.averageExitPrice != null
                    }
                    .mapNotNull {
                            trade ->

                        val openDate =
                            parseTradeDate(
                                trade.openDate
                            )
                                ?: return@mapNotNull null

                        val daysAfter =
                            java.time.temporal.ChronoUnit
                                .DAYS
                                .between(
                                    predictionDate,
                                    openDate
                                )

                        if (
                            daysAfter < 0L ||
                            daysAfter > 7L
                        ) {

                            return@mapNotNull null
                        }

                        trade to daysAfter
                    }
                    .sortedWith(
                        compareBy<Pair<TradeEpisode, Long>> {
                            it.second
                        }
                            .thenBy {
                                parseTradeDate(
                                    it.first.openDate
                                )
                                    ?: LocalDate.MAX
                            }
                    )
                    .firstOrNull()
                    ?.first

            if (
                candidate == null
            ) {

                unresolved++
                return@forEach
            }

            val actualReturn =
                calculateReturnPercent(
                    entryPrice =
                        candidate.averageEntryPrice,
                    exitPrice =
                        candidate.averageExitPrice
                )

            val directionCorrect =
                evaluateDirectionCorrect(
                    decision =
                        prediction.decision,
                    actualReturnPercent =
                        actualReturn
                )

            val updated =
                prediction.copy(
                    outcomeLinked =
                        true,

                    linkedTradeId =
                        candidate.id,

                    actualEntryPrice =
                        candidate.averageEntryPrice,

                    actualExitPrice =
                        candidate.averageExitPrice,

                    actualReturnPercent =
                        actualReturn,

                    actualRealizedPnl =
                        candidate.realizedPnl,

                    predictionDirectionCorrect =
                        directionCorrect
                )

            AgentPredictionJournal.updatePrediction(
                context =
                    context,
                updated =
                    updated
            )

            alreadyUsedTradeIds.add(
                candidate.id
            )

            linked++
        }

        return PredictionLinkResult(
            examinedPredictions =
                predictions.size,

            newlyLinkedPredictions =
                linked,

            alreadyLinkedPredictions =
                alreadyLinked,

            unresolvedPredictions =
                unresolved
        )
    }

    private fun predictionDate(
        epochMillis: Long
    ): LocalDate {

        return Instant
            .ofEpochMilli(
                epochMillis
            )
            .atZone(
                ZoneId.systemDefault()
            )
            .toLocalDate()
    }

    private fun parseTradeDate(
        value: String
    ): LocalDate? {

        return try {

            LocalDate.parse(
                value,
                tradeDateFormatter
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }

    private fun calculateReturnPercent(
        entryPrice: Double,
        exitPrice: Double?
    ): Double? {

        if (
            entryPrice <= 0.0 ||
            exitPrice == null
        ) {

            return null
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

    private fun evaluateDirectionCorrect(
        decision: String,
        actualReturnPercent: Double?
    ): Boolean? {

        val actual =
            actualReturnPercent
                ?: return null

        return when (
            decision.uppercase()
        ) {

            AgentTradeDecision.HIGH_CONVICTION.name,
            AgentTradeDecision.FAVORABLE.name ->
                actual > 0.0

            AgentTradeDecision.AVOID.name ->
                actual <= 0.0

            /*
             * WATCH and WAIT are process recommendations rather than
             * directional predictions, so we do not score them as
             * right/wrong yet.
             */
            AgentTradeDecision.WATCH.name,
            AgentTradeDecision.WAIT.name ->
                null

            else ->
                null
        }
    }
}
