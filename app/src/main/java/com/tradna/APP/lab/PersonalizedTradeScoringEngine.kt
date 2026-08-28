package com.tradna.APP.lab

import com.tradna.APP.market.TechnicalSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class AgentTradeDecision {
    AVOID,
    WAIT,
    WATCH,
    FAVORABLE,
    HIGH_CONVICTION
}

data class HistoricalTradeMatch(
    val tradeId: String,
    val symbol: String,
    val openDate: String,

    val similarityPercent: Int,

    val actualReturnPercent: Double?,
    val realizedPnl: Double,

    val entryTechnicalScore: Int?,
    val entryEfficiencyScore: Int?,
    val exitEfficiencyScore: Int?,

    val bestAlternativeTitle: String?,
    val bestAlternativeReturnPercent: Double?,

    val profitable: Boolean
)

data class AgentScoreBreakdown(
    val technicalScore: Int,
    val historyMatchScore: Int,
    val entryQualityScore: Int,
    val riskQualityScore: Int,
    val evidenceConfidenceScore: Int
)

data class AgentTradeDecisionResult(
    val symbol: String,

    val decision: AgentTradeDecision,
    val overallScore: Int,
    val confidencePercent: Int,

    val scoreBreakdown: AgentScoreBreakdown,

    val matchedHistoricalTrades: List<HistoricalTradeMatch>,
    val matchedTradeCount: Int,

    val historicalProfitableRatePercent: Double?,
    val historicalAverageReturnPercent: Double?,
    val historicalAverageMfePercent: Double?,
    val historicalAverageMaePercent: Double?,

    val preferredEntryMethod: String,
    val preferredExitMethod: String,

    val strengths: List<String>,
    val warnings: List<String>,
    val reasoning: List<String>,

    val evidenceSummary: String
)

object PersonalizedTradeScoringEngine {

    private const val MAX_MATCHES =
        12

    private const val MIN_SIMILARITY_TO_COUNT =
        48

