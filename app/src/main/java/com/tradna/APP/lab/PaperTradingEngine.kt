package com.tradna.APP.lab

import kotlin.math.floor

const val PAPER_STARTING_CASH = 5_000.0
const val PAPER_MAX_ENTRY_PERCENT = 10.0

data class PaperPosition(
    val predictionId: String,
    val symbol: String,
    val openedAtEpochMillis: Long,
    val quantity: Double,
    val entryPrice: Double,
    val lastPrice: Double,
    val decision: String,
    val confidencePercent: Int,
    val stopPrice: Double?,
    val targetPrice: Double?
) {
    val marketValue: Double get() = quantity * lastPrice
    val unrealizedPnl: Double get() = quantity * (lastPrice - entryPrice)
}

data class PaperAccount(
    val startingCash: Double = PAPER_STARTING_CASH,
    val cash: Double = PAPER_STARTING_CASH,
    val realizedPnl: Double = 0.0,
    val positions: List<PaperPosition> = emptyList()
) {
    val marketValue: Double get() = positions.sumOf { it.marketValue }
    val equity: Double get() = cash + marketValue
    val totalReturnPercent: Double
        get() = if (startingCash > 0.0) (equity - startingCash) / startingCash * 100.0 else 0.0
}

data class PaperOrderResult(
    val account: PaperAccount,
    val accepted: Boolean,
    val reason: String
)

object PaperTradingEngine {
    fun openFromPrediction(
        account: PaperAccount,
        prediction: AgentPredictionRecord
    ): PaperOrderResult {
        if (prediction.id in account.positions.map { it.predictionId }) {
            return PaperOrderResult(account, false, "Recommendation already simulated.")
        }
        if (!AgentTradingUniverse.contains(prediction.symbol)) {
            return PaperOrderResult(account, false, "Symbol is outside the Agentic trading universe.")
        }
        if (prediction.decision !in setOf("FAVORABLE", "HIGH_CONVICTION")) {
            return PaperOrderResult(account, false, "Only favorable recommendations open paper positions.")
        }
        if (prediction.marketPrice <= 0.0 || account.equity <= 0.0) {
            return PaperOrderResult(account, false, "A valid paper price and positive equity are required.")
        }

        val maximumNotional = floor(account.equity * PAPER_MAX_ENTRY_PERCENT) / 100.0
        val notional = minOf(maximumNotional, account.cash)
        if (notional <= 0.0) return PaperOrderResult(account, false, "No paper cash is available.")

        val quantity = notional / prediction.marketPrice
        val position = PaperPosition(
            predictionId = prediction.id,
            symbol = prediction.symbol,
            openedAtEpochMillis = System.currentTimeMillis(),
            quantity = quantity,
            entryPrice = prediction.marketPrice,
            lastPrice = prediction.marketPrice,
            decision = prediction.decision,
            confidencePercent = prediction.confidencePercent,
            stopPrice = prediction.proposedStopPrice,
            targetPrice = prediction.proposedTargetPrice
        )
        return PaperOrderResult(
            account = account.copy(
                cash = account.cash - notional,
                positions = account.positions + position
            ),
            accepted = true,
            reason = "Opened a paper position within the 10% limit."
        )
    }
}
