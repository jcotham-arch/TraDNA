package com.tradna.APP.lab

data class ScoreBucketCalibration(
    val label: String,
    val minimumScore: Int,
    val maximumScore: Int,
    val predictions: Int,
    val linkedOutcomes: Int,
    val profitableRatePercent: Double?,
    val averageReturnPercent: Double?,
    val directionalAccuracyPercent: Double?
)

data class CalibrationRecommendation(
    val title: String,
    val category: String,
    val currentValue: String,
    val recommendedValue: String,
    val reason: String,
    val confidencePercent: Int
)

data class AgentCalibrationReport(
    val totalPredictions: Int,
    val linkedOutcomes: Int,
    val calibrationReady: Boolean,
    val calibrationConfidencePercent: Int,

    val scoreBuckets: List<ScoreBucketCalibration>,

    val recommendations: List<CalibrationRecommendation>,

    val strongestBucket: ScoreBucketCalibration?,
    val weakestBucket: ScoreBucketCalibration?,

    val summary: String,
    val caution: String?
)

object AgentCalibrationEngine {

    /*
     * V1 intentionally does NOT modify the Agent.
     *
     * It evaluates whether the current decision thresholds appear
     * consistent with prospective outcomes and recommends changes
     * only when enough evidence exists.
     */
    fun analyze(
        predictions: List<AgentPredictionRecord>
    ): AgentCalibrationReport {

        val linked =
            predictions.filter {
                it.outcomeLinked
            }

        val buckets =
            listOf(
                buildBucket(
                    label = "0–44",
                    minimum = 0,
                    maximum = 44,
                    predictions = predictions
                ),
                buildBucket(
                    label = "45–57",
                    minimum = 45,
                    maximum = 57,
                    predictions = predictions
                ),
                buildBucket(
                    label = "58–71",
                    minimum = 58,
                    maximum = 71,
                    predictions = predictions
                ),
                buildBucket(
                    label = "72–84",
                    minimum = 72,
                    maximum = 84,
                    predictions = predictions
                ),
                buildBucket(
                    label = "85–100",
                    minimum = 85,
                    maximum = 100,
                    predictions = predictions
                )
            )

        val usefulBuckets =
            buckets.filter {
                it.linkedOutcomes >= 3
            }

        val strongest =
            usefulBuckets.maxWithOrNull(
                compareBy<ScoreBucketCalibration> {
                    it.profitableRatePercent
                        ?: Double.NEGATIVE_INFINITY
                }
                    .thenBy {
                        it.averageReturnPercent
                            ?: Double.NEGATIVE_INFINITY
                    }
            )

        val weakest =
            usefulBuckets.minWithOrNull(
                compareBy<ScoreBucketCalibration> {
                    it.profitableRatePercent
                        ?: Double.POSITIVE_INFINITY
                }
                    .thenBy {
                        it.averageReturnPercent
                            ?: Double.POSITIVE_INFINITY
                    }
            )

        val confidence =
            calibrationConfidence(
                linked.size
            )

        val recommendations =
            buildRecommendations(
                predictions =
                    predictions,
                linked =
                    linked,
                buckets =
                    buckets,
                confidence =
                    confidence
            )

        return AgentCalibrationReport(
            totalPredictions =
                predictions.size,

            linkedOutcomes =
                linked.size,

            calibrationReady =
                linked.size >= 10,

            calibrationConfidencePercent =
                confidence,

            scoreBuckets =
                buckets,

            recommendations =
                recommendations,

            strongestBucket =
                strongest,

            weakestBucket =
                weakest,

            summary =
                buildSummary(
                    linkedCount =
                        linked.size,
                    confidence =
                        confidence,
                    strongest =
                        strongest,
                    weakest =
                        weakest
                ),

            caution =
                calibrationCaution(
                    linked.size
                )
        )
    }