    /*
     * This is the first advisory decision engine.
     *
     * It combines:
     *
     * 1. Current technical conditions
     * 2. Similar historical trades from AgentTrainingRecord
     * 3. PatternLearningEngine output
     * 4. Entry-location quality
     * 5. Dataset confidence
     *
     * It does NOT place trades.
     */
    fun score(
        symbol: String,
        snapshot: TechnicalSnapshot,
        records: List<AgentTrainingRecord>,
        patternProfile: AgentPatternProfile,
        proposedEntryPrice: Double? = null,
        proposedStopPrice: Double? = null,
        proposedTargetPrice: Double? = null
    ): AgentTradeDecisionResult {

        val currentPrice =
            proposedEntryPrice
                ?: snapshot.price

        val matches =
            buildHistoricalMatches(
                symbol =
                    symbol,
                snapshot =
                    snapshot,
                currentPrice =
                    currentPrice,
                records =
                    records
            )

        val meaningfulMatches =
            matches.filter {
                it.similarityPercent >=
                        MIN_SIMILARITY_TO_COUNT
            }

        val technicalScore =
            snapshot.technicalScore
                .coerceIn(
                    0,
                    100
                )

        val historyScore =
            calculateHistoryMatchScore(
                meaningfulMatches
            )

        val entryQualityScore =
            calculateEntryQualityScore(
                snapshot =
                    snapshot,
                entryPrice =
                    currentPrice,
                records =
                    records
            )

        val riskQualityScore =
            calculateRiskQualityScore(
                entryPrice =
                    currentPrice,
                stopPrice =
                    proposedStopPrice,
                targetPrice =
                    proposedTargetPrice
            )

        val evidenceConfidence =
            calculateEvidenceConfidenceScore(
                patternProfile =
                    patternProfile,
                matchCount =
                    meaningfulMatches.size
            )

        /*
         * Weighted overall score.
         *
         * Technical state and personal historical evidence
         * are intentionally dominant.
         */
        val overallScore =
            (
                    technicalScore *
                            0.30 +
                            historyScore *
                            0.28 +
                            entryQualityScore *
                            0.18 +
                            riskQualityScore *
                            0.12 +
                            evidenceConfidence *
                            0.12
                    )
                .toInt()
                .coerceIn(
                    0,
                    100
                )

        val historicalWinRate =
            percent(
                meaningfulMatches.count {
                    it.profitable
                },
                meaningfulMatches.size
            )

        val historicalAverageReturn =
            averageOrNull(
                meaningfulMatches.mapNotNull {
                    it.actualReturnPercent
                }
            )

        val historicalAverageMfe =
            averageOrNull(
                meaningfulMatches.mapNotNull { match ->

                    records
                        .firstOrNull {
                            it.tradeId ==
                                    match.tradeId
                        }
                        ?.maximumFavorableExcursionPercent
                }
            )

        val historicalAverageMae =
            averageOrNull(
                meaningfulMatches.mapNotNull { match ->

                    records
                        .firstOrNull {
                            it.tradeId ==
                                    match.tradeId
                        }
                        ?.maximumAdverseExcursionPercent
                }
            )

        val preferredExitMethod =
            determinePreferredExitMethod(
                records =
                    records,
                meaningfulMatches =
                    meaningfulMatches,
                patternProfile =
                    patternProfile
            )

        val preferredEntryMethod =
            determinePreferredEntryMethod(
                snapshot =
                    snapshot,
                entryQualityScore =
                    entryQualityScore,
                patternProfile =
                    patternProfile
            )

        val strengths =
            buildStrengths(
                snapshot =
                    snapshot,
                meaningfulMatches =
                    meaningfulMatches,
                historicalWinRate =
                    historicalWinRate,
                historicalAverageReturn =
                    historicalAverageReturn,
                entryQualityScore =
                    entryQualityScore,
                riskQualityScore =
                    riskQualityScore
            )

        val warnings =
            buildWarnings(
                snapshot =
                    snapshot,
                meaningfulMatches =
                    meaningfulMatches,
                historicalWinRate =
                    historicalWinRate,
                historicalAverageReturn =
                    historicalAverageReturn,
                entryQualityScore =
                    entryQualityScore,
                riskQualityScore =
                    riskQualityScore,
                patternProfile =
                    patternProfile
            )

        val decision =
            determineDecision(
                overallScore =
                    overallScore,
                technicalScore =
                    technicalScore,
                historyScore =
                    historyScore,
                evidenceConfidence =
                    evidenceConfidence,
                warnings =
                    warnings
            )

        val confidence =
            calculateDecisionConfidence(
                overallScore =
                    overallScore,
                evidenceConfidence =
                    evidenceConfidence,
                meaningfulMatches =
                    meaningfulMatches,
                technicalScore =
                    technicalScore
            )

        val reasoning =
            buildReasoning(
                symbol =
                    symbol,
                snapshot =
                    snapshot,
                meaningfulMatches =
                    meaningfulMatches,
                historicalWinRate =
                    historicalWinRate,
                historicalAverageReturn =
                    historicalAverageReturn,
                technicalScore =
                    technicalScore,
                historyScore =
                    historyScore,
                entryQualityScore =
                    entryQualityScore,
                riskQualityScore =
                    riskQualityScore,
                evidenceConfidence =
                    evidenceConfidence,
                preferredEntryMethod =
                    preferredEntryMethod,
                preferredExitMethod =
                    preferredExitMethod
            )

        return AgentTradeDecisionResult(
            symbol =
                symbol,

            decision =
                decision,

            overallScore =
                overallScore,

            confidencePercent =
                confidence,

            scoreBreakdown =
                AgentScoreBreakdown(
                    technicalScore =
                        technicalScore,
                    historyMatchScore =
                        historyScore,
                    entryQualityScore =
                        entryQualityScore,
                    riskQualityScore =
                        riskQualityScore,
                    evidenceConfidenceScore =
                        evidenceConfidence
                ),

            matchedHistoricalTrades =
                meaningfulMatches,

            matchedTradeCount =
                meaningfulMatches.size,

            historicalProfitableRatePercent =
                historicalWinRate,

            historicalAverageReturnPercent =
                historicalAverageReturn,

            historicalAverageMfePercent =
                historicalAverageMfe,

            historicalAverageMaePercent =
                historicalAverageMae,

            preferredEntryMethod =
                preferredEntryMethod,

            preferredExitMethod =
                preferredExitMethod,

            strengths =
                strengths,

            warnings =
                warnings,

            reasoning =
                reasoning,

            evidenceSummary =
                buildEvidenceSummary(
                    meaningfulMatches =
                        meaningfulMatches,
                    winRate =
                        historicalWinRate,
                    averageReturn =
                        historicalAverageReturn,
                    patternProfile =
                        patternProfile
                )
        )
    }

