package com.tradna.APP.lab

data class AgentDecisionPerformance(
    val decision: String,
    val totalPredictions: Int,
    val linkedOutcomes: Int,
    val scoredDirectionalCalls: Int,
    val correctDirectionalCalls: Int,
    val directionalAccuracyPercent: Double?,
    val averageActualReturnPercent: Double?,
    val totalActualRealizedPnl: Double?
)

data class AgentPerformanceReport(
    val totalPredictions: Int,
    val linkedOutcomes: Int,
    val unresolvedPredictions: Int,

    val scoredDirectionalCalls: Int,
    val correctDirectionalCalls: Int,
    val incorrectDirectionalCalls: Int,
    val directionalAccuracyPercent: Double?,

    val averageActualReturnPercent: Double?,
    val medianActualReturnPercent: Double?,
    val totalActualRealizedPnl: Double?,

    val favorablePredictionCount: Int,
    val favorableLinkedCount: Int,
    val favorableProfitableRatePercent: Double?,
    val favorableAverageReturnPercent: Double?,

    val highConvictionPredictionCount: Int,
    val highConvictionLinkedCount: Int,
    val highConvictionProfitableRatePercent: Double?,
    val highConvictionAverageReturnPercent: Double?,

    val avoidPredictionCount: Int,
    val avoidLinkedCount: Int,
    val avoidLossAvoidanceRatePercent: Double?,
    val avoidAverageReturnPercent: Double?,

    val averagePredictionScore: Double?,
    val averagePredictionConfidence: Double?,

    val profitableLinkedOutcomeRatePercent: Double?,

    val decisionPerformance: List<AgentDecisionPerformance>,

    val calibrationSummary: String,
    val evidenceWarning: String?
)

object AgentPerformanceEngine {

    fun analyze(
        predictions: List<AgentPredictionRecord>
    ): AgentPerformanceReport {

        val linked =
            predictions.filter {
                it.outcomeLinked
            }

        val unresolved =
            predictions.count {
                !it.outcomeLinked
            }

        val directional =
            linked.filter {
                it.predictionDirectionCorrect !=
                        null
            }

        val correct =
            directional.count {
                it.predictionDirectionCorrect ==
                        true
            }

        val incorrect =
            directional.count {
                it.predictionDirectionCorrect ==
                        false
            }

        val linkedReturns =
            linked.mapNotNull {
                it.actualReturnPercent
            }

        val linkedPnl =
            linked.mapNotNull {
                it.actualRealizedPnl
            }

        val favorable =
            predictions.filter {
                it.decision ==
                        AgentTradeDecision.FAVORABLE.name
            }

        val favorableLinked =
            favorable.filter {
                it.outcomeLinked
            }

        val highConviction =
            predictions.filter {
                it.decision ==
                        AgentTradeDecision.HIGH_CONVICTION.name
            }

        val highConvictionLinked =
            highConviction.filter {
                it.outcomeLinked
            }

        val avoid =
            predictions.filter {
                it.decision ==
                        AgentTradeDecision.AVOID.name
            }

        val avoidLinked =
            avoid.filter {
                it.outcomeLinked
            }

        val decisionPerformance =
            AgentTradeDecision
                .entries
                .map {
                        decision ->

                    buildDecisionPerformance(
                        decision =
                            decision,
                        predictions =
                            predictions
                    )
                }
                .filter {
                    it.totalPredictions >
                            0
                }

        val calibrationSummary =
            buildCalibrationSummary(
                linkedCount =
                    linked.size,
                highConvictionLinked =
                    highConvictionLinked,
                favorableLinked =
                    favorableLinked,
                avoidLinked =
                    avoidLinked,
                directionalAccuracy =
                    percent(
                        correct,
                        directional.size
                    )
            )

        return AgentPerformanceReport(
            totalPredictions =
                predictions.size,

            linkedOutcomes =
                linked.size,

            unresolvedPredictions =
                unresolved,

            scoredDirectionalCalls =
                directional.size,

            correctDirectionalCalls =
                correct,

            incorrectDirectionalCalls =
                incorrect,

            directionalAccuracyPercent =
                percent(
                    correct,
                    directional.size
                ),

            averageActualReturnPercent =
                averageOrNull(
                    linkedReturns
                ),

            medianActualReturnPercent =
                medianOrNull(
                    linkedReturns
                ),

            totalActualRealizedPnl =
                linkedPnl
                    .takeIf {
                        it.isNotEmpty()
                    }
                    ?.sum(),

            favorablePredictionCount =
                favorable.size,

            favorableLinkedCount =
                favorableLinked.size,

            favorableProfitableRatePercent =
                profitableRate(
                    favorableLinked
                ),

            favorableAverageReturnPercent =
                averageOrNull(
                    favorableLinked.mapNotNull {
                        it.actualReturnPercent
                    }
                ),

            highConvictionPredictionCount =
                highConviction.size,

            highConvictionLinkedCount =
                highConvictionLinked.size,

            highConvictionProfitableRatePercent =
                profitableRate(
                    highConvictionLinked
                ),

            highConvictionAverageReturnPercent =
                averageOrNull(
                    highConvictionLinked.mapNotNull {
                        it.actualReturnPercent
                    }
                ),

            avoidPredictionCount =
                avoid.size,

            avoidLinkedCount =
                avoidLinked.size,

            avoidLossAvoidanceRatePercent =
                percent(
                    avoidLinked.count {
                        (
                                it.actualReturnPercent
                                    ?: 0.0
                                ) <= 0.0
                    },
                    avoidLinked.size
                ),

            avoidAverageReturnPercent =
                averageOrNull(
                    avoidLinked.mapNotNull {
                        it.actualReturnPercent
                    }
                ),

            averagePredictionScore =
                averageOrNull(
                    predictions.map {
                        it.overallScore.toDouble()
                    }
                ),

            averagePredictionConfidence =
                averageOrNull(
                    predictions.map {
                        it.confidencePercent.toDouble()
                    }
                ),

            profitableLinkedOutcomeRatePercent =
                profitableRate(
                    linked
                ),

            decisionPerformance =
                decisionPerformance,

            calibrationSummary =
                calibrationSummary,

            evidenceWarning =
                evidenceWarning(
                    linked.size
                )
        )
    }