    private fun buildBucket(
        label: String,
        minimum: Int,
        maximum: Int,
        predictions: List<AgentPredictionRecord>
    ): ScoreBucketCalibration {

        val matching =
            predictions.filter {
                it.overallScore in
                        minimum..maximum
            }

        val linked =
            matching.filter {
                it.outcomeLinked
            }

        val returns =
            linked.mapNotNull {
                it.actualReturnPercent
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

        return ScoreBucketCalibration(
            label =
                label,

            minimumScore =
                minimum,

            maximumScore =
                maximum,

            predictions =
                matching.size,

            linkedOutcomes =
                linked.size,

            profitableRatePercent =
                if (
                    returns.isNotEmpty()
                ) {

                    percent(
                        returns.count {
                            it > 0.0
                        },
                        returns.size
                    )

                } else {
                    null
                },

            averageReturnPercent =
                averageOrNull(
                    returns
                ),

            directionalAccuracyPercent =
                percent(
                    correct,
                    directional.size
                )
        )
    }

    private fun buildRecommendations(
        predictions: List<AgentPredictionRecord>,
        linked: List<AgentPredictionRecord>,
        buckets: List<ScoreBucketCalibration>,
        confidence: Int
    ): List<CalibrationRecommendation> {

        val results =
            mutableListOf<CalibrationRecommendation>()

        if (
            linked.size <
            5
        ) {

            results.add(
                CalibrationRecommendation(
                    title =
                        "Keep current thresholds",
                    category =
                        "DATA",
                    currentValue =
                        "Current Agent thresholds",
                    recommendedValue =
                        "No change",
                    reason =
                        "Too few linked prospective outcomes exist to justify changing the scoring system.",
                    confidencePercent =
                        confidence
                )
            )

            return results
        }

        val favorable =
            linked.filter {
                it.decision ==
                        AgentTradeDecision.FAVORABLE.name
            }

        val highConviction =
            linked.filter {
                it.decision ==
                        AgentTradeDecision.HIGH_CONVICTION.name
            }

        val avoid =
            linked.filter {
                it.decision ==
                        AgentTradeDecision.AVOID.name
            }

        val favorableWinRate =
            profitableRate(
                favorable
            )

        val favorableAverage =
            averageOrNull(
                favorable.mapNotNull {
                    it.actualReturnPercent
                }
            )

        val highConvictionWinRate =
            profitableRate(
                highConviction
            )

        val avoidLossRate =
            if (
                avoid.isNotEmpty()
            ) {

                percent(
                    avoid.count {
                        (
                                it.actualReturnPercent
                                    ?: 0.0
                                ) <= 0.0
                    },
                    avoid.size
                )

            } else {
                null
            }

        /*
         * FAVORABLE currently begins at 72.
         */
        if (
            favorable.size >= 5
        ) {

            when {

                (
                        favorableWinRate
                            ?: 0.0
                        ) <
                        50.0 ||
                        (
                                favorableAverage
                                    ?: 0.0
                                ) <=
                        0.0 -> {

                    results.add(
                        CalibrationRecommendation(
                            title =
                                "Tighten FAVORABLE threshold",
                            category =
                                "THRESHOLD",
                            currentValue =
                                "72",
                            recommendedValue =
                                "76–78",
                            reason =
                                "Prospective FAVORABLE outcomes are not separating strongly enough from weaker setups.",
                            confidencePercent =
                                confidence
                        )
                    )
                }

                (
                        favorableWinRate
                            ?: 0.0
                        ) >=
                        70.0 &&
                        (
                                favorableAverage
                                    ?: 0.0
                                ) >
                        1.0 -> {

                    results.add(
                        CalibrationRecommendation(
                            title =
                                "FAVORABLE threshold is working",
                            category =
                                "THRESHOLD",
                            currentValue =
                                "72",
                            recommendedValue =
                                "Keep 72",
                            reason =
                                "FAVORABLE calls show both strong profitable rate and positive average return prospectively.",
                            confidencePercent =
                                confidence
                        )
                    )
                }
            }
        }

        /*
         * HIGH CONVICTION currently begins at 85.
         */
        if (
            highConviction.size >= 4
        ) {

            if (
                (
                        highConvictionWinRate
                            ?: 0.0
                        ) <
                65.0
            ) {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "Tighten HIGH CONVICTION threshold",
                        category =
                            "THRESHOLD",
                        currentValue =
                            "85",
                        recommendedValue =
                            "88–90",
                        reason =
                            "HIGH CONVICTION calls are not yet producing the level of separation expected from the strongest Agent category.",
                        confidencePercent =
                            confidence
                    )
                )

            } else {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "HIGH CONVICTION threshold is holding",
                        category =
                            "THRESHOLD",
                        currentValue =
                            "85",
                        recommendedValue =
                            "Keep 85",
                        reason =
                            "HIGH CONVICTION calls are prospectively outperforming at an acceptable rate.",
                        confidencePercent =
                            confidence
                    )
                )
            }
        }

        /*
         * AVOID calibration.
         */
        if (
            avoid.size >= 4
        ) {

            if (
                (
                        avoidLossRate
                            ?: 0.0
                        ) <
                55.0
            ) {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "Revisit AVOID sensitivity",
                        category =
                            "THRESHOLD",
                        currentValue =
                            "Current AVOID rule",
                        recommendedValue =
                            "Require stronger negative evidence",
                        reason =
                            "Too many AVOID calls are followed by profitable outcomes.",
                        confidencePercent =
                            confidence
                    )
                )

            } else if (
                (
                        avoidLossRate
                            ?: 0.0
                        ) >=
                70.0
            ) {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "AVOID filter is useful",
                        category =
                            "THRESHOLD",
                        currentValue =
                            "Current AVOID rule",
                        recommendedValue =
                            "Keep current logic",
                        reason =
                            "Most linked AVOID calls correspond to non-profitable outcomes.",
                        confidencePercent =
                            confidence
                    )
                )
            }
        }

        /*
         * Score monotonicity test:
         * higher score buckets should generally perform better.
         */
        val bucket58to71 =
            buckets.first {
                it.minimumScore ==
                        58
            }

        val bucket72to84 =
            buckets.first {
                it.minimumScore ==
                        72
            }

        val bucket85to100 =
            buckets.first {
                it.minimumScore ==
                        85
            }

        if (
            bucket58to71.linkedOutcomes >= 3 &&
            bucket72to84.linkedOutcomes >= 3
        ) {

            val lowerReturn =
                bucket58to71.averageReturnPercent

            val higherReturn =
                bucket72to84.averageReturnPercent

            if (
                lowerReturn != null &&
                higherReturn != null &&
                higherReturn <
                lowerReturn
            ) {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "Score ordering needs review",
                        category =
                            "WEIGHTING",
                        currentValue =
                            "58–71 < 72–84",
                        recommendedValue =
                            "Audit component weights",
                        reason =
                            "The higher score bucket currently has a lower average realized return than the lower score bucket.",
                        confidencePercent =
                            confidence
                    )
                )
            }
        }

        if (
            bucket72to84.linkedOutcomes >= 3 &&
            bucket85to100.linkedOutcomes >= 3
        ) {

            val midReturn =
                bucket72to84.averageReturnPercent

            val highReturn =
                bucket85to100.averageReturnPercent

            if (
                midReturn != null &&
                highReturn != null &&
                highReturn <
                midReturn
            ) {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "Top score bucket is not separating",
                        category =
                            "WEIGHTING",
                        currentValue =
                            "85–100",
                        recommendedValue =
                            "Audit HIGH CONVICTION weighting",
                        reason =
                            "The highest score bucket is not yet outperforming the 72–84 bucket on average return.",
                        confidencePercent =
                            confidence
                    )
                )
            }
        }

        /*
         * Compare score components on correct vs incorrect directional calls.
         */
        val scoredDirectional =
            linked.filter {
                it.predictionDirectionCorrect !=
                        null
            }

        val correct =
            scoredDirectional.filter {
                it.predictionDirectionCorrect ==
                        true
            }

        val incorrect =
            scoredDirectional.filter {
                it.predictionDirectionCorrect ==
                        false
            }

        if (
            correct.size >= 4 &&
            incorrect.size >= 4
        ) {

            val historyGap =
                averageOrNull(
                    correct.map {
                        it.historyMatchScore.toDouble()
                    }
                )
                    .minusNullable(
                        averageOrNull(
                            incorrect.map {
                                it.historyMatchScore.toDouble()
                            }
                        )
                    )

            val technicalGap =
                averageOrNull(
                    correct.map {
                        it.technicalScore.toDouble()
                    }
                )
                    .minusNullable(
                        averageOrNull(
                            incorrect.map {
                                it.technicalScore.toDouble()
                            }
                        )
                    )

            if (
                historyGap != null &&
                technicalGap != null &&
                historyGap >
                technicalGap +
                8.0
            ) {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "Historical similarity is highly predictive",
                        category =
                            "WEIGHTING",
                        currentValue =
                            "History weight 28%",
                        recommendedValue =
                            "Consider 30–34% later",
                        reason =
                            "Correct directional calls show a much larger separation in history-match score than technical score.",
                        confidencePercent =
                            confidence
                    )
                )
            }

            if (
                technicalGap != null &&
                historyGap != null &&
                technicalGap >
                historyGap +
                8.0
            ) {

                results.add(
                    CalibrationRecommendation(
                        title =
                            "Technical score is carrying more signal",
                        category =
                            "WEIGHTING",
                        currentValue =
                            "Technical weight 30%",
                        recommendedValue =
                            "Consider 32–35% later",
                        reason =
                            "Correct directional calls show a larger separation in technical score than history-match score.",
                        confidencePercent =
                            confidence
                    )
                )
            }
        }

        if (
            results.isEmpty()
        ) {

            results.add(
                CalibrationRecommendation(
                    title =
                        "No calibration change recommended",
                    category =
                        "DATA",
                    currentValue =
                        "Current scoring model",
                    recommendedValue =
                        "Keep current settings",
                    reason =
                        "The current prospective sample does not show a strong enough reason to alter thresholds or weights.",
                    confidencePercent =
                        confidence
                )
            )
        }

        return results
            .take(
                8
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

    private fun calibrationConfidence(
        linkedOutcomes: Int
    ): Int {

        return when {

            linkedOutcomes >= 75 ->
                95

            linkedOutcomes >= 50 ->
                90

            linkedOutcomes >= 30 ->
                80

            linkedOutcomes >= 20 ->
                70

            linkedOutcomes >= 15 ->
                60

            linkedOutcomes >= 10 ->
                50

            linkedOutcomes >= 5 ->
                30

            linkedOutcomes > 0 ->
                15

            else ->
                0
        }
    }

    private fun buildSummary(
        linkedCount: Int,
        confidence: Int,
        strongest: ScoreBucketCalibration?,
        weakest: ScoreBucketCalibration?
    ): String {

        if (
            linkedCount ==
            0
        ) {

            return "No prospective outcomes are linked yet. TraDNA will evaluate score calibration after saved predictions begin receiving real trade outcomes."
        }

        return buildString {

            append(
                "Calibration currently uses $linkedCount linked prospective outcomes with $confidence% confidence."
            )

            strongest
                ?.let {

                    append(
                        " Strongest observed score bucket: ${it.label}."
                    )
                }

            weakest
                ?.let {

                    append(
                        " Weakest observed score bucket: ${it.label}."
                    )
                }
        }
    }

    private fun calibrationCaution(
        linkedCount: Int
    ): String? {

        return when {

            linkedCount < 5 ->
                "Do not change the Agent from this sample."

            linkedCount < 10 ->
                "Calibration observations are exploratory only."

            linkedCount < 20 ->
                "Enough data exists for cautious recommendations, but not automatic parameter changes."

            linkedCount < 30 ->
                "Calibration evidence is becoming useful. Continue prospective validation before enabling adaptive weights."

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

    private fun Double?.minusNullable(
        other: Double?
    ): Double? {

        if (
            this == null ||
            other == null
        ) {
            return null
        }

        return this -
                other
    }
}
