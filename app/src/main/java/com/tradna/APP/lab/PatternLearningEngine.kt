package com.tradna.APP.lab

import kotlin.math.abs

enum class PatternStrength {
    INSUFFICIENT,
    EARLY,
    MODERATE,
    STRONG
}

data class PatternEvidence(
    val title: String,
    val category: String,
    val summary: String,
    val sampleSize: Int,
    val profitableRatePercent: Double?,
    val averageReturnPercent: Double?,
    val averageTechnicalScore: Double?,
    val confidencePercent: Int,
    val strength: PatternStrength
)

data class StrategyPattern(
    val strategyTitle: String,
    val timesBest: Int,
    val averageImprovementPercent: Double?,
    val averageImprovementDollars: Double?,
    val confidencePercent: Int
)

data class SignalPattern(
    val signal: String,
    val occurrences: Int,
    val profitableRatePercent: Double?,
    val averageReturnPercent: Double?,
    val averageTechnicalScore: Double?,
    val confidencePercent: Int
)

data class SymbolPattern(
    val symbol: String,
    val trades: Int,
    val profitableRatePercent: Double?,
    val averageReturnPercent: Double?,
    val realizedPnl: Double,
    val confidencePercent: Int
)

data class AgentPatternProfile(
    val totalRecords: Int,

    val overallProfitableRatePercent: Double?,
    val overallAverageReturnPercent: Double?,

    val strongTechnicalEntryRatePercent: Double?,
    val efficientEntryRatePercent: Double?,
    val efficientExitRatePercent: Double?,
    val earlyExitRatePercent: Double?,
    val highAdverseExcursionRatePercent: Double?,
    val alternativeOutperformedRatePercent: Double?,

    val averageTechnicalScore: Double?,
    val averageEntryEfficiency: Double?,
    val averageExitEfficiency: Double?,
    val averageMissedUpsidePercent: Double?,
    val averageAlternativeImprovementPercent: Double?,

    val bestEnvironment: PatternEvidence?,
    val weakestEnvironment: PatternEvidence?,

    val signalPatterns: List<SignalPattern>,
    val strategyPatterns: List<StrategyPattern>,
    val symbolPatterns: List<SymbolPattern>,
    val vwapPatterns: List<PatternEvidence>,

    val behavioralPatterns: List<PatternEvidence>,
    val coachingPriorities: List<String>,

    val profileConfidencePercent: Int,
    val profileStrength: PatternStrength
)

object PatternLearningEngine {

