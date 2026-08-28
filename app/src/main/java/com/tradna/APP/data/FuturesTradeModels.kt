package com.tradna.APP.data

import com.tradna.APP.market.FutureInstrument
import kotlin.math.abs

enum class FutureTradeStatus {
    OPEN,
    PARTIAL,
    CLOSED
}

enum class FuturePositionDirection {
    LONG,
    SHORT
}

enum class FutureExecutionSide {
    BUY,
    SELL
}

data class FutureExecution(
    val id: String,
    val contract: FutureInstrument,
    val side: FutureExecutionSide,
    val contracts: Double,
    val price: Double,
    val executionDate: String,
    val commission: Double = 0.0,
    val fees: Double = 0.0,
    val orderId: String? = null,
    val source: TradingPlatformSource? = null
) {

    val totalCosts: Double
        get() =
            commission +
                    fees

    val signedContracts: Double
        get() =
            when (
                side
            ) {

                FutureExecutionSide.BUY ->
                    contracts

                FutureExecutionSide.SELL ->
                    -contracts
            }
}

data class FutureTradeEpisode(
    val id: String,

    val contract: FutureInstrument,

    val direction: FuturePositionDirection,

    val status: FutureTradeStatus,

    val openDate: String,

    val closeDate: String?,

    /*
     * Remaining open contracts.
     * Closed episodes should normally be 0.
     */
    val netContracts: Double,

    val totalContractsOpened: Double,

    val totalContractsClosed: Double,

    val averageEntryPrice: Double,

    val averageExitPrice: Double?,

    val realizedPnl: Double,

    val totalCommission: Double,

    val totalFees: Double,

    val executions: List<FutureExecution>,

    /*
     * Optional enrichment fields for future Replay / Agent analysis.
     */
    val sessionHighAfterEntry: Double? = null,
    val sessionLowAfterEntry: Double? = null,
    val maximumFavorableExcursion: Double? = null,
    val maximumAdverseExcursion: Double? = null
) {

    val symbol: String
        get() =
            contract.symbol

    val rootSymbol: String
        get() =
            contract.rootSymbol

    val expirationDate: String
        get() =
            contract.expirationDate

    val pointValue: Double
        get() =
            contract.pointValue

    val tickSize: Double
        get() =
            contract.tickSize

    val tickValue: Double
        get() =
            contract.tickValue

    val isClosed: Boolean
        get() =
            status ==
                    FutureTradeStatus.CLOSED

    val isOpen: Boolean
        get() =
            status ==
                    FutureTradeStatus.OPEN ||
                    status ==
                    FutureTradeStatus.PARTIAL

    val grossPoints: Double?
        get() {

            val exit =
                averageExitPrice
                    ?: return null

            return when (
                direction
            ) {

                FuturePositionDirection.LONG ->
                    exit -
                            averageEntryPrice

                FuturePositionDirection.SHORT ->
                    averageEntryPrice -
                            exit
            }
        }

    val grossDollarPnl: Double?
        get() {

            val points =
                grossPoints
                    ?: return null

            if (
                totalContractsClosed <=
                0.0
            ) {
                return null
            }

            return points *
                    contract.pointValue *
                    totalContractsClosed
        }

    val netDollarPnl: Double
        get() =
            realizedPnl

    val returnPerContract: Double?
        get() {

            val points =
                grossPoints
                    ?: return null

            return points *
                    contract.pointValue
        }

    val ticksMoved: Double?
        get() {

            val points =
                grossPoints
                    ?: return null

            if (
                contract.tickSize <=
                0.0
            ) {
                return null
            }

            return points /
                    contract.tickSize
        }

    val averagePnlPerClosedContract: Double?
        get() {

            if (
                totalContractsClosed <=
                0.0
            ) {
                return null
            }

            return realizedPnl /
                    totalContractsClosed
        }

    val remainingContracts: Double
        get() =
            abs(
                netContracts
            )
}