    private fun buildDecisionPerformance(
        decision: AgentTradeDecision,
        predictions: List<AgentPredictionRecord>
    ): AgentDecisionPerformance {

        val matching =
            predictions.filter {
                it.decision ==
                        decision.name
            }

        val linked =
            matching.filter {
                it.outcomeLinked
            }

        val directional =
            linked.filter {
                it.predictionDirectionCorrect !=
                        null
            }

        val correct =
            directional.count {
                it.predictionDirectionCorrect ==
                        true
            }

        val returns =
            linked.mapNotNull {
                it.actualReturnPercent
            }

        val pnl =
            linked.mapNotNull {
                it.actualRealizedPnl
            }

        return AgentDecisionPerformance(
            decision =
                decision.name,

            totalPredictions =
                matching.size,

            linkedOutcomes =
                linked.size,

            scoredDirectionalCalls =
                directional.size,

            correctDirectionalCalls =
                correct,

            directionalAccuracyPercent =
                percent(
                    correct,
                    directional.size
                ),

            averageActualReturnPercent =
                averageOrNull(
                    returns
                ),

            totalActualRealizedPnl =
                pnl
                    .takeIf {
                        it.isNotEmpty()
                    }
                    ?.sum()
        )
    }

    private fun profitableRate(
        predictions: List<AgentPredictionRecord>
    ): Double? {

        val returns =
            predictions.mapNotNull {
                it.actualReturnPercent
            }

        if (
            returns.isEmpty()
        ) {
            return null
        }

        return percent(
            returns.count {
                it > 0.0
            },
            returns.size
        )
    }

    private fun buildCalibrationSummary(
        linkedCount: Int,
        highConvictionLinked: List<AgentPredictionRecord>,
        favorableLinked: List<AgentPredictionRecord>,
        avoidLinked: List<AgentPredictionRecord>,
        directionalAccuracy: Double?
    ): String {

        if (
            linkedCount <
            5
        ) {

            return "TraDNA is still collecting prospective evidence. Save recommendations before trades and link later outcomes before changing Agent thresholds."
        }

        val highConvictionWinRate =
            profitableRate(
                highConvictionLinked
            )

        val favorableWinRate =
            profitableRate(
                favorableLinked
            )

        val avoidLossRate =
            percent(
                avoidLinked.count {
                    (
                            it.actualReturnPercent
                                ?: 0.0
                            ) <= 0.0
                },
                avoidLinked.size
            )

        return buildString {

            append(
                "Prospective validation now includes $linkedCount linked outcomes."
            )

            directionalAccuracy
                ?.let {

                    append(
                        " Directional accuracy is ${formatPercent(it)}."
                    )
                }

            highConvictionWinRate
                ?.let {

                    append(
                        " HIGH CONVICTION outcomes are profitable ${formatPercent(it)} of the time."
                    )
                }

            favorableWinRate
                ?.let {

                    append(
                        " FAVORABLE outcomes are profitable ${formatPercent(it)} of the time."
                    )
                }

            avoidLossRate
                ?.let {

                    append(
                        " AVOID calls correctly identified non-profitable outcomes ${formatPercent(it)} of the time."
                    )
                }
        }
    }

    private fun evidenceWarning(
        linkedCount: Int
    ): String? {

        return when {

            linkedCount == 0 ->
                "No saved Agent prediction has a linked real-world outcome yet."

            linkedCount < 5 ->
                "Very small validation sample. Treat all Agent performance metrics as preliminary."

            linkedCount < 15 ->
                "Early validation sample. Useful for debugging, but not enough evidence to tune the Agent aggressively."

            linkedCount < 30 ->
                "Moderate validation sample. Patterns are becoming useful, but continue collecting prospective outcomes."

            else ->
                null
        }
    }

    private fun percent(
        numerator: Int,
        denominator: Int
    ): Double? {

        if (
            denominator <=
            0
        ) {
            return null
        }

        return numerator
            .toDouble() /
                denominator
                    .toDouble() *
                100.0
    }

    private fun averageOrNull(
        values: List<Double>
    ): Double? {

        if (
            values.isEmpty()
        ) {
            return null
        }

        return values.average()
    }

    private fun medianOrNull(
        values: List<Double>
    ): Double? {

        if (
            values.isEmpty()
        ) {
            return null
        }

        val sorted =
            values.sorted()

        val middle =
            sorted.size /
                    2

        return if (
            sorted.size %
            2 ==
            0
        ) {

            (
                    sorted[middle - 1] +
                            sorted[middle]
                    ) /
                    2.0

        } else {

            sorted[middle]
        }
    }

    private fun formatPercent(
        value: Double
    ): String {

        return String.format(
            java.util.Locale.US,
            "%.1f%%",
            value
        )
    }
}
