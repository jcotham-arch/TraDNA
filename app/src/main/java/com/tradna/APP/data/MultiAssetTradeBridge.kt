package com.tradna.APP.data

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

enum class TraDnaAssetType {
    STOCK,
    OPTION,
    FUTURE
}

enum class MultiAssetTradeDirection {
    LONG,
    SHORT,
    UNKNOWN
}

enum class MultiAssetTradeStatus {
    OPEN,
    PARTIAL,
    CLOSED
}

enum class MultiAssetMarketContext {
    STOCK_SYMBOL,
    OPTION_UNDERLYING,
    FUTURES_CONTRACT
}

enum class MultiAssetAnalysisState {
    READY,
    NEEDS_OPTION_CONTEXT,
    NEEDS_FUTURES_MARKET_DATA,
    NOT_READY
}

data class MultiAssetExecutionSummary(
    val id: String,
    val side: String,
    val quantity: Double,
    val price: Double,
    val executionDate: String,
    val fees: Double = 0.0
)

data class MultiAssetTradeEpisode(
    val id: String,

    val assetType: TraDnaAssetType,

    /*
     * Human-facing instrument symbol.
     *
     * STOCK  -> PLTR
     * OPTION -> full option contract symbol
     * FUTURE -> MESU2026 / provider-specific contract
     */
    val symbol: String,

    /*
     * Symbol TraDNA should eventually request market context for.
     *
     * STOCK  -> stock symbol
     * OPTION -> underlying stock symbol
     * FUTURE -> exact futures contract/provider symbol
     */
    val analysisSymbol: String,

    val marketContext: MultiAssetMarketContext,

    val direction: MultiAssetTradeDirection,

    val status: MultiAssetTradeStatus,

    val openDate: String,

    val closeDate: String?,

    val quantityOpened: Double,

    val quantityRemaining: Double,

    /*
     * STOCK/FUTURE:
     *   actual instrument price.
     *
     * OPTION:
     *   option premium per share, matching the existing option model.
     */
    val averageEntryPrice: Double,

    val averageExitPrice: Double?,

    val realizedPnl: Double,

    val executions: List<MultiAssetExecutionSummary>,

    /*
     * Optional derivative metadata used by future Replay/Lab screens.
     */
    val underlyingSymbol: String? = null,
    val expirationDate: String? = null,
    val strikePrice: Double? = null,
    val optionRight: String? = null,
    val contractMultiplier: Double? = null,

    val futuresRootSymbol: String? = null,
    val futuresPointValue: Double? = null,
    val futuresTickSize: Double? = null,
    val futuresTickValue: Double? = null,

    /*
     * This does not mean "the Agent has trained on it."
     * It describes whether the current TraDNA market-data architecture
     * has enough context to begin the existing style of analysis.
     */
    val analysisState: MultiAssetAnalysisState,

    val analysisNote: String
) {

    val isClosed: Boolean
        get() =
            status ==
                    MultiAssetTradeStatus.CLOSED

    val isDerivative: Boolean
        get() =
            assetType !=
                    TraDnaAssetType.STOCK

    val hasEntryPrice: Boolean
        get() =
            averageEntryPrice >
                    0.0

    val canEnterExistingStockAnalysis: Boolean
        get() =
            assetType ==
                    TraDnaAssetType.STOCK &&
                    hasEntryPrice

    val displayAssetType: String
        get() =
            when (
                assetType
            ) {

                TraDnaAssetType.STOCK ->
                    "STOCK"

                TraDnaAssetType.OPTION ->
                    "OPTION"

                TraDnaAssetType.FUTURE ->
                    "FUTURE"
            }
}

object MultiAssetTradeBridge {