    fun analyze(
        records: List<AgentTrainingRecord>
    ): AgentPatternProfile {

        if (records.isEmpty()) {

            return AgentPatternProfile(
                totalRecords = 0,

                overallProfitableRatePercent = null,
                overallAverageReturnPercent = null,

                strongTechnicalEntryRatePercent = null,
                efficientEntryRatePercent = null,
                efficientExitRatePercent = null,
                earlyExitRatePercent = null,
                highAdverseExcursionRatePercent = null,
                alternativeOutperformedRatePercent = null,

                averageTechnicalScore = null,
                averageEntryEfficiency = null,
                averageExitEfficiency = null,
                averageMissedUpsidePercent = null,
                averageAlternativeImprovementPercent = null,

                bestEnvironment = null,
                weakestEnvironment = null,

                signalPatterns = emptyList(),
                strategyPatterns = emptyList(),
                symbolPatterns = emptyList(),
                vwapPatterns = emptyList(),

                behavioralPatterns = emptyList(),
                coachingPriorities =
                    listOf(
                        "Add more historical training records before TraDNA creates personalized behavioral rules."
                    ),

                profileConfidencePercent = 0,
                profileStrength = PatternStrength.INSUFFICIENT
            )
        }

        val total =
            records.size

        val overallWinRate =
            percent(
                records.count {
                    it.profitableTrade
                },
                total
            )

        val overallAverageReturn =
            averageOrNull(
                records.mapNotNull {
                    it.actualReturnPercent
                }
            )

        val technicalAverage =
            averageOrNull(
                records.mapNotNull {
                    it.entryTechnicalScore
                        ?.toDouble()
                }
            )

        val entryEfficiencyAverage =
            averageOrNull(
                records.mapNotNull {
                    it.entryEfficiencyScore
                        ?.toDouble()
                }
            )

        val exitEfficiencyAverage =
            averageOrNull(
                records.mapNotNull {
                    it.exitEfficiencyScore
                        ?.toDouble()
                }
            )

        val missedUpsideAverage =
            averageOrNull(
                records.mapNotNull {
                    it.missedUpsidePercent
                }
            )

        val alternativeImprovementAverage =
            averageOrNull(
                records.mapNotNull {
                    it.improvementVsActualPercent
                }
            )

        val environmentGroups =
            buildEnvironmentGroups(
                records
            )

        val bestEnvironment =
            environmentGroups
                .filter {
                    it.sampleSize >= minimumUsefulSample(
                        total
                    )
                }
                .maxWithOrNull(
                    compareBy<PatternEvidence> {
                        it.profitableRatePercent
                            ?: Double.NEGATIVE_INFINITY
                    }
                        .thenBy {
                            it.averageReturnPercent
                                ?: Double.NEGATIVE_INFINITY
                        }
                )

        val weakestEnvironment =
            environmentGroups
                .filter {
                    it.sampleSize >= minimumUsefulSample(
                        total
                    )
                }
                .minWithOrNull(
                    compareBy<PatternEvidence> {
                        it.profitableRatePercent
                            ?: Double.POSITIVE_INFINITY
                    }
                        .thenBy {
                            it.averageReturnPercent
                                ?: Double.POSITIVE_INFINITY
                        }
                )

        val signalPatterns =
            buildSignalPatterns(
                records
            )

        val strategyPatterns =
            buildStrategyPatterns(
                records
            )

        val symbolPatterns =
            buildSymbolPatterns(
                records
            )

        val vwapPatterns =
            buildVwapPatterns(
                records
            )

        val behavioralPatterns =
            buildBehavioralPatterns(
                records
            )

        val coachingPriorities =
            buildCoachingPriorities(
                records = records,
                bestEnvironment = bestEnvironment,
                weakestEnvironment = weakestEnvironment,
                behavioralPatterns = behavioralPatterns,
                strategyPatterns = strategyPatterns
            )

        val profileConfidence =
            profileConfidence(
                total
            )

        return AgentPatternProfile(
            totalRecords =
                total,

            overallProfitableRatePercent =
                overallWinRate,

            overallAverageReturnPercent =
                overallAverageReturn,

            strongTechnicalEntryRatePercent =
                percent(
                    records.count {
                        it.strongTechnicalEntry
                    },
                    total
                ),

            efficientEntryRatePercent =
                percent(
                    records.count {
                        it.efficientEntry
                    },
                    total
                ),

            efficientExitRatePercent =
                percent(
                    records.count {
                        it.efficientExit
                    },
                    total
                ),

            earlyExitRatePercent =
                percent(
                    records.count {
                        it.earlyExitCandidate
                    },
                    total
                ),

            highAdverseExcursionRatePercent =
                percent(
                    records.count {
                        it.highAdverseExcursion
                    },
                    total
                ),

            alternativeOutperformedRatePercent =
                percent(
                    records.count {
                        it.alternativeOutperformedActual
                    },
                    total
                ),

            averageTechnicalScore =
                technicalAverage,

            averageEntryEfficiency =
                entryEfficiencyAverage,

            averageExitEfficiency =
                exitEfficiencyAverage,

            averageMissedUpsidePercent =
                missedUpsideAverage,

            averageAlternativeImprovementPercent =
                alternativeImprovementAverage,

            bestEnvironment =
                bestEnvironment,

            weakestEnvironment =
                weakestEnvironment,

            signalPatterns =
                signalPatterns,

            strategyPatterns =
                strategyPatterns,

            symbolPatterns =
                symbolPatterns,

            vwapPatterns =
                vwapPatterns,

            behavioralPatterns =
                behavioralPatterns,

            coachingPriorities =
                coachingPriorities,

            profileConfidencePercent =
                profileConfidence,

            profileStrength =
                strengthForConfidence(
                    profileConfidence
                )
        )
    }

    private fun buildSymbolPatterns(
        records: List<AgentTrainingRecord>
    ): List<SymbolPattern> =
        records
            .filter { it.symbol.isNotBlank() }
            .groupBy { it.symbol.trim().uppercase() }
            .map { (symbol, matching) ->
                SymbolPattern(
                    symbol = symbol,
                    trades = matching.size,
                    profitableRatePercent = percent(
                        matching.count { it.profitableTrade },
                        matching.size
                    ),
                    averageReturnPercent = averageOrNull(
                        matching.mapNotNull { it.actualReturnPercent }
                    ),
                    realizedPnl = matching.sumOf { it.actualRealizedPnl },
                    confidencePercent = confidenceForSample(
                        matching.size,
                        records.size
                    )
                )
            }
            .sortedWith(
                compareByDescending<SymbolPattern> { it.trades }
                    .thenByDescending { it.realizedPnl }
                    .thenBy { it.symbol }
            )