    /*
     * =========================================================
     * HISTORICAL SIMILARITY
     * =========================================================
     */

    private fun buildHistoricalMatches(
        symbol: String,
        snapshot: TechnicalSnapshot,
        currentPrice: Double,
        records: List<AgentTrainingRecord>
    ): List<HistoricalTradeMatch> {

        return records
            .map {
                    record ->

                val similarity =
                    calculateSimilarity(
                        symbol =
                            symbol,
                        snapshot =
                            snapshot,
                        currentPrice =
                            currentPrice,
                        record =
                            record
                    )

                HistoricalTradeMatch(
                    tradeId =
                        record.tradeId,

                    symbol =
                        record.symbol,

                    openDate =
                        record.openDate,

                    similarityPercent =
                        similarity,

                    actualReturnPercent =
                        record.actualReturnPercent,

                    realizedPnl =
                        record.actualRealizedPnl,

                    entryTechnicalScore =
                        record.entryTechnicalScore,

                    entryEfficiencyScore =
                        record.entryEfficiencyScore,

                    exitEfficiencyScore =
                        record.exitEfficiencyScore,

                    bestAlternativeTitle =
                        record.bestAlternativeTitle,

                    bestAlternativeReturnPercent =
                        record.bestAlternativeReturnPercent,

                    profitable =
                        record.profitableTrade
                )
            }
            .sortedWith(
                compareByDescending<HistoricalTradeMatch> {
                    it.similarityPercent
                }
                    .thenByDescending {
                        it.actualReturnPercent
                            ?: Double.NEGATIVE_INFINITY
                    }
            )
            .take(
                MAX_MATCHES
            )
    }

