package com.tradna.APP.data

import kotlin.math.abs

object TradeReconstructor {

    private const val EPSILON = 0.000001

    private data class Lot(
        var quantity: Double,
        val costPerShare: Double
    )

    private data class EpisodeBuilder(
        val symbol: String,
        val sequenceNumber: Int,
        val openDate: String,

        var closeDate: String? = null,

        var totalSharesBought: Double = 0.0,
        var totalSharesSold: Double = 0.0,

        var totalBuyCost: Double = 0.0,
        var totalSellProceeds: Double = 0.0,

        var realizedPnl: Double = 0.0,

        val executions:
        MutableList<StockExecution> =
            mutableListOf(),

        val lots:
        MutableList<Lot> =
            mutableListOf()
    )

    fun reconstruct(
        activities: List<RobinhoodActivity>
    ): List<TradeEpisode> {

        val chronological =
            activities
                .asReversed()
                .filter {
                    it.instrument.isNotBlank() &&
                            (
                                    it.transCode.equals(
                                        "Buy",
                                        ignoreCase = true
                                    ) ||
                                            it.transCode.equals(
                                                "Sell",
                                                ignoreCase = true
                                            )
                                    )
                }

        val currentEpisodes =
            mutableMapOf<String, EpisodeBuilder>()

        val sequenceCounters =
            mutableMapOf<String, Int>()

        val completed =
            mutableListOf<TradeEpisode>()

        chronological.forEach { activity ->

            val symbol =
                activity.instrument.trim()

            val side =
                activity.transCode
                    .trim()
                    .uppercase()

            val quantity =
                parseNumber(
                    activity.quantity
                )
                    ?: return@forEach

            if (quantity <= 0.0) {
                return@forEach
            }

            val statedPrice =
                parseMoney(
                    activity.price
                )
                    ?.let {
                        abs(it)
                    }
                    ?: 0.0

            val amount =
                parseMoney(
                    activity.amount
                )

            if (side == "BUY") {

                var episode =
                    currentEpisodes[symbol]

                if (episode == null) {

                    val nextSequence =
                        (
                                sequenceCounters[symbol]
                                    ?: 0
                                ) + 1

                    sequenceCounters[symbol] =
                        nextSequence

                    episode =
                        EpisodeBuilder(
                            symbol =
                                symbol,

                            sequenceNumber =
                                nextSequence,

                            openDate =
                                activity.activityDate
                        )

                    currentEpisodes[symbol] =
                        episode
                }

                val buyCash =
                    when {

                        amount != null &&
                                amount < 0.0 -> {
                            abs(amount)
                        }

                        statedPrice > 0.0 -> {
                            quantity *
                                    statedPrice
                        }

                        else -> {
                            0.0
                        }
                    }

                val effectivePrice =
                    if (
                        quantity > EPSILON
                    ) {
                        buyCash / quantity
                    } else {
                        statedPrice
                    }

                episode
                    .totalSharesBought +=
                    quantity

                episode
                    .totalBuyCost +=
                    buyCash

                episode.lots.add(
                    Lot(
                        quantity =
                            quantity,

                        costPerShare =
                            effectivePrice
                    )
                )

                episode.executions.add(
                    StockExecution(
                        activityDate =
                            activity.activityDate,

                        symbol =
                            symbol,

                        side =
                            "BUY",

                        quantity =
                            quantity,

                        statedPrice =
                            statedPrice,

                        actualCash =
                            buyCash,

                        source =
                            activity
                    )
                )
            }

            if (side == "SELL") {

                val episode =
                    currentEpisodes[symbol]
                        ?: return@forEach

                val availableShares =
                    episode.lots.sumOf {
                        it.quantity
                    }

                val sellQuantity =
                    minOf(
                        quantity,
                        availableShares
                    )

                if (
                    sellQuantity <= EPSILON
                ) {
                    return@forEach
                }

                val fullSellCash =
                    when {

                        amount != null &&
                                amount > 0.0 -> {
                            amount
                        }

                        statedPrice > 0.0 -> {
                            quantity *
                                    statedPrice
                        }

                        else -> {
                            0.0
                        }
                    }

                val proceedsPerShare =
                    if (
                        quantity > EPSILON
                    ) {
                        fullSellCash /
                                quantity
                    } else {
                        statedPrice
                    }

                val recognizedProceeds =
                    proceedsPerShare *
                            sellQuantity

                var quantityToMatch =
                    sellQuantity

                var matchedCostBasis =
                    0.0

                while (
                    quantityToMatch >
                    EPSILON &&
                    episode.lots
                        .isNotEmpty()
                ) {

                    val lot =
                        episode.lots.first()

                    val matched =
                        minOf(
                            lot.quantity,
                            quantityToMatch
                        )

                    matchedCostBasis +=
                        matched *
                                lot.costPerShare

                    lot.quantity -=
                        matched

                    quantityToMatch -=
                        matched

                    if (
                        lot.quantity <=
                        EPSILON
                    ) {
                        episode.lots
                            .removeAt(0)
                    }
                }

                episode
                    .totalSharesSold +=
                    sellQuantity

                episode
                    .totalSellProceeds +=
                    recognizedProceeds

                episode
                    .realizedPnl +=
                    recognizedProceeds -
                            matchedCostBasis

                episode.executions.add(
                    StockExecution(
                        activityDate =
                            activity.activityDate,

                        symbol =
                            symbol,

                        side =
                            "SELL",

                        quantity =
                            sellQuantity,

                        statedPrice =
                            statedPrice,

                        actualCash =
                            recognizedProceeds,

                        source =
                            activity
                    )
                )

                val remaining =
                    episode.lots
                        .sumOf {
                            it.quantity
                        }

                if (
                    remaining <=
                    EPSILON
                ) {

                    episode.closeDate =
                        activity.activityDate

                    completed.add(
                        episode.toTradeEpisode()
                    )

                    currentEpisodes.remove(
                        symbol
                    )
                }
            }
        }

        currentEpisodes
            .values
            .forEach { builder ->

                completed.add(
                    builder.toTradeEpisode()
                )
            }

        return completed
            .sortedWith(
                compareByDescending<TradeEpisode> {
                    dateSortKey(
                        it.openDate
                    )
                }
                    .thenByDescending {
                        it.sequenceNumber
                    }
            )
    }

