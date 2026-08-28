package com.tradna.APP.market

enum class InstrumentType {
    STOCK,
    OPTION,
    FUTURE
}

enum class OptionRight {
    CALL,
    PUT
}

enum class DerivativePositionSide {
    LONG,
    SHORT
}

/*
 * Common identity shared by every instrument TraDNA can analyze.
 *
 * symbol:
 *   STOCK   -> PLTR
 *   OPTION  -> broker/provider symbol or OCC-style contract symbol
 *   FUTURE  -> provider-specific contract symbol such as an individual expiry
 *
 * displaySymbol:
 *   Human-readable label used in the UI.
 */
sealed interface MarketInstrument {
    val instrumentType: InstrumentType
    val symbol: String
    val displaySymbol: String
}

data class StockInstrument(
    override val symbol: String,
    override val displaySymbol: String = symbol
) : MarketInstrument {

    override val instrumentType: InstrumentType =
        InstrumentType.STOCK
}

data class OptionInstrument(
    override val symbol: String,

    val underlyingSymbol: String,
    val expirationDate: String,
    val strikePrice: Double,
    val right: OptionRight,

    /*
     * US equity options are commonly 100 shares per standard contract,
     * but TraDNA keeps this configurable rather than hard-coding it
     * into P&L calculations.
     */
    val contractMultiplier: Double = 100.0,

    override val displaySymbol: String =
        buildString {
            append(underlyingSymbol)
            append(" ")
            append(expirationDate)
            append(" ")
            append(
                String.format(
                    java.util.Locale.US,
                    "%.2f",
                    strikePrice
                )
            )
            append(" ")
            append(right.name)
        }
) : MarketInstrument {

    override val instrumentType: InstrumentType =
        InstrumentType.OPTION
}

data class FutureInstrument(
    override val symbol: String,

    val rootSymbol: String,
    val expirationDate: String,

    /*
     * Value of one full index/commodity point per contract.
     * Keep provider/contract specs external so ES, MES, CL, GC, etc.
     * can all be represented correctly.
     */
    val pointValue: Double,

    /*
     * Smallest permitted price movement.
     */
    val tickSize: Double,

    /*
     * Dollar value of one minimum tick.
     */
    val tickValue: Double,

    val exchange: String? = null,

    override val displaySymbol: String =
        "$rootSymbol $expirationDate"
) : MarketInstrument {

    override val instrumentType: InstrumentType =
        InstrumentType.FUTURE
}

/*
 * Generic execution record for the future multi-asset trade engine.
 *
 * We are deliberately keeping this separate from the existing
 * Robinhood StockExecution model so the current working stock app
 * remains stable while derivatives support is added.
 */
data class InstrumentExecution(
    val id: String,
    val instrument: MarketInstrument,
    val side: DerivativePositionSide,
    val quantity: Double,
    val price: Double,
    val executionDate: String,
    val fees: Double = 0.0
)

/*
 * Optional option-specific market state.
 *
 * These fields are nullable because a broker export may not contain
 * Greeks/IV, and the market-data provider may populate them later.
 */
data class OptionMarketSnapshot(
    val contract: OptionInstrument,
    val optionPrice: Double,
    val underlyingPrice: Double? = null,
    val impliedVolatility: Double? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val theta: Double? = null,
    val vega: Double? = null,
    val rho: Double? = null,
    val openInterest: Long? = null,
    val volume: Long? = null,
    val bid: Double? = null,
    val ask: Double? = null
)

/*
 * Futures-specific market state.
 */
data class FutureMarketSnapshot(
    val contract: FutureInstrument,
    val price: Double,
    val sessionHigh: Double? = null,
    val sessionLow: Double? = null,
    val volume: Long? = null,
    val openInterest: Long? = null
)
