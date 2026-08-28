package com.tradna.APP.data

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

enum class NormalizedStockTradeStatus {
    OPEN,
    PARTIAL,
    CLOSED
}

enum class NormalizedStockPositionDirection {
    LONG,
    SHORT
}

enum class NormalizedStockExecutionSide {
    BUY,
    SELL
}

data class NormalizedStockExecution(
    val id: String,
    val source: TradingPlatformSource,
    val accountId: String?,
    val symbol: String,
    val side: NormalizedStockExecutionSide,
    val shares: Double,
    val price: Double,
    val activityDate: String,
    val commission: Double = 0.0,
    val fees: Double = 0.0,
    val orderId: String? = null
) {

    val totalCosts: Double
        get() =
            commission +
                    fees
}

data class NormalizedStockTradeEpisode(
    val id: String,
    val source: TradingPlatformSource,
    val accountId: String?,
    val symbol: String,
    val direction: NormalizedStockPositionDirection,
    val status: NormalizedStockTradeStatus,
    val openDate: String,
    val closeDate: String?,
    val netShares: Double,
    val totalSharesOpened: Double,
    val totalSharesClosed: Double,
    val averageEntryPrice: Double,
    val averageExitPrice: Double?,
    val realizedPnl: Double,
    val totalCommission: Double,
    val totalFees: Double,
    val executions: List<NormalizedStockExecution>
) {

    val isClosed: Boolean
        get() =
            status ==
                    NormalizedStockTradeStatus.CLOSED

    val remainingShares: Double
        get() =
            abs(
                netShares
            )
}

object NormalizedStockTradeReconstructor {