    private fun EpisodeBuilder
            .toTradeEpisode():
            TradeEpisode {

        val remainingShares =
            lots.sumOf {
                it.quantity
            }

        val averageEntry =
            if (
                totalSharesBought >
                EPSILON
            ) {
                totalBuyCost /
                        totalSharesBought
            } else {
                0.0
            }

        val averageExit =
            if (
                totalSharesSold >
                EPSILON
            ) {
                totalSellProceeds /
                        totalSharesSold
            } else {
                null
            }

        val status =
            when {

                remainingShares <=
                        EPSILON -> {
                    TradeStatus.CLOSED
                }

                totalSharesSold >
                        EPSILON -> {
                    TradeStatus.PARTIAL
                }

                else -> {
                    TradeStatus.OPEN
                }
            }

        return TradeEpisode(
            id =
                "$symbol-$sequenceNumber",

            symbol =
                symbol,

            sequenceNumber =
                sequenceNumber,

            openDate =
                openDate,

            closeDate =
                closeDate,

            totalSharesBought =
                totalSharesBought,

            totalSharesSold =
                totalSharesSold,

            remainingShares =
                remainingShares,

            totalBuyCost =
                totalBuyCost,

            totalSellProceeds =
                totalSellProceeds,

            averageEntryPrice =
                averageEntry,

            averageExitPrice =
                averageExit,

            realizedPnl =
                realizedPnl,

            status =
                status,

            executions =
                executions.toList()
        )
    }

    private fun parseNumber(
        value: String
    ): Double? {

        return value
            .replace(",", "")
            .trim()
            .toDoubleOrNull()
    }

    private fun parseMoney(
        value: String
    ): Double? {

        if (
            value.isBlank()
        ) {
            return null
        }

        val trimmed =
            value.trim()

        val negative =
            trimmed.startsWith("(") &&
                    trimmed.endsWith(")")

        val cleaned =
            trimmed
                .replace("$", "")
                .replace(",", "")
                .replace("(", "")
                .replace(")", "")
                .trim()

        val number =
            cleaned.toDoubleOrNull()
                ?: return null

        return if (
            negative
        ) {
            -number
        } else {
            number
        }
    }

    private fun dateSortKey(
        value: String
    ): Long {

        val pieces =
            value.split("/")

        if (
            pieces.size != 3
        ) {
            return 0L
        }

        val month =
            pieces[0]
                .toLongOrNull()
                ?: 0L

        val day =
            pieces[1]
                .toLongOrNull()
                ?: 0L

        val year =
            pieces[2]
                .toLongOrNull()
                ?: 0L

        return (
                year *
                        10000L
                ) +
                (
                        month *
                                100L
                        ) +
                day
    }
}