    private fun calculateSimilarity(
        symbol: String,
        snapshot: TechnicalSnapshot,
        currentPrice: Double,
        record: AgentTrainingRecord
    ): Int {

        var weightedScore =
            0.0

        var totalWeight =
            0.0

        fun add(
            similarity: Double?,
            weight: Double
        ) {

            if (
                similarity ==
                null
            ) {
                return
            }

            weightedScore +=
                similarity
                    .coerceIn(
                        0.0,
                        1.0
                    ) *
                        weight

            totalWeight +=
                weight
        }

        /*
         * Technical score similarity.
         */
        add(
            similarityByDistance(
                current =
                    snapshot.technicalScore
                        .toDouble(),
                historical =
                    record.entryTechnicalScore
                        ?.toDouble(),
                tolerance =
                    35.0
            ),
            weight =
                1.8
        )

        /*
         * VWAP distance similarity.
         */
        add(
            similarityByDistance(
                current =
                    snapshot.distanceFromVwapPercent,
                historical =
                    record.entryDistanceFromVwapPercent,
                tolerance =
                    6.0
            ),
            weight =
                1.4
        )

        /*
         * Relative volume similarity.
         */
        add(
            similarityByDistance(
                current =
                    snapshot.volumeRatio,
                historical =
                    record.entryRelativeVolume,
                tolerance =
                    2.5
            ),
            weight =
                1.2
        )

        /*
         * VWAP side.
         */
        val currentAboveVwap =
            snapshot.vwap
                ?.let {
                    currentPrice >
                            it
                }

        val historicalAboveVwap =
            record.entryVwap
                ?.let {
                    record.actualEntryPrice >
                            it
                }

        if (
            currentAboveVwap != null &&
            historicalAboveVwap != null
        ) {

            add(
                if (
                    currentAboveVwap ==
                    historicalAboveVwap
                ) {
                    1.0
                } else {
                    0.0
                },
                weight =
                    1.4
            )
        }

        /*
         * EMA trend alignment.
         */
        val currentBullishEma =
            bullishEmaAlignment(
                price =
                    currentPrice,
                ema9 =
                    snapshot.ema9,
                ema20 =
                    snapshot.ema20
            )

        val historicalBullishEma =
            bullishEmaAlignment(
                price =
                    record.actualEntryPrice,
                ema9 =
                    record.entryEma9,
                ema20 =
                    record.entryEma20
            )

        if (
            currentBullishEma != null &&
            historicalBullishEma != null
        ) {

            add(
                if (
                    currentBullishEma ==
                    historicalBullishEma
                ) {
                    1.0
                } else {
                    0.0
                },
                weight =
                    1.5
            )
        }

        /*
         * Technical signal overlap.
         */
        add(
            signalOverlap(
                currentSignals =
                    snapshot.signals,
                historicalSignals =
                    record.entrySignals
            ),
            weight =
                2.0
        )

        /*
         * Same-symbol history receives a modest bonus, but
         * it cannot dominate technical similarity.
         */
        if (
            symbol.equals(
                record.symbol,
                ignoreCase =
                    true
            )
        ) {

            add(
                1.0,
                weight =
                    0.6
            )
        }

        if (
            totalWeight <=
            0.0
        ) {
            return 0
        }

        return (
                weightedScore /
                        totalWeight *
                        100.0
                )
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun similarityByDistance(
        current: Double?,
        historical: Double?,
        tolerance: Double
    ): Double? {

        if (
            current == null ||
            historical == null ||
            tolerance <= 0.0
        ) {
            return null
        }

        val distance =
            abs(
                current -
                        historical
            )

        return (
                1.0 -
                        distance /
                        tolerance
                )
            .coerceIn(
                0.0,
                1.0
            )
    }

    private fun signalOverlap(
        currentSignals: List<String>,
        historicalSignals: List<String>
    ): Double? {

        val current =
            currentSignals
                .map {
                    normalizeSignal(
                        it
                    )
                }
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        val historical =
            historicalSignals
                .map {
                    normalizeSignal(
                        it
                    )
                }
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        if (
            current.isEmpty() &&
            historical.isEmpty()
        ) {
            return 0.5
        }

        if (
            current.isEmpty() ||
            historical.isEmpty()
        ) {
            return 0.0
        }

        val intersection =
            current
                .intersect(
                    historical
                )
                .size

        val union =
            current
                .union(
                    historical
                )
                .size

        if (
            union ==
            0
        ) {
            return 0.0
        }

        return intersection
            .toDouble() /
                union.toDouble()
    }

    private fun normalizeSignal(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                "bullish ",
                ""
            )
            .replace(
                "bearish ",
                ""
            )
            .replace(
                "volume confirmation",
                "volume"
            )
            .replace(
                "volume expansion",
                "volume"
            )
            .trim()
    }

    private fun bullishEmaAlignment(
        price: Double,
        ema9: Double?,
        ema20: Double?
    ): Boolean? {

        if (
            ema9 == null ||
            ema20 == null
        ) {
            return null
        }

        return price >
                ema9 &&
                ema9 >
                ema20
    }

    /*
     * =========================================================
     * SUB-SCORES
     * =========================================================
     */

