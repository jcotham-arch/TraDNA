package com.tradna.APP.data

import com.tradna.APP.market.FutureInstrument
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

object FuturesTradeReconstructor {

    fun reconstruct(
        activities: List<NormalizedTradeActivity>
    ): List<FutureTradeEpisode> {

        val futureActivities =
            activities
                .filter {
                    it.assetClass ==
                            NormalizedAssetClass.FUTURE &&
                            it.symbol.isNotBlank() &&
                            it.quantity > 0.0 &&
                            it.price != null
                }

        if (
            futureActivities.isEmpty()
        ) {
            return emptyList()
        }

        val grouped =
            futureActivities
                .groupBy {
                    buildContractKey(
                        it
                    )
                }

        val episodes =
            mutableListOf<FutureTradeEpisode>()

        grouped.forEach {
                (_, contractActivities) ->

            val sorted =
                contractActivities
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

            var currentExecutions =
                mutableListOf<FutureExecution>()

            var direction =
                null as FuturePositionDirection?

            var signedPosition =
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

            fun currentAverageEntry(): Double {

                return if (
                    totalOpened >
                    0.0
                ) {

                    entryNotional /
                            totalOpened

                } else {
                    0.0
                }
            }

            fun resetState() {

                currentExecutions =
                    mutableListOf()

                direction =
                    null

                signedPosition =
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
                status: FutureTradeStatus
            ) {

                if (
                    currentExecutions.isEmpty() ||
                    direction == null
                ) {
                    return
                }

                val firstExecution =
                    currentExecutions.first()

                val avgEntry =
                    currentAverageEntry()

                val avgExit =
                    if (
                        totalClosed >
                        0.0
                    ) {

                        exitNotional /
                                totalClosed

                    } else {
                        null
                    }

                val deterministicIdSource =
                    listOf(
                        firstExecution.contract.symbol,
                        openDate,
                        currentExecutions.first().id
                    )
                        .joinToString(
                            "|"
                        )

                val id =
                    UUID.nameUUIDFromBytes(
                        deterministicIdSource
                            .toByteArray(
                                StandardCharsets.UTF_8
                            )
                    )
                        .toString()

                episodes.add(
                    FutureTradeEpisode(
                        id =
                            id,

                        contract =
                            firstExecution.contract,

                        direction =
                            direction!!,

                        status =
                            status,

                        openDate =
                            openDate
                                .ifBlank {
                                    firstExecution.executionDate
                                },

                        closeDate =
                            closeDate,

                        netContracts =
                            signedPosition,

                        totalContractsOpened =
                            totalOpened,

                        totalContractsClosed =
                            totalClosed,

                        averageEntryPrice =
                            avgEntry,

                        averageExitPrice =
                            avgExit,

                        realizedPnl =
                            realizedPnl,

                        totalCommission =
                            totalCommission,

                        totalFees =
                            totalFees,

                        executions =
                            currentExecutions
                                .toList()
                    )
                )
            }

            sorted.forEach {
                    activity ->

                val price =
                    activity.price
                        ?: return@forEach

                val contract =
                    buildFutureInstrument(
                        activity
                    )

                val side =
                    executionSide(
                        activity.side
                    )
                        ?: return@forEach

                val execution =
                    FutureExecution(
                        id =
                            activity.id,

                        contract =
                            contract,

                        side =
                            side,

                        contracts =
                            activity.quantity,

                        price =
                            price,

                        executionDate =
                            activity.activityDate,

                        commission =
                            activity.commission,

                        fees =
                            activity.fees,

                        orderId =
                            activity.orderId,

                        source =
                            activity.source
                    )

                /*
                 * If there is no open episode, this execution opens one.
                 */
                if (
                    currentExecutions.isEmpty()
                ) {

                    openDate =
                        activity.activityDate

                    direction =
                        if (
                            side ==
                            FutureExecutionSide.BUY
                        ) {
                            FuturePositionDirection.LONG
                        } else {
                            FuturePositionDirection.SHORT
                        }
                }

                val currentDirection =
                    direction
                        ?: return@forEach

                val isOpeningExecution =
                    when (
                        currentDirection
                    ) {

                        FuturePositionDirection.LONG ->
                            side ==
                                    FutureExecutionSide.BUY

                        FuturePositionDirection.SHORT ->
                            side ==
                                    FutureExecutionSide.SELL
                    }

                currentExecutions.add(
                    execution
                )

                totalCommission +=
                    activity.commission

                totalFees +=
                    activity.fees

                if (
                    isOpeningExecution
                ) {

                    val signedDelta =
                        when (
                            currentDirection
                        ) {

                            FuturePositionDirection.LONG ->
                                activity.quantity

                            FuturePositionDirection.SHORT ->
                                -activity.quantity
                        }

                    signedPosition +=
                        signedDelta

                    totalOpened +=
                        activity.quantity

                    entryNotional +=
                        activity.quantity *
                                price

                    realizedPnl -=
                        activity.commission +
                                activity.fees

                } else {

                    val closeContracts =
                        minOf(
                            activity.quantity,
                            abs(
                                signedPosition
                            )
                        )

                    if (
                        closeContracts >
                        0.0
                    ) {

                        val avgEntry =
                            currentAverageEntry()

                        val tradePnl =
                            when (
                                currentDirection
                            ) {

                                FuturePositionDirection.LONG ->
                                    (
                                            price -
                                                    avgEntry
                                            ) *
                                            contract.pointValue *
                                            closeContracts

                                FuturePositionDirection.SHORT ->
                                    (
                                            avgEntry -
                                                    price
                                            ) *
                                            contract.pointValue *
                                            closeContracts
                            }

                        realizedPnl +=
                            tradePnl

                        realizedPnl -=
                            activity.commission +
                                    activity.fees

                        totalClosed +=
                            closeContracts

                        exitNotional +=
                            closeContracts *
                                    price

                        signedPosition =
                            when (
                                currentDirection
                            ) {

                                FuturePositionDirection.LONG ->
                                    signedPosition -
                                            closeContracts

                                FuturePositionDirection.SHORT ->
                                    signedPosition +
                                            closeContracts
                            }

                        if (
                            abs(
                                signedPosition
                            ) >
                            0.000001
                        ) {

                            hadPartialClose =
                                true
                        }
                    }

                    /*
                     * If a single execution crosses through zero,
                     * close the current episode at zero and open a
                     * fresh opposite-direction episode for the excess.
                     */
                    val excessContracts =
                        activity.quantity -
                                closeContracts

                    val episodeClosed =
                        abs(
                            signedPosition
                        ) <
                                0.000001

                    if (
                        episodeClosed
                    ) {

                        buildEpisode(
                            closeDate =
                                activity.activityDate,
                            status =
                                FutureTradeStatus.CLOSED
                        )

                        val excess =
                            excessContracts

                        resetState()

                        if (
                            excess >
                            0.000001
                        ) {

                            openDate =
                                activity.activityDate

                            direction =
                                if (
                                    side ==
                                    FutureExecutionSide.BUY
                                ) {
                                    FuturePositionDirection.LONG
                                } else {
                                    FuturePositionDirection.SHORT
                                }

                            val rolloverExecution =
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

                                    contracts =
                                        excess,

                                    commission =
                                        0.0,

                                    fees =
                                        0.0
                                )

                            currentExecutions.add(
                                rolloverExecution
                            )

                            totalOpened =
                                excess

                            entryNotional =
                                excess *
                                        price

                            signedPosition =
                                if (
                                    direction ==
                                    FuturePositionDirection.LONG
                                ) {
                                    excess
                                } else {
                                    -excess
                                }
                        }
                    }
                }
            }

            if (
                currentExecutions.isNotEmpty() &&
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
                            FutureTradeStatus.PARTIAL
                        } else {
                            FutureTradeStatus.OPEN
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

    private fun buildContractKey(
        activity: NormalizedTradeActivity
    ): String {

        return listOf(
            activity.symbol
                .trim()
                .uppercase(
                    Locale.US
                ),

            activity.futuresExpirationDate
                .orEmpty()
                .trim()
                .uppercase(
                    Locale.US
                ),

            activity.accountId
                .orEmpty()
                .trim()
                .uppercase(
                    Locale.US
                ),

            activity.source.name
        )
            .joinToString(
                "|"
            )
    }

    private fun buildFutureInstrument(
        activity: NormalizedTradeActivity
    ): FutureInstrument {

        val symbol =
            activity.symbol
                .trim()
                .uppercase(
                    Locale.US
                )

        val root =
            activity.futuresRootSymbol
                ?.trim()
                ?.uppercase(
                    Locale.US
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: detectFutureRoot(
                    symbol
                )

        val expiration =
            activity.futuresExpirationDate
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: detectExpirationLabel(
                    symbol
                )

        val specs =
            FutureContractSpecs.forRoot(
                root
            )

        return FutureInstrument(
            symbol =
                symbol,

            rootSymbol =
                root,

            expirationDate =
                expiration,

            pointValue =
                activity.futuresPointValue
                    ?: specs.pointValue,

            tickSize =
                activity.futuresTickSize
                    ?: specs.tickSize,

            tickValue =
                activity.futuresTickValue
                    ?: specs.tickValue,

            exchange =
                specs.exchange
        )
    }

    private fun executionSide(
        side: NormalizedTradeSide
    ): FutureExecutionSide? {

        return when (
            side
        ) {

            NormalizedTradeSide.BUY,
            NormalizedTradeSide.COVER,
            NormalizedTradeSide.BUY_TO_OPEN,
            NormalizedTradeSide.BUY_TO_CLOSE ->
                FutureExecutionSide.BUY

            NormalizedTradeSide.SELL,
            NormalizedTradeSide.SHORT,
            NormalizedTradeSide.SELL_TO_OPEN,
            NormalizedTradeSide.SELL_TO_CLOSE ->
                FutureExecutionSide.SELL

            NormalizedTradeSide.EXPIRATION,
            NormalizedTradeSide.ASSIGNMENT,
            NormalizedTradeSide.EXERCISE,
            NormalizedTradeSide.UNKNOWN ->
                null
        }
    }

    private fun detectFutureRoot(
        symbol: String
    ): String {

        val compact =
            symbol
                .replace(
                    "/",
                    ""
                )
                .replace(
                    "!",
                    ""
                )
                .replace(
                    " ",
                    ""
                )
                .uppercase(
                    Locale.US
                )

        val monthCodes =
            setOf(
                'F',
                'G',
                'H',
                'J',
                'K',
                'M',
                'N',
                'Q',
                'U',
                'V',
                'X',
                'Z'
            )

        val letters =
            compact.takeWhile {
                it.isLetter()
            }

        if (
            letters.isBlank()
        ) {
            return compact
        }

        return if (
            letters.length >
            1 &&
            letters.last() in
            monthCodes
        ) {

            letters.dropLast(
                1
            )

        } else {
            letters
        }
    }

    private fun detectExpirationLabel(
        symbol: String
    ): String {

        val compact =
            symbol
                .replace(
                    "/",
                    ""
                )
                .replace(
                    "!",
                    ""
                )
                .replace(
                    " ",
                    ""
                )
                .uppercase(
                    Locale.US
                )

        val match =
            Regex(
                """([FGHJKMNQUVXZ]\d{1,4})$"""
            )
                .find(
                    compact
                )

        return match
            ?.groupValues
            ?.getOrNull(
                1
            )
            ?: ""
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

data class FutureContractSpec(
    val pointValue: Double,
    val tickSize: Double,
    val tickValue: Double,
    val exchange: String? = null
)

object FutureContractSpecs {

    private val specs =
        mapOf(
            "ES" to
                    FutureContractSpec(
                        pointValue = 50.0,
                        tickSize = 0.25,
                        tickValue = 12.50,
                        exchange = "CME"
                    ),

            "MES" to
                    FutureContractSpec(
                        pointValue = 5.0,
                        tickSize = 0.25,
                        tickValue = 1.25,
                        exchange = "CME"
                    ),

            "NQ" to
                    FutureContractSpec(
                        pointValue = 20.0,
                        tickSize = 0.25,
                        tickValue = 5.00,
                        exchange = "CME"
                    ),

            "MNQ" to
                    FutureContractSpec(
                        pointValue = 2.0,
                        tickSize = 0.25,
                        tickValue = 0.50,
                        exchange = "CME"
                    ),

            "YM" to
                    FutureContractSpec(
                        pointValue = 5.0,
                        tickSize = 1.0,
                        tickValue = 5.0,
                        exchange = "CBOT"
                    ),

            "MYM" to
                    FutureContractSpec(
                        pointValue = 0.50,
                        tickSize = 1.0,
                        tickValue = 0.50,
                        exchange = "CBOT"
                    ),

            "RTY" to
                    FutureContractSpec(
                        pointValue = 50.0,
                        tickSize = 0.10,
                        tickValue = 5.0,
                        exchange = "CME"
                    ),

            "M2K" to
                    FutureContractSpec(
                        pointValue = 5.0,
                        tickSize = 0.10,
                        tickValue = 0.50,
                        exchange = "CME"
                    ),

            "CL" to
                    FutureContractSpec(
                        pointValue = 1000.0,
                        tickSize = 0.01,
                        tickValue = 10.0,
                        exchange = "NYMEX"
                    ),

            "MCL" to
                    FutureContractSpec(
                        pointValue = 100.0,
                        tickSize = 0.01,
                        tickValue = 1.0,
                        exchange = "NYMEX"
                    ),

            "GC" to
                    FutureContractSpec(
                        pointValue = 100.0,
                        tickSize = 0.10,
                        tickValue = 10.0,
                        exchange = "COMEX"
                    ),

            "MGC" to
                    FutureContractSpec(
                        pointValue = 10.0,
                        tickSize = 0.10,
                        tickValue = 1.0,
                        exchange = "COMEX"
                    ),

            "SI" to
                    FutureContractSpec(
                        pointValue = 5000.0,
                        tickSize = 0.005,
                        tickValue = 25.0,
                        exchange = "COMEX"
                    ),

            "SIL" to
                    FutureContractSpec(
                        pointValue = 1000.0,
                        tickSize = 0.005,
                        tickValue = 5.0,
                        exchange = "COMEX"
                    )
        )

    fun forRoot(
        rootSymbol: String
    ): FutureContractSpec {

        val normalized =
            rootSymbol
                .trim()
                .uppercase(
                    Locale.US
                )

        return specs[
            normalized
        ]
            ?: FutureContractSpec(
                pointValue = 1.0,
                tickSize = 0.01,
                tickValue = 0.01,
                exchange = null
            )
    }
}