    fun reconstruct(
        activities: List<NormalizedTradeActivity>
    ): List<NormalizedStockTradeEpisode> {

        val stockActivities =
            activities
                .filter {
                    it.assetClass ==
                            NormalizedAssetClass.STOCK &&
                            it.symbol.isNotBlank() &&
                            it.quantity > 0.0 &&
                            it.price != null
                }

        if (
            stockActivities.isEmpty()
        ) {
            return emptyList()
        }

        val grouped =
            stockActivities
                .groupBy {
                    listOf(
                        it.source.name,
                        it.accountId
                            .orEmpty()
                            .trim()
                            .uppercase(
                                Locale.US
                            ),
                        it.symbol
                            .trim()
                            .uppercase(
                                Locale.US
                            )
                    )
                        .joinToString(
                            "|"
                        )
                }

        val episodes =
            mutableListOf<NormalizedStockTradeEpisode>()

        grouped.forEach {
                (_, symbolActivities) ->

            val sorted =
                symbolActivities
                    .sortedBy {
                        parseSortableDate(
                            it.activityDate
                        )
                    }

            if (
                sorted.isEmpty()
            ) {
                return@forEach
            }

            var executions =
                mutableListOf<NormalizedStockExecution>()

            var direction =
                null as NormalizedStockPositionDirection?

            var signedPosition =
                0.0

            /*
             * Current open basis is used for realized-P&L calculations.
             * It is reduced as shares are closed, which keeps scale-out /
             * scale-back-in behavior more accurate than using one lifetime
             * average for the entire episode.
             */
            var openCostBasis =
                0.0

            var totalOpened =
                0.0

            var totalClosed =
                0.0

            var entryNotional =
                0.0

            var exitNotional =
                0.0

            var realizedPnl =
                0.0

            var totalCommission =
                0.0

            var totalFees =
                0.0

            var openDate =
                ""

            var hadPartialClose =
                false

            fun absolutePosition(): Double =
                abs(
                    signedPosition
                )

            fun currentAverageOpenPrice(): Double {

                val openShares =
                    absolutePosition()

                return if (
                    openShares >
                    0.000001
                ) {

                    openCostBasis /
                            openShares

                } else {
                    0.0
                }
            }

            fun resetState() {

                executions =
                    mutableListOf()

                direction =
                    null

                signedPosition =
                    0.0

                openCostBasis =
                    0.0

                totalOpened =
                    0.0

                totalClosed =
                    0.0

                entryNotional =
                    0.0

                exitNotional =
                    0.0

                realizedPnl =
                    0.0

                totalCommission =
                    0.0

                totalFees =
                    0.0

                openDate =
                    ""

                hadPartialClose =
                    false
            }

            fun buildEpisode(
                closeDate: String?,
                status: NormalizedStockTradeStatus
            ) {

                val episodeDirection =
                    direction
                        ?: return

                if (
                    executions.isEmpty()
                ) {
                    return
                }

                val first =
                    executions.first()

                val averageEntry =
                    if (
                        totalOpened >
                        0.0
                    ) {

                        entryNotional /
                                totalOpened

                    } else {
                        0.0
                    }

                val averageExit =
                    if (
                        totalClosed >
                        0.0
                    ) {

                        exitNotional /
                                totalClosed

                    } else {
                        null
                    }

                val id =
                    UUID.nameUUIDFromBytes(
                        listOf(
                            first.source.name,
                            first.accountId.orEmpty(),
                            first.symbol,
                            openDate,
                            first.id
                        )
                            .joinToString(
                                "|"
                            )
                            .toByteArray(
                                StandardCharsets.UTF_8
                            )
                    )
                        .toString()

                episodes.add(
                    NormalizedStockTradeEpisode(
                        id =
                            id,

                        source =
                            first.source,

                        accountId =
                            first.accountId,

                        symbol =
                            first.symbol,

                        direction =
                            episodeDirection,

                        status =
                            status,

                        openDate =
                            openDate
                                .ifBlank {
                                    first.activityDate
                                },

                        closeDate =
                            closeDate,

                        netShares =
                            signedPosition,

                        totalSharesOpened =
                            totalOpened,

                        totalSharesClosed =
                            totalClosed,

                        averageEntryPrice =
                            averageEntry,

                        averageExitPrice =
                            averageExit,

                        realizedPnl =
                            realizedPnl,

                        totalCommission =
                            totalCommission,

                        totalFees =
                            totalFees,

                        executions =
                            executions
                                .toList()
                    )
                )
            }

            sorted.forEach activityLoop@{
                    activity ->

                val price =
                    activity.price
                        ?: return@activityLoop

                val side =
                    executionSide(
                        activity.side
                    )
                        ?: return@activityLoop

                /*
                 * A COVER row with no known short position is not enough
                 * evidence to create a new long position. Skip it rather
                 * than inventing history.
                 */
                if (
                    executions.isEmpty() &&
                    activity.side ==
                    NormalizedTradeSide.COVER
                ) {
                    return@activityLoop
                }

                val execution =
                    NormalizedStockExecution(
                        id =
                            activity.id,

                        source =
                            activity.source,

                        accountId =
                            activity.accountId,

                        symbol =
                            activity.symbol
                                .trim()
                                .uppercase(
                                    Locale.US
                                ),

                        side =
                            side,

                        shares =
                            activity.quantity,

                        price =
                            price,

                        activityDate =
                            activity.activityDate,

                        commission =
                            activity.commission,

                        fees =
                            activity.fees,

                        orderId =
                            activity.orderId
                    )

                if (
                    executions.isEmpty()
                ) {

                    openDate =
                        activity.activityDate

                    direction =
                        openingDirection(
                            activity.side,
                            side
                        )
                }

                val currentDirection =
                    direction
                        ?: return@activityLoop

                val opensSameDirection =
                    when (
                        currentDirection
                    ) {

                        NormalizedStockPositionDirection.LONG ->
                            side ==
                                    NormalizedStockExecutionSide.BUY

                        NormalizedStockPositionDirection.SHORT ->
                            side ==
                                    NormalizedStockExecutionSide.SELL
                    }

                executions.add(
                    execution
                )

                totalCommission +=
                    activity.commission

                totalFees +=
                    activity.fees

                if (
                    opensSameDirection
                ) {

                    val quantity =
                        activity.quantity

                    signedPosition +=
                        if (
                            currentDirection ==
                            NormalizedStockPositionDirection.LONG
                        ) {
                            quantity
                        } else {
                            -quantity
                        }

                    openCostBasis +=
                        quantity *
                                price

                    totalOpened +=
                        quantity

                    entryNotional +=
                        quantity *
                                price

                    realizedPnl -=
                        activity.commission +
                                activity.fees

                    return@activityLoop
                }

                val positionBeforeClose =
                    absolutePosition()

                if (
                    positionBeforeClose <=
                    0.000001
                ) {
                    return@activityLoop
                }

                val closeShares =
                    minOf(
                        activity.quantity,
                        positionBeforeClose
                    )

                val averageOpenPrice =
                    currentAverageOpenPrice()

                val tradePnl =
                    when (
                        currentDirection
                    ) {

                        NormalizedStockPositionDirection.LONG ->
                            (
                                    price -
                                            averageOpenPrice
                                    ) *
                                    closeShares

                        NormalizedStockPositionDirection.SHORT ->
                            (
                                    averageOpenPrice -
                                            price
                                    ) *
                                    closeShares
                    }

                realizedPnl +=
                    tradePnl

                realizedPnl -=
                    activity.commission +
                            activity.fees

                totalClosed +=
                    closeShares

                exitNotional +=
                    closeShares *
                            price

                openCostBasis -=
                    averageOpenPrice *
                            closeShares

                if (
                    openCostBasis <
                    0.000001
                ) {
                    openCostBasis =
                        0.0
                }

                signedPosition =
                    when (
                        currentDirection
                    ) {

                        NormalizedStockPositionDirection.LONG ->
                            signedPosition -
                                    closeShares

                        NormalizedStockPositionDirection.SHORT ->
                            signedPosition +
                                    closeShares
                    }

                val remaining =
                    absolutePosition()

                if (
                    remaining >
                    0.000001
                ) {
                    hadPartialClose =
                        true
                }

                val excessShares =
                    activity.quantity -
                            closeShares

                val episodeClosed =
                    remaining <
                            0.000001

                if (
                    episodeClosed
                ) {

                    buildEpisode(
                        closeDate =
                            activity.activityDate,

                        status =
                            NormalizedStockTradeStatus.CLOSED
                    )

                    resetState()

                    /*
                     * A single execution can reverse a position:
                     * long 10 -> sell 15 becomes a closed long 10
                     * plus a fresh short 5.
                     */
                    if (
                        excessShares >
                        0.000001
                    ) {

                        openDate =
                            activity.activityDate

                        direction =
                            if (
                                side ==
                                NormalizedStockExecutionSide.BUY
                            ) {
                                NormalizedStockPositionDirection.LONG
                            } else {
                                NormalizedStockPositionDirection.SHORT
                            }

                        val excessExecution =
                            execution.copy(
                                id =
                                    UUID.nameUUIDFromBytes(
                                        (
                                                execution.id +
                                                        "|EXCESS"
                                                )
                                            .toByteArray(
                                                StandardCharsets.UTF_8
                                            )
                                    )
                                        .toString(),

                                shares =
                                    excessShares,

                                commission =
                                    0.0,

                                fees =
                                    0.0
                            )

                        executions.add(
                            excessExecution
                        )

                        signedPosition =
                            if (
                                direction ==
                                NormalizedStockPositionDirection.LONG
                            ) {
                                excessShares
                            } else {
                                -excessShares
                            }

                        openCostBasis =
                            excessShares *
                                    price

                        totalOpened =
                            excessShares

                        entryNotional =
                            excessShares *
                                    price
                    }
                }
            }

            if (
                executions.isNotEmpty() &&
                direction != null
            ) {

                buildEpisode(
                    closeDate =
                        null,

                    status =
                        if (
                            hadPartialClose ||
                            totalClosed >
                            0.0
                        ) {
                            NormalizedStockTradeStatus.PARTIAL
                        } else {
                            NormalizedStockTradeStatus.OPEN
                        }
                )
            }
        }

        return episodes
            .sortedByDescending {
                parseSortableDate(
                    it.openDate
                )
            }
    }

