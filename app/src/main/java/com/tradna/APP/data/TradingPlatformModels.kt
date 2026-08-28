package com.tradna.APP.data

enum class TradingPlatformSource {
    ROBINHOOD,
    TRADINGVIEW,
    WEALTHCHARTS,
    GENERIC_CSV
}

enum class NormalizedAssetClass {
    STOCK,
    OPTION,
    FUTURE,
    UNKNOWN
}

enum class NormalizedTradeSide {
    BUY,
    SELL,
    SHORT,
    COVER,
    BUY_TO_OPEN,
    SELL_TO_CLOSE,
    SELL_TO_OPEN,
    BUY_TO_CLOSE,
    EXPIRATION,
    ASSIGNMENT,
    EXERCISE,
    UNKNOWN
}

/*
 * Platform-neutral execution record.
 *
 * Robinhood, TradingView, WealthCharts, and future broker/platform
 * adapters should normalize their imported rows into this model.
 *
 * TraDNA can then route the normalized execution to the correct
 * stock/options/futures reconstruction engine.
 */
data class NormalizedTradeActivity(
    val id: String,

    val source: TradingPlatformSource,

    val accountId: String? = null,

    val assetClass: NormalizedAssetClass,

    /*
     * For stocks/futures this is normally the traded symbol.
     * For options this may be the contract symbol when available.
     */
    val symbol: String,

    /*
     * Used primarily for derivatives.
     * Example: PLTR for a PLTR call option.
     */
    val underlyingSymbol: String? = null,

    val side: NormalizedTradeSide,

    val quantity: Double,

    val price: Double?,

    val activityDate: String,

    val commission: Double = 0.0,
    val fees: Double = 0.0,

    /*
     * OPTIONS
     */
    val optionExpirationDate: String? = null,
    val optionStrikePrice: Double? = null,
    val optionRight: String? = null,
    val optionContractMultiplier: Double? = null,

    /*
     * FUTURES
     */
    val futuresRootSymbol: String? = null,
    val futuresExpirationDate: String? = null,
    val futuresPointValue: Double? = null,
    val futuresTickSize: Double? = null,
    val futuresTickValue: Double? = null,

    /*
     * Platform-specific metadata retained for audit/debugging.
     */
    val orderId: String? = null,
    val status: String? = null,
    val notes: String? = null,
    val rawDescription: String? = null
)

data class PlatformImportResult(
    val source: TradingPlatformSource,

    val rowsDetected: Int,
    val rowsParsed: Int,
    val rowsSkipped: Int,

    val activities: List<NormalizedTradeActivity>,

    val warnings: List<String>
)

/*
 * Standard contract for every external platform adapter.
 */
interface TradingPlatformImporter {

    val source: TradingPlatformSource

    fun canParse(
        csvText: String
    ): Boolean

    fun parse(
        csvText: String
    ): PlatformImportResult
}

/*
 * Lightweight source detector.
 *
 * This does not parse the file itself. It only determines which
 * importer should be attempted first.
 */
object TradingPlatformDetector {

    fun detect(
        csvText: String
    ): TradingPlatformSource {

        val firstLines =
            csvText
                .lineSequence()
                .take(
                    8
                )
                .joinToString(
                    "\n"
                )
                .lowercase()

        /*
         * Robinhood account activity exports commonly contain
         * these column names.
         */
        if (
            firstLines.contains(
                "activity date"
            ) &&
            firstLines.contains(
                "trans code"
            ) &&
            firstLines.contains(
                "instrument"
            )
        ) {

            return TradingPlatformSource.ROBINHOOD
        }

        /*
         * WealthCharts trade exports expose fields such as
         * Symbol, Account, Created Time, QTY, Order ID,
         * Order Type, Filled Price, PnL, and Points.
         */
        if (
            firstLines.contains(
                "created time"
            ) &&
            firstLines.contains(
                "order id"
            ) &&
            firstLines.contains(
                "filled price"
            ) &&
            firstLines.contains(
                "order type"
            )
        ) {

            return TradingPlatformSource.WEALTHCHARTS
        }

        /*
         * TradingView exports vary by the specific Account Manager
         * tab/broker. Common transaction exports use fields such as
         * Symbol, Side, Date, Qty, Price, Value and Commission.
         */
        if (
            firstLines.contains(
                "symbol"
            ) &&
            firstLines.contains(
                "side"
            ) &&
            firstLines.contains(
                "qty"
            ) &&
            firstLines.contains(
                "price"
            )
        ) {

            return TradingPlatformSource.TRADINGVIEW
        }

        return TradingPlatformSource.GENERIC_CSV
    }
}
