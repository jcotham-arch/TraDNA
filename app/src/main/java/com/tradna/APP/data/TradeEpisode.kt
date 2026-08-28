package com.tradna.APP.data

data class StockExecution(
    val activityDate: String,
    val symbol: String,
    val side: String,
    val quantity: Double,
    val statedPrice: Double,
    val actualCash: Double,
    val source: RobinhoodActivity
)

enum class TradeStatus {
    OPEN,
    PARTIAL,
    CLOSED
}

data class TradeEpisode(
    val id: String,
    val symbol: String,
    val sequenceNumber: Int,

    val openDate: String,
    val closeDate: String?,

    val totalSharesBought: Double,
    val totalSharesSold: Double,
    val remainingShares: Double,

    val totalBuyCost: Double,
    val totalSellProceeds: Double,

    val averageEntryPrice: Double,
    val averageExitPrice: Double?,

    val realizedPnl: Double,

    val status: TradeStatus,

    val executions: List<StockExecution>
)

