package com.tradna.APP.data

import com.tradna.APP.market.OptionInstrument
import com.tradna.APP.market.OptionRight
import java.util.UUID
import kotlin.math.abs

data class RawOptionActivity(
    val id: String,
    val contractSymbol: String,
    val underlyingSymbol: String,
    val expirationDate: String,
    val strikePrice: Double,
    val right: OptionRight,
    val transactionType: OptionTransactionType,
    val contracts: Double,
    val premium: Double,
    val activityDate: String,
    val fees: Double = 0.0,
    val contractMultiplier: Double = 100.0
)

object OptionTradeReconstructor {

    fun reconstruct(
        activities: List<RawOptionActivity>
    ): List<OptionTradeEpisode> {

        if (
            activities.isEmpty()
        ) {
            return emptyList()
        }

        val grouped =
            activities
                .filter {
                    it.contractSymbol.isNotBlank() &&
                            it.underlyingSymbol.isNotBlank() &&
                            it.contracts > 0.0
                }
                .groupBy {
                    it.contractSymbol
                }

        val episodes =
            mutableListOf<OptionTradeEpisode>()

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
                mutableListOf<OptionExecution>()

            var positionContracts =
                0.0

            var openingPremiumDollars =
                0.0

            var openingContracts =
                0.0

            var closingPremiumDollars =
                0.0

            var closingContracts =
                0.0

            var realizedPnl =
                0.0

            var episodeOpenDate =
                sorted.first().activityDate

            var openingDirection =
                OpeningDirection.UNKNOWN

            fun resetEpisodeState() {

                currentExecutions =
                    mutableListOf()

                positionContracts =
                    0.0

                openingPremiumDollars =
                    0.0

                openingContracts =
                    0.0

                closingPremiumDollars =
                    0.0

                closingContracts =
                    0.0

                realizedPnl =
                    0.0

                episodeOpenDate =
                    ""

                openingDirection =
                    OpeningDirection.UNKNOWN
            }

            fun buildEpisode(
                closeDate: String?,
                status: OptionTradeStatus
            ) {

                if (
                    currentExecutions.isEmpty()
                ) {
                    return
                }

                val first =
                    sorted.firstOrNull {
                        it.contractSymbol ==
                                contractActivities.first().contractSymbol
                    }
                        ?: contractActivities.first()

                val contract =
                    OptionInstrument(
                        symbol =
                            first.contractSymbol,
                        underlyingSymbol =
                            first.underlyingSymbol,
                        expirationDate =
                            first.expirationDate,
                        strikePrice =
                            first.strikePrice,
                        right =
                            first.right,
                        contractMultiplier =
                            first.contractMultiplier
                    )

                val averageEntry =
                    if (
                        openingContracts >
                        0.0
                    ) {

                        openingPremiumDollars /
                                openingContracts /
                                contract.contractMultiplier

                    } else {

                        currentExecutions
                            .firstOrNull()
                            ?.premium
                            ?: 0.0
                    }

                val averageExit =
                    if (
                        closingContracts >
                        0.0
                    ) {

                        closingPremiumDollars /
                                closingContracts /
                                contract.contractMultiplier

                    } else {

                        null
                    }

                episodes.add(
                    OptionTradeEpisode(
                        id =
                            UUID.randomUUID()
                                .toString(),

                        contract =
                            contract,

                        status =
                            status,

                        openDate =
                            episodeOpenDate
                                .ifBlank {
                                    currentExecutions
                                        .first()
                                        .executionDate
                                },

                        closeDate =
                            closeDate,

                        netContracts =
                            positionContracts,

                        averageEntryPremium =
                            averageEntry,

                        averageExitPremium =
                            averageExit,

                        realizedPnl =
                            realizedPnl,

                        executions =
                            currentExecutions
                                .toList()
                    )
                )
            }

            sorted.forEach {
                    activity ->

                val execution =
                    OptionExecution(
                        id =
                            activity.id,
                        contract =
                            OptionInstrument(
                                symbol =
                                    activity.contractSymbol,
                                underlyingSymbol =
                                    activity.underlyingSymbol,
                                expirationDate =
                                    activity.expirationDate,
                                strikePrice =
                                    activity.strikePrice,
                                right =
                                    activity.right,
                                contractMultiplier =
                                    activity.contractMultiplier
                            ),
                        transactionType =
                            activity.transactionType,
                        contracts =
                            activity.contracts,
                        premium =
                            activity.premium,
                        executionDate =
                            activity.activityDate,
                        fees =
                            activity.fees
                    )

                if (
                    currentExecutions.isEmpty()
                ) {

                    episodeOpenDate =
                        activity.activityDate
                }

                currentExecutions.add(
                    execution
                )

                when (
                    activity.transactionType
                ) {

                    OptionTransactionType.BUY_TO_OPEN -> {

                        if (
                            openingDirection ==
                            OpeningDirection.UNKNOWN
                        ) {

                            openingDirection =
                                OpeningDirection.LONG
                        }

                        positionContracts +=
                            activity.contracts

                        openingContracts +=
                            activity.contracts

                        openingPremiumDollars +=
                            activity.contracts *
                                    activity.premium *
                                    activity.contractMultiplier

                        realizedPnl -=
                            activity.fees
                    }

                    OptionTransactionType.SELL_TO_OPEN -> {

                        if (
                            openingDirection ==
                            OpeningDirection.UNKNOWN
                        ) {

                            openingDirection =
                                OpeningDirection.SHORT
                        }

                        positionContracts -=
                            activity.contracts

                        openingContracts +=
                            activity.contracts

                        openingPremiumDollars +=
                            activity.contracts *
                                    activity.premium *
                                    activity.contractMultiplier

                        realizedPnl -=
                            activity.fees
                    }

                    OptionTransactionType.SELL_TO_CLOSE -> {

                        val closeContracts =
                            minOf(
                                activity.contracts,
                                abs(
                                    positionContracts
                                )
                            )

                        if (
                            closeContracts >
                            0.0
                        ) {

                            val averageOpenPremium =
                                if (
                                    openingContracts >
                                    0.0
                                ) {

                                    openingPremiumDollars /
                                            openingContracts /
                                            activity.contractMultiplier

                                } else {
                                    0.0
                                }

                            realizedPnl +=
                                (
                                        activity.premium -
                                                averageOpenPremium
                                        ) *
                                        closeContracts *
                                        activity.contractMultiplier

                            closingContracts +=
                                closeContracts

                            closingPremiumDollars +=
                                closeContracts *
                                        activity.premium *
                                        activity.contractMultiplier

                            positionContracts -=
                                closeContracts
                        }

                        realizedPnl -=
                            activity.fees
                    }

                    OptionTransactionType.BUY_TO_CLOSE -> {

                        val closeContracts =
                            minOf(
                                activity.contracts,
                                abs(
                                    positionContracts
                                )
                            )

                        if (
                            closeContracts >
                            0.0
                        ) {

                            val averageOpenPremium =
                                if (
                                    openingContracts >
                                    0.0
                                ) {

                                    openingPremiumDollars /
                                            openingContracts /
                                            activity.contractMultiplier

                                } else {
                                    0.0
                                }

                            realizedPnl +=
                                (
                                        averageOpenPremium -
                                                activity.premium
                                        ) *
                                        closeContracts *
                                        activity.contractMultiplier

                            closingContracts +=
                                closeContracts

                            closingPremiumDollars +=
                                closeContracts *
                                        activity.premium *
                                        activity.contractMultiplier

                            positionContracts +=
                                closeContracts
                        }

                        realizedPnl -=
                            activity.fees
                    }

                    OptionTransactionType.EXPIRATION -> {

                        val closeContracts =
                            abs(
                                positionContracts
                            )

                        if (
                            closeContracts >
                            0.0
                        ) {

                            closingContracts +=
                                closeContracts

                            /*
                             * Expiration closes the option at zero premium.
                             *
                             * LONG premium:
                             *   the paid premium is lost.
                             *
                             * SHORT premium:
                             *   the collected opening premium becomes profit.
                             */
                            closingPremiumDollars +=
                                0.0

                            val averageOpenPremium =
                                if (
                                    openingContracts >
                                    0.0
                                ) {

                                    openingPremiumDollars /
                                            openingContracts /
                                            activity.contractMultiplier

                                } else {
                                    0.0
                                }

                            realizedPnl +=
                                when (
                                    openingDirection
                                ) {

                                    OpeningDirection.LONG ->
                                        -averageOpenPremium *
                                                closeContracts *
                                                activity.contractMultiplier

                                    OpeningDirection.SHORT ->
                                        averageOpenPremium *
                                                closeContracts *
                                                activity.contractMultiplier

                                    OpeningDirection.UNKNOWN ->
                                        0.0
                                }
                        }

                        realizedPnl -=
                            activity.fees

                        positionContracts =
                            0.0
                    }

                    OptionTransactionType.ASSIGNMENT,
                    OptionTransactionType.EXERCISE -> {

                        positionContracts =
                            0.0
                    }

                    OptionTransactionType.UNKNOWN -> {
                        // Keep the execution for audit visibility.
                    }
                }

                val closed =
                    abs(
                        positionContracts
                    ) <
                            0.000001

                if (
                    closed &&
                    (
                            activity.transactionType ==
                                    OptionTransactionType.SELL_TO_CLOSE ||
                                    activity.transactionType ==
                                    OptionTransactionType.BUY_TO_CLOSE ||
                                    activity.transactionType ==
                                    OptionTransactionType.EXPIRATION ||
                                    activity.transactionType ==
                                    OptionTransactionType.ASSIGNMENT ||
                                    activity.transactionType ==
                                    OptionTransactionType.EXERCISE
                            )
                ) {

                    buildEpisode(
                        closeDate =
                            activity.activityDate,
                        status =
                            OptionTradeStatus.CLOSED
                    )

                    resetEpisodeState()
                }
            }

            if (
                currentExecutions.isNotEmpty()
            ) {

                buildEpisode(
                    closeDate =
                        null,
                    status =
                        if (
                            abs(
                                positionContracts
                            ) <
                            0.000001
                        ) {
                            OptionTradeStatus.CLOSED
                        } else {
                            OptionTradeStatus.OPEN
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

    private fun parseSortableDate(
        value: String
    ): Long {

        val formats =
            listOf(
                "M/d/yyyy",
                "MM/dd/yyyy",
                "yyyy-MM-dd",
                "M/d/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss"
            )

        formats.forEach {
                pattern ->

            try {

                val parser =
                    java.text.SimpleDateFormat(
                        pattern,
                        java.util.Locale.US
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
                // Try next supported format.
            }
        }

        return Long.MIN_VALUE
    }

    private enum class OpeningDirection {
        LONG,
        SHORT,
        UNKNOWN
    }
}