    fun combine(
        legacyStockTrades: List<TradeEpisode>,
        normalizedStockTrades: List<NormalizedStockTradeEpisode>,
        optionTrades: List<OptionTradeEpisode>,
        futuresTrades: List<FutureTradeEpisode>
    ): List<MultiAssetTradeEpisode> {

        return buildList {

            legacyStockTrades.forEach {
                    trade ->

                add(
                    fromLegacyStock(
                        trade
                    )
                )
            }

            normalizedStockTrades.forEach {
                    trade ->

                add(
                    fromNormalizedStock(
                        trade
                    )
                )
            }

            optionTrades.forEach {
                    trade ->

                add(
                    fromOption(
                        trade
                    )
                )
            }

            futuresTrades.forEach {
                    trade ->

                add(
                    fromFuture(
                        trade
                    )
                )
            }
        }
            .distinctBy {
                buildString {

                    append(
                        it.assetType.name
                    )

                    append(
                        "|"
                    )

                    append(
                        it.id
                    )
                }
            }
            .sortedByDescending {
                parseSortableDate(
                    it.openDate
                )
            }
    }

    fun fromLegacyStock(
        trade: TradeEpisode
    ): MultiAssetTradeEpisode {

        val executions =
            trade.executions.mapIndexed {
                    index,
                    execution ->

                /*
                 * The legacy Robinhood StockExecution model does not
                 * expose its own execution id. Build a deterministic
                 * bridge id from the parent trade id and execution order.
                 */
                MultiAssetExecutionSummary(
                    id =
                        "${trade.id}|LEGACY_STOCK_EXECUTION|$index",

                    side =
                        execution.side,

                    quantity =
                        execution.quantity,

                    price =
                        execution.statedPrice,

                    executionDate =
                        execution.activityDate,

                    fees =
                        0.0
                )
            }

        val openingExecutions =
            executions.filter {
                it.side.equals(
                    "BUY",
                    ignoreCase =
                        true
                ) ||
                        it.side.equals(
                            "SELL",
                            ignoreCase =
                                true
                        )
            }

        val direction =
            when (
                openingExecutions
                    .firstOrNull()
                    ?.side
                    ?.trim()
                    ?.uppercase(
                        Locale.US
                    )
            ) {

                "BUY" ->
                    MultiAssetTradeDirection.LONG

                "SELL" ->
                    MultiAssetTradeDirection.SHORT

                else ->
                    MultiAssetTradeDirection.UNKNOWN
            }

        val quantityOpened =
            openingExecutions
                .firstOrNull()
                ?.quantity
                ?: 0.0

        return MultiAssetTradeEpisode(
            id =
                trade.id,

            assetType =
                TraDnaAssetType.STOCK,

            symbol =
                trade.symbol,

            analysisSymbol =
                trade.symbol,

            marketContext =
                MultiAssetMarketContext.STOCK_SYMBOL,

            direction =
                direction,

            status =
                mapStatusName(
                    trade.status.name
                ),

            openDate =
                trade.openDate,

            closeDate =
                trade.closeDate,

            quantityOpened =
                quantityOpened,

            quantityRemaining =
                if (
                    trade.status.name.equals(
                        "CLOSED",
                        ignoreCase =
                            true
                    )
                ) {
                    0.0
                } else {
                    quantityOpened
                },

            averageEntryPrice =
                trade.averageEntryPrice,

            averageExitPrice =
                trade.averageExitPrice,

            realizedPnl =
                trade.realizedPnl,

            executions =
                executions,

            analysisState =
                if (
                    trade.symbol.isNotBlank() &&
                    trade.averageEntryPrice >
                    0.0
                ) {
                    MultiAssetAnalysisState.READY
                } else {
                    MultiAssetAnalysisState.NOT_READY
                },

            analysisNote =
                if (
                    trade.symbol.isNotBlank() &&
                    trade.averageEntryPrice >
                    0.0
                ) {
                    "Ready for the existing stock Replay and Agent Lab market-context pipeline."
                } else {
                    "Stock trade is missing the symbol or entry price required for analysis."
                }
        )
    }

