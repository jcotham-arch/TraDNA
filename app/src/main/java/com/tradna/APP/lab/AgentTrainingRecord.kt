package com.tradna.APP.lab

import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.replay.CounterfactualReport
import com.tradna.APP.replay.HistoricalTradeAnalysis

data class AgentTrainingRecord(

    val id: String,

    val tradeId: String,
    val symbol: String,

    val openDate: String,
    val closeDate: String?,

    /*
     * ACTUAL EXECUTION
     */
    val actualEntryPrice: Double,
    val actualExitPrice: Double?,
    val actualReturnPercent: Double?,
    val actualRealizedPnl: Double,

    /*
     * TECHNICAL STATE AT ENTRY
     */
    val entryTechnicalScore: Int?,
    val entryVwap: Double?,
    val entryEma9: Double?,
    val entryEma20: Double?,
    val entryRelativeVolume: Double?,
    val entryDistanceFromVwapPercent: Double?,

    val entrySignals: List<String>,

    /*
     * TRADE QUALITY
     */
    val entryEfficiencyScore: Int?,
    val exitEfficiencyScore: Int?,
    val totalEfficiencyScore: Int?,

    val maximumFavorableExcursionPercent: Double?,
    val maximumAdverseExcursionPercent: Double?,

    val missedUpsidePercent: Double?,

    /*
     * COUNTERFACTUAL LEARNING
     */
    val bestAlternativeId: String?,
    val bestAlternativeTitle: String?,
    val bestAlternativeReturnPercent: Double?,
    val bestAlternativeEstimatedPnl: Double?,

    val improvementVsActualPercent: Double?,
    val improvementVsActualDollars: Double?,

    /*
     * TEACHING LABELS
     */
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<String>,

    val lessonTitle: String,
    val lessonSummary: String,

    /*
     * AGENT LABELS
     *
     * These provide standardized machine-readable
     * descriptions TraDNA can aggregate across trades.
     */
    val profitableTrade: Boolean,
    val strongTechnicalEntry: Boolean,
    val efficientEntry: Boolean,
    val efficientExit: Boolean,
    val earlyExitCandidate: Boolean,
    val highAdverseExcursion: Boolean,
    val alternativeOutperformedActual: Boolean,

    /*
     * Dataset versioning allows us to improve the
     * training-record format later without destroying
     * older records.
     */
    val schemaVersion: Int = 1
)

object AgentTrainingEngine {

    fun buildRecord(
        trade: TradeEpisode,
        analysis: HistoricalTradeAnalysis,
        counterfactualReport: CounterfactualReport
    ): AgentTrainingRecord {

        val best =
            counterfactualReport
                .bestScenario

        val profitableTrade =
            analysis.actualRealizedPnl > 0.0

        val strongTechnicalEntry =
            (
                    analysis.entryTechnicalScore
                        ?: 0
                    ) >= 70

        val efficientEntry =
            (
                    analysis.entryEfficiencyScore
                        ?: 0
                    ) >= 70

        val efficientExit =
            (
                    analysis.exitEfficiencyScore
                        ?: 0
                    ) >= 70

        val earlyExitCandidate =
            (
                    analysis.missedUpsidePercent
                        ?: 0.0
                    ) >= 5.0

        val highAdverseExcursion =
            (
                    analysis.maximumAdverseExcursionPercent
                        ?: 0.0
                    ) <= -5.0

        val alternativeOutperformedActual =
            (
                    best
                        ?.improvementVsActualPercent
                        ?: 0.0
                    ) > 0.0

        return AgentTrainingRecord(

            id =
                buildRecordId(
                    trade
                ),

            tradeId =
                trade.id,

            symbol =
                trade.symbol,

            openDate =
                trade.openDate,

            closeDate =
                trade.closeDate,

            actualEntryPrice =
                analysis.actualEntryPrice,

            actualExitPrice =
                analysis.actualExitPrice,

            actualReturnPercent =
                analysis.actualReturnPercent,

            actualRealizedPnl =
                analysis.actualRealizedPnl,

            entryTechnicalScore =
                analysis.entryTechnicalScore,

            entryVwap =
                analysis.entryVwap,

            entryEma9 =
                analysis.entryEma9,

            entryEma20 =
                analysis.entryEma20,

            entryRelativeVolume =
                analysis.entryRelativeVolume,

            entryDistanceFromVwapPercent =
                analysis.entryDistanceFromVwapPercent,

            entrySignals =
                analysis.entrySignals,

            entryEfficiencyScore =
                analysis.entryEfficiencyScore,

            exitEfficiencyScore =
                analysis.exitEfficiencyScore,

            totalEfficiencyScore =
                analysis.tradeEfficiencyScore,

            maximumFavorableExcursionPercent =
                analysis
                    .maximumFavorableExcursionPercent,

            maximumAdverseExcursionPercent =
                analysis
                    .maximumAdverseExcursionPercent,

            missedUpsidePercent =
                analysis.missedUpsidePercent,

            bestAlternativeId =
                best?.id,

            bestAlternativeTitle =
                best?.title,

            bestAlternativeReturnPercent =
                best?.returnPercent,

            bestAlternativeEstimatedPnl =
                best?.estimatedPnl,

            improvementVsActualPercent =
                best?.improvementVsActualPercent,

            improvementVsActualDollars =
                best?.improvementVsActualDollars,

            strengths =
                analysis.strengths,

            weaknesses =
                analysis.weaknesses,

            recommendations =
                analysis.recommendations,

            lessonTitle =
                analysis.lessonTitle,

            lessonSummary =
                analysis.lessonSummary,

            profitableTrade =
                profitableTrade,

            strongTechnicalEntry =
                strongTechnicalEntry,

            efficientEntry =
                efficientEntry,

            efficientExit =
                efficientExit,

            earlyExitCandidate =
                earlyExitCandidate,

            highAdverseExcursion =
                highAdverseExcursion,

            alternativeOutperformedActual =
                alternativeOutperformedActual
        )
    }

    private fun buildRecordId(
        trade: TradeEpisode
    ): String {

        return listOf(
            trade.id,
            trade.symbol,
            trade.openDate,
            trade.closeDate ?: "OPEN"
        )
            .joinToString(
                separator = "_"
            )
            .replace(
                "/",
                "-"
            )
            .replace(
                " ",
                "_"
            )
    }
}