    private fun openingDirection(
        originalSide: NormalizedTradeSide,
        executionSide: NormalizedStockExecutionSide
    ): NormalizedStockPositionDirection {

        return when (
            originalSide
        ) {

            NormalizedTradeSide.SHORT,
            NormalizedTradeSide.SELL_TO_OPEN ->
                NormalizedStockPositionDirection.SHORT

            NormalizedTradeSide.BUY,
            NormalizedTradeSide.BUY_TO_OPEN ->
                NormalizedStockPositionDirection.LONG

            else ->
                if (
                    executionSide ==
                    NormalizedStockExecutionSide.BUY
                ) {
                    NormalizedStockPositionDirection.LONG
                } else {
                    NormalizedStockPositionDirection.SHORT
                }
        }
    }

    private fun executionSide(
        side: NormalizedTradeSide
    ): NormalizedStockExecutionSide? {

        return when (
            side
        ) {

            NormalizedTradeSide.BUY,
            NormalizedTradeSide.COVER,
            NormalizedTradeSide.BUY_TO_OPEN,
            NormalizedTradeSide.BUY_TO_CLOSE ->
                NormalizedStockExecutionSide.BUY

            NormalizedTradeSide.SELL,
            NormalizedTradeSide.SHORT,
            NormalizedTradeSide.SELL_TO_OPEN,
            NormalizedTradeSide.SELL_TO_CLOSE ->
                NormalizedStockExecutionSide.SELL

            NormalizedTradeSide.EXPIRATION,
            NormalizedTradeSide.ASSIGNMENT,
            NormalizedTradeSide.EXERCISE,
            NormalizedTradeSide.UNKNOWN ->
                null
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