    fun fromNormalizedStock(
        trade: NormalizedStockTradeEpisode
    ): MultiAssetTradeEpisode {

        return MultiAssetTradeEpisode(
            id =
                trade.id,

            assetType =
                TraDnaAssetType.STOCK,

            symbol =
                trade.symbol,

            analysisSymbol =
                trade.symbol,

            marketContext =
                MultiAssetMarketContext.STOCK_SYMBOL,

            direction =
                when (
                    trade.direction
                ) {

                    NormalizedStockPositionDirection.LONG ->
                        MultiAssetTradeDirection.LONG

                    NormalizedStockPositionDirection.SHORT ->
                        MultiAssetTradeDirection.SHORT
                },

            status =
                mapStatusName(
                    trade.status.name
                ),

            openDate =
                trade.openDate,

            closeDate =
                trade.closeDate,

            quantityOpened =
                trade.totalSharesOpened,

            quantityRemaining =
                trade.remainingShares,

            averageEntryPrice =
                trade.averageEntryPrice,

            averageExitPrice =
                trade.averageExitPrice,

            realizedPnl =
                trade.realizedPnl,

            executions =
                trade.executions.map {
                        execution ->

                    MultiAssetExecutionSummary(
                        id =
                            execution.id,

                        side =
                            execution.side.name,

                        quantity =
                            execution.shares,

                        price =
                            execution.price,

                        executionDate =
                            execution.activityDate,

                        fees =
                            execution.totalCosts
                    )
                },

            analysisState =
                if (
                    trade.symbol.isNotBlank() &&
                    trade.averageEntryPrice >
                    0.0
                ) {
                    MultiAssetAnalysisState.READY
                } else {
                    MultiAssetAnalysisState.NOT_READY
                },

            analysisNote =
                if (
                    trade.symbol.isNotBlank() &&
                    trade.averageEntryPrice >
                    0.0
                ) {
                    "Ready for stock market-context analysis."
                } else {
                    "Normalized stock trade is missing the symbol or entry price required for analysis."
                }
        )
    }

    fun fromOption(
        trade: OptionTradeEpisode
    ): MultiAssetTradeEpisode {

        val openingContracts =
            trade.executions
                .filter {
                    it.transactionType ==
                            OptionTransactionType.BUY_TO_OPEN ||
                            it.transactionType ==
                            OptionTransactionType.SELL_TO_OPEN
                }
                .sumOf {
                    it.contracts
                }

        return MultiAssetTradeEpisode(
            id =
                trade.id,

            assetType =
                TraDnaAssetType.OPTION,

            symbol =
                trade.symbol,

            /*
             * Options should be analyzed against the underlying
             * equity market context until a dedicated options market
             * data provider / IV history pipeline is connected.
             */
            analysisSymbol =
                trade.underlyingSymbol,

            marketContext =
                MultiAssetMarketContext.OPTION_UNDERLYING,

            direction =
                if (
                    trade.isLongPremiumTrade
                ) {
                    MultiAssetTradeDirection.LONG
                } else {
                    MultiAssetTradeDirection.SHORT
                },

            status =
                mapStatusName(
                    trade.status.name
                ),

            openDate =
                trade.openDate,

            closeDate =
                trade.closeDate,

            quantityOpened =
                openingContracts,

            quantityRemaining =
                abs(
                    trade.netContracts
                ),

            averageEntryPrice =
                trade.averageEntryPremium,

            averageExitPrice =
                trade.averageExitPremium,

            realizedPnl =
                trade.realizedPnl,

            executions =
                trade.executions.map {
                        execution ->

                    MultiAssetExecutionSummary(
                        id =
                            execution.id,

                        side =
                            execution.transactionType.name,

                        quantity =
                            execution.contracts,

                        price =
                            execution.premium,

                        executionDate =
                            execution.executionDate,

                        fees =
                            execution.fees
                    )
                },

            underlyingSymbol =
                trade.underlyingSymbol,

            expirationDate =
                trade.expirationDate,

            strikePrice =
                trade.strikePrice,

            optionRight =
                trade.right.name,

            contractMultiplier =
                trade.contractMultiplier,

            analysisState =
                if (
                    trade.underlyingSymbol.isNotBlank() &&
                    trade.averageEntryPremium >
                    0.0
                ) {
                    MultiAssetAnalysisState.NEEDS_OPTION_CONTEXT
                } else {
                    MultiAssetAnalysisState.NOT_READY
                },

            analysisNote =
                if (
                    trade.underlyingSymbol.isNotBlank() &&
                    trade.averageEntryPremium >
                    0.0
                ) {
                    "Trade is visible to multi-asset Replay/Lab. Underlying stock context can be reconstructed next; dedicated option-price/IV history is not connected yet."
                } else {
                    "Option trade is missing the underlying symbol or entry premium required for analysis."
                }
        )
    }