    private fun buildVwapPatterns(
        records: List<AgentTrainingRecord>
    ): List<PatternEvidence> {
        val withVwap = records.filter { it.entryDistanceFromVwapPercent != null }
        if (withVwap.isEmpty()) return emptyList()

        return listOf(
            evidenceForRecords(
                title = "Below VWAP",
                category = "ENTRY LOCATION",
                summary = "Entry was more than 0.25% below reconstructed VWAP.",
                records = withVwap.filter { it.entryDistanceFromVwapPercent!! < -0.25 }
            ),
            evidenceForRecords(
                title = "Near VWAP",
                category = "ENTRY LOCATION",
                summary = "Entry was within 0.25% of reconstructed VWAP.",
                records = withVwap.filter { abs(it.entryDistanceFromVwapPercent!!) <= 0.25 }
            ),
            evidenceForRecords(
                title = "Above VWAP",
                category = "ENTRY LOCATION",
                summary = "Entry was more than 0.25% above reconstructed VWAP.",
                records = withVwap.filter { it.entryDistanceFromVwapPercent!! > 0.25 }
            )
        ).filter { it.sampleSize > 0 }
    }

    private fun buildEnvironmentGroups(
        records: List<AgentTrainingRecord>
    ): List<PatternEvidence> {

        val groups =
            mutableListOf<PatternEvidence>()

        addEnvironmentGroup(
            groups = groups,
            title = "Strong technical entries",
            category = "ENTRY ENVIRONMENT",
            summary =
                "Trades entered with a TraDNA technical score of 70 or higher.",
            records =
                records.filter {
                    (
                            it.entryTechnicalScore
                                ?: 0
                            ) >= 70
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Weak technical entries",
            category = "ENTRY ENVIRONMENT",
            summary =
                "Trades entered with a TraDNA technical score below 50.",
            records =
                records.filter {
                    val score =
                        it.entryTechnicalScore

                    score != null &&
                            score < 50
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Above VWAP entries",
            category = "VWAP",
            summary =
                "Entries where the historical entry price was above reconstructed VWAP.",
            records =
                records.filter {

                    val vwap =
                        it.entryVwap

                    vwap != null &&
                            it.actualEntryPrice >
                            vwap
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Below VWAP entries",
            category = "VWAP",
            summary =
                "Entries where the historical entry price was below reconstructed VWAP.",
            records =
                records.filter {

                    val vwap =
                        it.entryVwap

                    vwap != null &&
                            it.actualEntryPrice <
                            vwap
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Bullish EMA alignment",
            category = "TREND",
            summary =
                "Entries where price was above EMA 9 and EMA 9 was above EMA 20.",
            records =
                records.filter {

                    val ema9 =
                        it.entryEma9

                    val ema20 =
                        it.entryEma20

                    ema9 != null &&
                            ema20 != null &&
                            it.actualEntryPrice >
                            ema9 &&
                            ema9 >
                            ema20
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Elevated relative volume",
            category = "VOLUME",
            summary =
                "Entries with relative volume of at least 1.5x.",
            records =
                records.filter {
                    (
                            it.entryRelativeVolume
                                ?: 0.0
                            ) >= 1.5
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Low relative volume",
            category = "VOLUME",
            summary =
                "Entries with relative volume below 1.0x.",
            records =
                records.filter {

                    val ratio =
                        it.entryRelativeVolume

                    ratio != null &&
                            ratio <
                            1.0
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Efficient entries",
            category = "EXECUTION",
            summary =
                "Historical entries with an entry-efficiency score of 70 or higher.",
            records =
                records.filter {
                    it.efficientEntry
                }
        )

        addEnvironmentGroup(
            groups = groups,
            title = "Inefficient entries",
            category = "EXECUTION",
            summary =
                "Historical entries with an entry-efficiency score below 50.",
            records =
                records.filter {

                    val score =
                        it.entryEfficiencyScore

                    score != null &&
                            score <
                            50
                }
        )

        return groups
    }

    private fun addEnvironmentGroup(
        groups: MutableList<PatternEvidence>,
        title: String,
        category: String,
        summary: String,
        records: List<AgentTrainingRecord>
    ) {

        if (
            records.isEmpty()
        ) {
            return
        }

        groups.add(
            evidenceForRecords(
                title = title,
                category = category,
                summary = summary,
                records = records
            )
        )
    }

    private fun buildSignalPatterns(
        records: List<AgentTrainingRecord>
    ): List<SignalPattern> {

        val signalNames =
            records
                .flatMap {
                    it.entrySignals
                }
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        return signalNames
            .map { signal ->

                val matching =
                    records.filter { record ->

                        record.entrySignals.any {
                            it.equals(
                                signal,
                                ignoreCase = true
                            )
                        }
                    }

                SignalPattern(
                    signal =
                        signal,

                    occurrences =
                        matching.size,

                    profitableRatePercent =
                        percent(
                            matching.count {
                                it.profitableTrade
                            },
                            matching.size
                        ),

                    averageReturnPercent =
                        averageOrNull(
                            matching.mapNotNull {
                                it.actualReturnPercent
                            }
                        ),

                    averageTechnicalScore =
                        averageOrNull(
                            matching.mapNotNull {
                                it.entryTechnicalScore
                                    ?.toDouble()
                            }
                        ),

                    confidencePercent =
                        confidenceForSample(
                            matching.size,
                            records.size
                        )
                )
            }
            .sortedWith(
                compareByDescending<SignalPattern> {
                    it.confidencePercent
                }
                    .thenByDescending {
                        it.profitableRatePercent
                            ?: Double.NEGATIVE_INFINITY
                    }
                    .thenByDescending {
                        it.averageReturnPercent
                            ?: Double.NEGATIVE_INFINITY
                    }
            )
    }

    private fun buildStrategyPatterns(
        records: List<AgentTrainingRecord>
    ): List<StrategyPattern> {

        val groups =
            records
                .filter {
                    !it.bestAlternativeTitle
                        .isNullOrBlank()
                }
                .groupBy {
                    it.bestAlternativeTitle!!
                }

        return groups
            .map {
                    entry ->

                val title =
                    entry.key

                val matching =
                    entry.value

                StrategyPattern(
                    strategyTitle =
                        title,

                    timesBest =
                        matching.size,

                    averageImprovementPercent =
                        averageOrNull(
                            matching.mapNotNull {
                                it.improvementVsActualPercent
                            }
                        ),

                    averageImprovementDollars =
                        averageOrNull(
                            matching.mapNotNull {
                                it.improvementVsActualDollars
                            }
                        ),

                    confidencePercent =
                        confidenceForSample(
                            matching.size,
                            records.size
                        )
                )
            }
            .sortedWith(
                compareByDescending<StrategyPattern> {
                    it.timesBest
                }
                    .thenByDescending {
                        it.averageImprovementPercent
                            ?: Double.NEGATIVE_INFINITY
                    }
            )
    }

    private fun buildBehavioralPatterns(
        records: List<AgentTrainingRecord>
    ): List<PatternEvidence> {

        val patterns =
            mutableListOf<PatternEvidence>()

        val earlyExits =
            records.filter {
                it.earlyExitCandidate
            }

        if (
            earlyExits.isNotEmpty()
        ) {

            patterns.add(
                evidenceForRecords(
                    title =
                        "Early profit-taking candidate",
                    category =
                        "EXIT BEHAVIOR",
                    summary =
                        "These trades continued at least 5% higher after the historical exit.",
                    records =
                        earlyExits
                )
            )
        }

        val adverseTrades =
            records.filter {
                it.highAdverseExcursion
            }

        if (
            adverseTrades.isNotEmpty()
        ) {

            patterns.add(
                evidenceForRecords(
                    title =
                        "High adverse excursion",
                    category =
                        "RISK",
                    summary =
                        "These trades moved at least 5% against the entry after execution.",
                    records =
                        adverseTrades
                )
            )
        }

        val weakEntries =
            records.filter {

                val score =
                    it.entryEfficiencyScore

                score != null &&
                        score <
                        50
            }

        if (
            weakEntries.isNotEmpty()
        ) {

            patterns.add(
                evidenceForRecords(
                    title =
                        "Entry location weakness",
                    category =
                        "ENTRY BEHAVIOR",
                    summary =
                        "These trades had entry-efficiency scores below 50.",
                    records =
                        weakEntries
                )
            )
        }

        val inefficientExits =
            records.filter {

                val score =
                    it.exitEfficiencyScore

                score != null &&
                        score <
                        50
            }

        if (
            inefficientExits.isNotEmpty()
        ) {

            patterns.add(
                evidenceForRecords(
                    title =
                        "Exit efficiency weakness",
                    category =
                        "EXIT BEHAVIOR",
                    summary =
                        "These trades captured less than half of the modeled local exit opportunity.",
                    records =
                        inefficientExits
                )
            )
        }

        val alternativeWins =
            records.filter {
                it.alternativeOutperformedActual
            }

        if (
            alternativeWins.isNotEmpty()
        ) {

            patterns.add(
                evidenceForRecords(
                    title =
                        "Rule-based alternative advantage",
                    category =
                        "PROCESS",
                    summary =
                        "A standardized alternative produced a higher modeled return than the historical execution.",
                    records =
                        alternativeWins
                )
            )
        }

        return patterns
            .sortedWith(
                compareByDescending<PatternEvidence> {
                    it.confidencePercent
                }
                    .thenByDescending {
                        it.sampleSize
                    }
            )
    }

    private fun evidenceForRecords(
        title: String,
        category: String,
        summary: String,
        records: List<AgentTrainingRecord>
    ): PatternEvidence {

        val sample =
            records.size

        val confidence =
            confidenceForSample(
                sample,
                records.size
            )

        return PatternEvidence(
            title =
                title,

            category =
                category,

            summary =
                summary,

            sampleSize =
                sample,

            profitableRatePercent =
                percent(
                    records.count {
                        it.profitableTrade
                    },
                    sample
                ),

            averageReturnPercent =
                averageOrNull(
                    records.mapNotNull {
                        it.actualReturnPercent
                    }
                ),

            averageTechnicalScore =
                averageOrNull(
                    records.mapNotNull {
                        it.entryTechnicalScore
                            ?.toDouble()
                    }
                ),

            confidencePercent =
                confidence,

            strength =
                strengthForConfidence(
                    confidence
                )
        )
    }

    private fun buildCoachingPriorities(
        records: List<AgentTrainingRecord>,
        bestEnvironment: PatternEvidence?,
        weakestEnvironment: PatternEvidence?,
        behavioralPatterns: List<PatternEvidence>,
        strategyPatterns: List<StrategyPattern>
    ): List<String> {

        val priorities =
            mutableListOf<String>()

        val total =
            records.size

        val earlyExitCount =
            records.count {
                it.earlyExitCandidate
            }

        val weakEntryCount =
            records.count {

                val score =
                    it.entryEfficiencyScore

                score != null &&
                        score <
                        50
            }

        val inefficientExitCount =
            records.count {

                val score =
                    it.exitEfficiencyScore

                score != null &&
                        score <
                        50
            }

        val highAdverseCount =
            records.count {
                it.highAdverseExcursion
            }

        if (
            earlyExitCount >=
            minimumUsefulSample(
                total
            )
        ) {

            val averageMissed =
                averageOrNull(
                    records
                        .filter {
                            it.earlyExitCandidate
                        }
                        .mapNotNull {
                            it.missedUpsidePercent
                        }
                )

            priorities.add(
                buildString {

                    append(
                        "Exit discipline: "
                    )

                    append(
                        "$earlyExitCount of $total trained trades were early-exit candidates"
                    )

                    if (
                        averageMissed !=
                        null
                    ) {

                        append(
                            ", with average modeled post-exit upside of ${formatPercent(averageMissed)}"
                        )
                    }

                    append(
                        "."
                    )
                }
            )
        }

        if (
            weakEntryCount >=
            minimumUsefulSample(
                total
            )
        ) {

            priorities.add(
                "Entry location: $weakEntryCount of $total trained trades had entry-efficiency scores below 50. Prioritize retest and confirmation exercises."
            )
        }

        if (
            inefficientExitCount >=
            minimumUsefulSample(
                total
            )
        ) {

            priorities.add(
                "Exit efficiency: $inefficientExitCount of $total trained trades captured less than half of the modeled local exit opportunity."
            )
        }

        if (
            highAdverseCount >=
            minimumUsefulSample(
                total
            )
        ) {

            priorities.add(
                "Risk control: $highAdverseCount of $total trained trades experienced at least 5% adverse excursion after entry."
            )
        }

        if (
            bestEnvironment !=
            null
        ) {

            priorities.add(
                "Lean into evidence: ${bestEnvironment.title} currently has the strongest repeatable historical profile across ${bestEnvironment.sampleSize} trained trades."
            )
        }

        if (
            weakestEnvironment !=
            null &&
            weakestEnvironment.title !=
            bestEnvironment?.title
        ) {

            priorities.add(
                "Avoid or demand more confirmation: ${weakestEnvironment.title} currently has the weakest repeatable historical profile across ${weakestEnvironment.sampleSize} trained trades."
            )
        }

        val topStrategy =
            strategyPatterns.firstOrNull()

        if (
            topStrategy !=
            null &&
            topStrategy.timesBest >=
            minimumUsefulSample(
                total
            )
        ) {

            priorities.add(
                buildString {

                    append(
                        "Exit/management experiment: ${topStrategy.strategyTitle} was the best modeled alternative ${topStrategy.timesBest} times"
                    )

                    val improvement =
                        topStrategy.averageImprovementPercent

                    if (
                        improvement !=
                        null
                    ) {

                        append(
                            ", averaging ${formatSignedPercent(improvement)} versus the actual trade"
                        )
                    }

                    append(
                        ". Validate this rule in Replay before treating it as a preferred strategy."
                    )
                }
            )
        }

        if (
            priorities.isEmpty()
        ) {

            priorities.add(
                "The dataset is still early. Continue adding trades before changing your process based on small-sample patterns."
            )
        }

        /*
         * Avoid overwhelming the user with every statistical
         * observation at once. These are the highest-priority
         * coaching themes for the next iteration of Train.
         */
        return priorities
            .take(
                6
            )
    }

    private fun profileConfidence(
        sampleSize: Int
    ): Int {

        return when {

            sampleSize >= 100 ->
                95

            sampleSize >= 75 ->
                90

            sampleSize >= 50 ->
                84

            sampleSize >= 40 ->
                78

            sampleSize >= 30 ->
                70

            sampleSize >= 20 ->
                60

            sampleSize >= 15 ->
                52

            sampleSize >= 10 ->
                42

            sampleSize >= 5 ->
                28

            else ->
                15
        }
    }

    private fun confidenceForSample(
        sampleSize: Int,
        totalRecords: Int
    ): Int {

        if (
            sampleSize <= 0 ||
            totalRecords <= 0
        ) {
            return 0
        }

        /*
         * Absolute sample size matters more than percentage
         * coverage. A pattern observed 3 times out of 4 is
         * still weak evidence even though it covers 75% of
         * the dataset.
         */
        val sampleConfidence =
            when {

                sampleSize >= 50 ->
                    90

                sampleSize >= 30 ->
                    80

                sampleSize >= 20 ->
                    70

                sampleSize >= 15 ->
                    62

                sampleSize >= 10 ->
                    52

                sampleSize >= 7 ->
                    42

                sampleSize >= 5 ->
                    32

                sampleSize >= 3 ->
                    22

                else ->
                    12
            }

        val coverage =
            (
                    sampleSize.toDouble() /
                            totalRecords.toDouble() *
                            100.0
                    )
                .coerceIn(
                    0.0,
                    100.0
                )

        val coverageBonus =
            when {

                coverage >= 75.0 ->
                    5

                coverage >= 50.0 ->
                    3

                coverage >= 25.0 ->
                    1

                else ->
                    0
            }

        return (
                sampleConfidence +
                        coverageBonus
                )
            .coerceIn(
                0,
                95
            )
    }

    private fun strengthForConfidence(
        confidence: Int
    ): PatternStrength {

        return when {

            confidence >= 75 ->
                PatternStrength.STRONG

            confidence >= 50 ->
                PatternStrength.MODERATE

            confidence >= 25 ->
                PatternStrength.EARLY

            else ->
                PatternStrength.INSUFFICIENT
        }
    }

    private fun minimumUsefulSample(
        totalRecords: Int
    ): Int {

        return when {

            totalRecords >= 50 ->
                8

            totalRecords >= 30 ->
                6

            totalRecords >= 20 ->
                5

            totalRecords >= 10 ->
                3

            else ->
                2
        }
    }

    private fun percent(
        numerator: Int,
        denominator: Int
    ): Double? {

        if (
            denominator <= 0
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

    private fun formatPercent(
        value: Double
    ): String {

        return String.format(
            java.util.Locale.US,
            "%.2f%%",
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
