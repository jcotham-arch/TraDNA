package com.tradna.APP.data

import com.tradna.APP.market.OptionInstrument
import com.tradna.APP.market.OptionRight

enum class OptionTradeStatus {
    OPEN,
    PARTIAL,
    CLOSED
}

enum class OptionTransactionType {
    BUY_TO_OPEN,
    BUY_TO_CLOSE,
    SELL_TO_OPEN,
    SELL_TO_CLOSE,
    ASSIGNMENT,
    EXERCISE,
    EXPIRATION,
    UNKNOWN
}

data class OptionExecution(
    val id: String,
    val contract: OptionInstrument,
    val transactionType: OptionTransactionType,
    val contracts: Double,
    val premium: Double,
    val executionDate: String,
    val fees: Double = 0.0
) {

    val grossDollarValue: Double
        get() =
            contracts *
                    premium *
                    contract.contractMultiplier

    val signedCashFlow: Double
        get() {

            val gross =
                grossDollarValue

            return when (
                transactionType
            ) {

                OptionTransactionType.BUY_TO_OPEN,
                OptionTransactionType.BUY_TO_CLOSE,
                OptionTransactionType.EXERCISE ->
                    -gross -
                            fees

                OptionTransactionType.SELL_TO_OPEN,
                OptionTransactionType.SELL_TO_CLOSE,
                OptionTransactionType.ASSIGNMENT,
                OptionTransactionType.EXPIRATION ->
                    gross -
                            fees

                OptionTransactionType.UNKNOWN ->
                    -fees
            }
        }
}

data class OptionTradeEpisode(
    val id: String,
    val contract: OptionInstrument,
    val status: OptionTradeStatus,
    val openDate: String,
    val closeDate: String?,
    val netContracts: Double,
    val averageEntryPremium: Double,
    val averageExitPremium: Double?,
    val realizedPnl: Double,
    val executions: List<OptionExecution>,
    val underlyingPriceAtEntry: Double? = null,
    val underlyingPriceAtExit: Double? = null,
    val impliedVolatilityAtEntry: Double? = null,
    val deltaAtEntry: Double? = null,
    val gammaAtEntry: Double? = null,
    val thetaAtEntry: Double? = null,
    val vegaAtEntry: Double? = null
) {

    val symbol: String
        get() =
            contract.symbol

    val underlyingSymbol: String
        get() =
            contract.underlyingSymbol

    val right: OptionRight
        get() =
            contract.right

    val strikePrice: Double
        get() =
            contract.strikePrice

    val expirationDate: String
        get() =
            contract.expirationDate

    val contractMultiplier: Double
        get() =
            contract.contractMultiplier

    val isClosed: Boolean
        get() =
            status ==
                    OptionTradeStatus.CLOSED

    val isLongPremiumTrade: Boolean
        get() {

            val firstOpeningExecution =
                executions.firstOrNull {
                    it.transactionType ==
                            OptionTransactionType.BUY_TO_OPEN ||
                            it.transactionType ==
                            OptionTransactionType.SELL_TO_OPEN
                }
                    ?: return true

            return firstOpeningExecution.transactionType ==
                    OptionTransactionType.BUY_TO_OPEN
        }

    val returnPercent: Double?
        get() {

            val exit =
                averageExitPremium
                    ?: return null

            if (
                averageEntryPremium <=
                0.0
            ) {
                return null
            }

            val raw =
                (
                        exit -
                                averageEntryPremium
                        ) /
                        averageEntryPremium *
                        100.0

            return if (
                isLongPremiumTrade
            ) {
                raw
            } else {
                -raw
            }
        }
}
