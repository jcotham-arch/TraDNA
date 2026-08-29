package com.tradna.APP.coaching

import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.data.TradeStatus
import com.tradna.APP.data.OptionTradeEpisode
import com.tradna.APP.data.OptionTradeStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

enum class CoachingSignalKind {
    STRENGTH,
    WATCH,
    CONTEXT
}

data class CoachingSignal(
    val kind: CoachingSignalKind,
    val title: String,
    val evidence: String
)

data class TradingBehaviorReport(
    val completedTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakevenTrades: Int,
    val winRatePercent: Double?,
    val stockRealizedPnl: Double,
    val optionRealizedPnl: Double,
    val realizedPnl: Double,
    val completedOptionTrades: Int,
    val averageWinner: Double?,
    val averageLoser: Double?,
    val payoffRatio: Double?,
    val profitFactor: Double?,
    val averageHoldingDays: Double?,
    val largestTradeCost: Double?,
    val medianTradeCost: Double?,
    val topSymbol: String?,
    val topSymbolTradeSharePercent: Double?,
    val signals: List<CoachingSignal>,
    val evidenceNote: String
)

object TradingBehaviorReportEngine {
    private val dateFormatter =
        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)

    fun analyze(
        trades: List<TradeEpisode>,
        optionTrades: List<OptionTradeEpisode> = emptyList()
    ): TradingBehaviorReport {
        val completed =
            trades.filter {
                it.status == TradeStatus.CLOSED && it.closeDate != null
            }
        val winners = completed.filter { it.realizedPnl > 0.0 }
        val losers = completed.filter { it.realizedPnl < 0.0 }
        val breakeven = completed.count { abs(it.realizedPnl) < 0.000001 }
        val grossProfit = winners.sumOf { it.realizedPnl }
        val grossLoss = abs(losers.sumOf { it.realizedPnl })
        val completedOptions =
            optionTrades.filter { it.status == OptionTradeStatus.CLOSED }
        val stockRealizedPnl = completed.sumOf { it.realizedPnl }
        val optionRealizedPnl = completedOptions.sumOf { it.realizedPnl }
        val averageWinner = winners.map { it.realizedPnl }.averageOrNull()
        val averageLoser = losers.map { it.realizedPnl }.averageOrNull()
        val payoffRatio =
            if (averageWinner != null && averageLoser != null && averageLoser != 0.0) {
                averageWinner / abs(averageLoser)
            } else {
                null
            }
        val costs = completed.map { it.totalBuyCost }.filter { it > 0.0 }.sorted()
        val medianCost = median(costs)
        val largestCost = costs.maxOrNull()
        val symbolCounts = completed.groupingBy { it.symbol }.eachCount()
        val top = symbolCounts.maxByOrNull { it.value }
        val topShare =
            if (top != null && completed.isNotEmpty()) {
                top.value.toDouble() / completed.size * 100.0
            } else {
                null
            }
        val holdingDays =
            completed.mapNotNull { trade ->
                val open = parseDate(trade.openDate)
                val close = trade.closeDate?.let(::parseDate)
                if (open != null && close != null && !close.isBefore(open)) {
                    ChronoUnit.DAYS.between(open, close).toDouble()
                } else {
                    null
                }
            }

        val signals = mutableListOf<CoachingSignal>()
        if (completed.size < 10) {
            signals += CoachingSignal(
                CoachingSignalKind.CONTEXT,
                "Early evidence",
                "Only ${completed.size} completed trades are available; treat every pattern as preliminary."
            )
        }
        if (completed.size >= 5 && payoffRatio != null) {
            signals +=
                if (payoffRatio >= 1.25) {
                    CoachingSignal(
                        CoachingSignalKind.STRENGTH,
                        "Winners outweigh losers",
                        "The average winner is ${formatRatio(payoffRatio)}× the average loss across ${completed.size} completed trades."
                    )
                } else {
                    CoachingSignal(
                        CoachingSignalKind.WATCH,
                        "Losses outweigh winners",
                        "The average winner is only ${formatRatio(payoffRatio)}× the average loss across ${completed.size} completed trades."
                    )
                }
        }
        if (completed.size >= 5 && top != null && topShare != null && topShare >= 40.0) {
            signals += CoachingSignal(
                CoachingSignalKind.WATCH,
                "Symbol concentration",
                "${top.key} represents ${formatPercent(topShare)} of completed trades (${top.value} of ${completed.size})."
            )
        }
        if (
            completed.size >= 5 && medianCost != null && medianCost > 0.0 &&
            largestCost != null && largestCost >= medianCost * 3.0
        ) {
            signals += CoachingSignal(
                CoachingSignalKind.WATCH,
                "Inconsistent position size",
                "The largest entry cost is ${formatRatio(largestCost / medianCost)}× the median completed-trade cost."
            )
        }
        if (completed.size >= 10 && winners.size.toDouble() / completed.size >= 0.60) {
            signals += CoachingSignal(
                CoachingSignalKind.STRENGTH,
                "Positive outcome frequency",
                "${formatPercent(winners.size.toDouble() / completed.size * 100.0)} of completed trades are profitable."
            )
        }
        if (signals.isEmpty()) {
            signals += CoachingSignal(
                CoachingSignalKind.CONTEXT,
                "Baseline established",
                "No current rule crossed a coaching threshold. More trades will make the profile more informative."
            )
        }

        return TradingBehaviorReport(
            completedTrades = completed.size,
            winningTrades = winners.size,
            losingTrades = losers.size,
            breakevenTrades = breakeven,
            winRatePercent = completed.takeIf { it.isNotEmpty() }
                ?.let { winners.size.toDouble() / it.size * 100.0 },
            stockRealizedPnl = stockRealizedPnl,
            optionRealizedPnl = optionRealizedPnl,
            realizedPnl = stockRealizedPnl + optionRealizedPnl,
            completedOptionTrades = completedOptions.size,
            averageWinner = averageWinner,
            averageLoser = averageLoser,
            payoffRatio = payoffRatio,
            profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else null,
            averageHoldingDays = holdingDays.averageOrNull(),
            largestTradeCost = largestCost,
            medianTradeCost = medianCost,
            topSymbol = top?.key,
            topSymbolTradeSharePercent = topShare,
            signals = signals,
            evidenceNote = "Combined P&L includes reconstructed completed stock and option trades. Interest, deposits, withdrawals, transfers, and open positions are excluded. FIFO stock cost basis is assumed. This is coaching evidence, not investment advice."
        )
    }

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value, dateFormatter)
        } catch (_: Exception) {
            null
        }

    private fun median(values: List<Double>): Double? =
        when {
            values.isEmpty() -> null
            values.size % 2 == 1 -> values[values.size / 2]
            else -> (values[values.size / 2 - 1] + values[values.size / 2]) / 2.0
        }

    private fun Iterable<Double>.averageOrNull(): Double? {
        val values = toList()
        return if (values.isEmpty()) null else values.average()
    }

    private fun formatRatio(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatPercent(value: Double): String =
        String.format(Locale.US, "%.1f%%", value)
}