    fun fromFuture(
        trade: FutureTradeEpisode
    ): MultiAssetTradeEpisode {

        return MultiAssetTradeEpisode(
            id =
                trade.id,

            assetType =
                TraDnaAssetType.FUTURE,

            symbol =
                trade.symbol,

            analysisSymbol =
                trade.symbol,

            marketContext =
                MultiAssetMarketContext.FUTURES_CONTRACT,

            direction =
                when (
                    trade.direction
                ) {

                    FuturePositionDirection.LONG ->
                        MultiAssetTradeDirection.LONG

                    FuturePositionDirection.SHORT ->
                        MultiAssetTradeDirection.SHORT
                },

            status =
                mapStatusName(
                    trade.status.name
                ),

            openDate =
                trade.openDate,

            closeDate =
                trade.closeDate,

            quantityOpened =
                trade.totalContractsOpened,

            quantityRemaining =
                trade.remainingContracts,

            averageEntryPrice =
                trade.averageEntryPrice,

            averageExitPrice =
                trade.averageExitPrice,

            realizedPnl =
                trade.realizedPnl,

            executions =
                trade.executions.map {
                        execution ->

                    MultiAssetExecutionSummary(
                        id =
                            execution.id,

                        side =
                            execution.side.name,

                        quantity =
                            execution.contracts,

                        price =
                            execution.price,

                        executionDate =
                            execution.executionDate,

                        fees =
                            execution.totalCosts
                    )
                },

            expirationDate =
                trade.expirationDate,

            futuresRootSymbol =
                trade.rootSymbol,

            futuresPointValue =
                trade.pointValue,

            futuresTickSize =
                trade.tickSize,

            futuresTickValue =
                trade.tickValue,

            analysisState =
                if (
                    trade.symbol.isNotBlank() &&
                    trade.averageEntryPrice >
                    0.0
                ) {
                    MultiAssetAnalysisState.NEEDS_FUTURES_MARKET_DATA
                } else {
                    MultiAssetAnalysisState.NOT_READY
                },

            analysisNote =
                if (
                    trade.symbol.isNotBlank() &&
                    trade.averageEntryPrice >
                    0.0
                ) {
                    "Trade is visible to multi-asset Replay/Lab. A futures-capable historical market-data provider is required before candle reconstruction and Agent training."
                } else {
                    "Futures trade is missing the contract symbol or entry price required for analysis."
                }
        )
    }

    private fun mapStatusName(
        status: String
    ): MultiAssetTradeStatus {

        return when (
            status
                .trim()
                .uppercase(
                    Locale.US
                )
        ) {

            "CLOSED" ->
                MultiAssetTradeStatus.CLOSED

            "PARTIAL" ->
                MultiAssetTradeStatus.PARTIAL

            else ->
                MultiAssetTradeStatus.OPEN
        }
    }

    private fun parseSortableDate(
        value: String
    ): Long {

        val formats =
            listOf(
                "M/d/yyyy",
                "MM/dd/yyyy",
                "yyyy-MM-dd",
                "M/d/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX"
            )

        formats.forEach {
                pattern ->

            try {

                val parser =
                    SimpleDateFormat(
                        pattern,
                        Locale.US
                    )

                parser.isLenient =
                    false

                return parser
                    .parse(
                        value
                    )
                    ?.time
                    ?: Long.MIN_VALUE

            } catch (
                _: Exception
            ) {
                // Try the next supported format.
            }
        }

        return Long.MIN_VALUE
    }
}