    private fun calculateHistoryMatchScore(
        matches: List<HistoricalTradeMatch>
    ): Int {

        if (
            matches.isEmpty()
        ) {
            return 45
        }

        var weightedOutcome =
            0.0

        var totalWeight =
            0.0

        matches.forEach {
                match ->

            val similarityWeight =
                max(
                    0.10,
                    match.similarityPercent /
                            100.0
                )

            val outcomeScore =
                when {

                    match.actualReturnPercent == null ->
                        50.0

                    match.actualReturnPercent >= 8.0 ->
                        100.0

                    match.actualReturnPercent >= 4.0 ->
                        85.0

                    match.actualReturnPercent > 0.0 ->
                        70.0

                    match.actualReturnPercent == 0.0 ->
                        50.0

                    match.actualReturnPercent > -3.0 ->
                        35.0

                    else ->
                        15.0
                }

            weightedOutcome +=
                outcomeScore *
                        similarityWeight

            totalWeight +=
                similarityWeight
        }

        if (
            totalWeight <=
            0.0
        ) {
            return 45
        }

        return (
                weightedOutcome /
                        totalWeight
                )
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun calculateEntryQualityScore(
        snapshot: TechnicalSnapshot,
        entryPrice: Double,
        records: List<AgentTrainingRecord>
    ): Int {

        var score =
            50.0

        val vwap =
            snapshot.vwap

        if (
            vwap != null &&
            vwap >
            0.0
        ) {

            val distance =
                (
                        (
                                entryPrice -
                                        vwap
                                ) /
                                vwap
                        ) *
                        100.0

            score +=
                when {

                    distance in 0.0..1.5 ->
                        20.0

                    distance in 1.5..3.0 ->
                        10.0

                    distance > 5.0 ->
                        -25.0

                    distance > 3.0 ->
                        -15.0

                    distance < -3.0 ->
                        -20.0

                    distance < 0.0 ->
                        -8.0

                    else ->
                        0.0
                }
        }

        val ema9 =
            snapshot.ema9

        val ema20 =
            snapshot.ema20

        if (
            ema9 != null &&
            ema20 != null
        ) {

            if (
                entryPrice >
                ema9 &&
                ema9 >
                ema20
            ) {

                score +=
                    15.0

            } else if (
                entryPrice <
                ema20
            ) {

                score -=
                    15.0
            }
        }

        /*
         * Use historical entry-efficiency evidence to nudge
         * the current entry score toward the user's observed
         * baseline.
         */
        val averageHistoricalEntryEfficiency =
            averageOrNull(
                records.mapNotNull {
                    it.entryEfficiencyScore
                        ?.toDouble()
                }
            )

        if (
            averageHistoricalEntryEfficiency !=
            null
        ) {

            score =
                score *
                        0.75 +
                        averageHistoricalEntryEfficiency *
                        0.25
        }

        return score
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun calculateRiskQualityScore(
        entryPrice: Double,
        stopPrice: Double?,
        targetPrice: Double?
    ): Int {

        if (
            stopPrice == null ||
            targetPrice == null ||
            entryPrice <= 0.0
        ) {
            return 50
        }

        val risk =
            entryPrice -
                    stopPrice

        val reward =
            targetPrice -
                    entryPrice

        if (
            risk <= 0.0 ||
            reward <= 0.0
        ) {
            return 15
        }

        val ratio =
            reward /
                    risk

        val riskPercent =
            risk /
                    entryPrice *
                    100.0

        var score =
            when {

                ratio >= 3.0 ->
                    95.0

                ratio >= 2.0 ->
                    85.0

                ratio >= 1.5 ->
                    70.0

                ratio >= 1.0 ->
                    50.0

                else ->
                    25.0
            }

        score +=
            when {

                riskPercent <= 0.25 ->
                    -10.0

                riskPercent <= 1.0 ->
                    5.0

                riskPercent <= 3.0 ->
                    8.0

                riskPercent <= 5.0 ->
                    0.0

                riskPercent <= 8.0 ->
                    -12.0

                else ->
                    -25.0
            }

        return score
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun calculateEvidenceConfidenceScore(
        patternProfile: AgentPatternProfile,
        matchCount: Int
    ): Int {

        val matchConfidence =
            when {

                matchCount >= 12 ->
                    90

                matchCount >= 10 ->
                    82

                matchCount >= 7 ->
                    70

                matchCount >= 5 ->
                    58

                matchCount >= 3 ->
                    42

                matchCount >= 1 ->
                    25

                else ->
                    10
            }

        return (
                patternProfile
                    .profileConfidencePercent *
                        0.55 +
                        matchConfidence *
                        0.45
                )
            .toInt()
            .coerceIn(
                0,
                100
            )
    }

    /*
     * =========================================================
     * DECISION
     * =========================================================
     */

    private fun determineDecision(
        overallScore: Int,
        technicalScore: Int,
        historyScore: Int,
        evidenceConfidence: Int,
        warnings: List<String>
    ): AgentTradeDecision {

        val seriousWarnings =
            warnings.count {
                it.startsWith(
                    "HIGH RISK:"
                )
            }

        if (
            seriousWarnings >
            0
        ) {
            return AgentTradeDecision.AVOID
        }

        if (
            overallScore >= 85 &&
            technicalScore >= 75 &&
            historyScore >= 70 &&
            evidenceConfidence >= 45
        ) {

            return AgentTradeDecision.HIGH_CONVICTION
        }

        if (
            overallScore >= 72 &&
            technicalScore >= 65
        ) {

            return AgentTradeDecision.FAVORABLE
        }

        if (
            overallScore >= 58
        ) {

            return AgentTradeDecision.WATCH
        }

        if (
            overallScore >= 42
        ) {

            return AgentTradeDecision.WAIT
        }

        return AgentTradeDecision.AVOID
    }

    private fun calculateDecisionConfidence(
        overallScore: Int,
        evidenceConfidence: Int,
        meaningfulMatches: List<HistoricalTradeMatch>,
        technicalScore: Int
    ): Int {

        val scores =
            listOf(
                overallScore.toDouble(),
                technicalScore.toDouble(),
                evidenceConfidence.toDouble()
            )

        val mean =
            scores.average()

        val variance =
            scores
                .map {
                    (
                            it -
                                    mean
                            ) *
                            (
                                    it -
                                            mean
                                    )
                }
                .average()

        val disagreement =
            sqrt(
                variance
            )

        val agreementPenalty =
            min(
                22.0,
                disagreement *
                        0.7
            )

        val matchBonus =
            min(
                12.0,
                meaningfulMatches.size *
                        1.25
            )

        return (
                evidenceConfidence *
                        0.55 +
                        overallScore *
                        0.35 +
                        matchBonus -
                        agreementPenalty
                )
            .toInt()
            .coerceIn(
                5,
                95
            )
    }

    /*
     * =========================================================
     * PREFERENCES
     * =========================================================
     */

    private fun determinePreferredEntryMethod(
        snapshot: TechnicalSnapshot,
        entryQualityScore: Int,
        patternProfile: AgentPatternProfile
    ): String {

        val distance =
            snapshot
                .distanceFromVwapPercent

        if (
            distance != null &&
            distance >
            3.0
        ) {

            return "Wait for pullback / VWAP or EMA retest"
        }

        if (
            entryQualityScore <
            55
        ) {

            return "Wait for confirmation before entry"
        }

        val weakest =
            patternProfile
                .weakestEnvironment
                ?.title
                ?.lowercase()

        if (
            weakest?.contains(
                "inefficient entries"
            ) ==
            true
        ) {

            return "Retest entry with defined invalidation"
        }

        if (
            snapshot.signals.any {
                it.contains(
                    "retest",
                    ignoreCase =
                        true
                )
            }
        ) {

            return "Confirmed retest"
        }

        return "Current structure is acceptable; avoid chasing extension"
    }

    private fun determinePreferredExitMethod(
        records: List<AgentTrainingRecord>,
        meaningfulMatches: List<HistoricalTradeMatch>,
        patternProfile: AgentPatternProfile
    ): String {

        val matchedIds =
            meaningfulMatches
                .map {
                    it.tradeId
                }
                .toSet()

        val matchedStrategy =
            records
                .filter {
                    it.tradeId in
                            matchedIds
                }
                .mapNotNull {
                    it.bestAlternativeTitle
                }
                .groupingBy {
                    it
                }
                .eachCount()
                .maxByOrNull {
                    it.value
                }
                ?.key

        if (
            matchedStrategy !=
            null
        ) {

            return matchedStrategy
        }

        return patternProfile
            .strategyPatterns
            .firstOrNull()
            ?.strategyTitle
            ?: "Use predefined risk and structure-based exit"
    }

    /*
     * =========================================================
     * EXPLANATION
     * =========================================================
     */

    private fun buildStrengths(
        snapshot: TechnicalSnapshot,
        meaningfulMatches: List<HistoricalTradeMatch>,
        historicalWinRate: Double?,
        historicalAverageReturn: Double?,
        entryQualityScore: Int,
        riskQualityScore: Int
    ): List<String> {

        val strengths =
            mutableListOf<String>()

        if (
            snapshot.technicalScore >=
            70
        ) {

            strengths.add(
                "Current technical score is ${snapshot.technicalScore}/100."
            )
        }

        val vwap =
            snapshot.vwap

        if (
            vwap != null &&
            snapshot.price >
            vwap
        ) {

            strengths.add(
                "Price is currently above VWAP."
            )
        }

        val ema9 =
            snapshot.ema9

        val ema20 =
            snapshot.ema20

        if (
            ema9 != null &&
            ema20 != null &&
            snapshot.price >
            ema9 &&
            ema9 >
            ema20
        ) {

            strengths.add(
                "Price, EMA 9, and EMA 20 are in bullish alignment."
            )
        }

        val volume =
            snapshot.volumeRatio

        if (
            volume != null &&
            volume >=
            1.5
        ) {

            strengths.add(
                "Relative volume is elevated at ${formatOneDecimal(volume)}x."
            )
        }

        if (
            meaningfulMatches.size >=
            5
        ) {

            strengths.add(
                "${meaningfulMatches.size} historical trades meet the current similarity threshold."
            )
        }

        if (
            historicalWinRate !=
            null &&
            historicalWinRate >=
            65.0
        ) {

            strengths.add(
                "Similar historical trades were profitable ${formatOneDecimal(historicalWinRate)}% of the time."
            )
        }

        if (
            historicalAverageReturn !=
            null &&
            historicalAverageReturn >
            0.0
        ) {

            strengths.add(
                "Similar historical trades averaged ${formatSignedPercent(historicalAverageReturn)}."
            )
        }

        if (
            entryQualityScore >=
            70
        ) {

            strengths.add(
                "Current entry-location score is favorable."
            )
        }

        if (
            riskQualityScore >=
            75
        ) {

            strengths.add(
                "The proposed stop/target structure has favorable risk/reward."
            )
        }

        if (
            strengths.isEmpty()
        ) {

            strengths.add(
                "No major personalized strength has been confirmed yet."
            )
        }

        return strengths
    }

    private fun buildWarnings(
        snapshot: TechnicalSnapshot,
        meaningfulMatches: List<HistoricalTradeMatch>,
        historicalWinRate: Double?,
        historicalAverageReturn: Double?,
        entryQualityScore: Int,
        riskQualityScore: Int,
        patternProfile: AgentPatternProfile
    ): List<String> {

        val warnings =
            mutableListOf<String>()

        if (
            snapshot.technicalScore <
            45
        ) {

            warnings.add(
                "HIGH RISK: current technical score is weak."
            )
        }

        val distance =
            snapshot
                .distanceFromVwapPercent

        if (
            distance != null &&
            distance >
            5.0
        ) {

            warnings.add(
                "HIGH RISK: price is extended more than 5% above VWAP."
            )

        } else if (
            distance != null &&
            distance >
            3.0
        ) {

            warnings.add(
                "Entry is extended above VWAP; waiting for a retest may improve location."
            )
        }

        if (
            entryQualityScore <
            45
        ) {

            warnings.add(
                "Current entry-location score is weak."
            )
        }

        if (
            riskQualityScore <
            40
        ) {

            warnings.add(
                "Proposed risk/reward structure is unfavorable or incomplete."
            )
        }

        if (
            meaningfulMatches.size <
            3
        ) {

            warnings.add(
                "Historical similarity evidence is limited."
            )
        }

        if (
            historicalWinRate !=
            null &&
            meaningfulMatches.size >=
            3 &&
            historicalWinRate <
            40.0
        ) {

            warnings.add(
                "Similar historical trades have a low profitable rate."
            )
        }

        if (
            historicalAverageReturn !=
            null &&
            meaningfulMatches.size >=
            3 &&
            historicalAverageReturn <
            0.0
        ) {

            warnings.add(
                "Similar historical trades have a negative average return."
            )
        }

        val weakest =
            patternProfile
                .weakestEnvironment

        if (
            weakest != null &&
            weakest.confidencePercent >=
            40
        ) {

            warnings.add(
                "Personal pattern warning: ${weakest.title} is currently one of your weaker historical environments."
            )
        }

        return warnings
    }

    private fun buildReasoning(
        symbol: String,
        snapshot: TechnicalSnapshot,
        meaningfulMatches: List<HistoricalTradeMatch>,
        historicalWinRate: Double?,
        historicalAverageReturn: Double?,
        technicalScore: Int,
        historyScore: Int,
        entryQualityScore: Int,
        riskQualityScore: Int,
        evidenceConfidence: Int,
        preferredEntryMethod: String,
        preferredExitMethod: String
    ): List<String> {

        val reasoning =
            mutableListOf<String>()

        reasoning.add(
            "$symbol currently has a technical score of $technicalScore/100."
        )

        if (
            meaningfulMatches.isNotEmpty()
        ) {

            reasoning.add(
                "TraDNA found ${meaningfulMatches.size} historically similar trained trades."
            )
        }

        if (
            historicalWinRate !=
            null
        ) {

            reasoning.add(
                "Those matches were profitable ${formatOneDecimal(historicalWinRate)}% of the time."
            )
        }

        if (
            historicalAverageReturn !=
            null
        ) {

            reasoning.add(
                "Their average historical return was ${formatSignedPercent(historicalAverageReturn)}."
            )
        }

        reasoning.add(
            "Personal history-match score: $historyScore/100."
        )

        reasoning.add(
            "Entry-quality score: $entryQualityScore/100."
        )

        reasoning.add(
            "Risk-quality score: $riskQualityScore/100."
        )

        reasoning.add(
            "Evidence confidence: $evidenceConfidence/100."
        )

        if (
            snapshot.signals.isNotEmpty()
        ) {

            reasoning.add(
                "Current signals: ${snapshot.signals.joinToString(", ")}."
            )
        }

        reasoning.add(
            "Preferred entry approach: $preferredEntryMethod."
        )

        reasoning.add(
            "Preferred management rule from current evidence: $preferredExitMethod."
        )

        return reasoning
    }

    private fun buildEvidenceSummary(
        meaningfulMatches: List<HistoricalTradeMatch>,
        winRate: Double?,
        averageReturn: Double?,
        patternProfile: AgentPatternProfile
    ): String {

        if (
            meaningfulMatches.isEmpty()
        ) {

            return "No sufficiently similar historical trades were found. The decision relies more heavily on current technical conditions and the broader personal profile."
        }

        return buildString {

            append(
                "TraDNA matched ${meaningfulMatches.size} historical trades"
            )

            winRate?.let {

                append(
                    ", with ${formatOneDecimal(it)}% profitable"
                )
            }

            averageReturn?.let {

                append(
                    " and ${formatSignedPercent(it)} average return"
                )
            }

            append(
                ". Personal profile confidence is ${patternProfile.profileConfidencePercent}%."
            )
        }
    }

    /*
     * =========================================================
     * UTILITIES
     * =========================================================
     */

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

    private fun formatOneDecimal(
        value: Double
    ): String {

        return String.format(
            java.util.Locale.US,
            "%.1f",
            value
        )
    }

    private fun formatSignedPercent(
        value: Double
    ): String {

        return String.format(
            java.util.Locale.US,
            "%+.2f%%",
            value
        )
    }
}
