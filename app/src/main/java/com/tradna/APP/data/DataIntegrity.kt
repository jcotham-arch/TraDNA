package com.tradna.APP.data

data class DataIntegrityReport(
    val totalRecords: Int,
    val buyRecords: Int,
    val sellRecords: Int,
    val optionRecords: Int,
    val otherRecords: Int,

    val uniqueInstruments: Int,

    val reconstructedTrades: Int,
    val closedTrades: Int,
    val openTrades: Int,
    val partialTrades: Int,

    val blankInstrumentRecords: Int,
    val blankDateRecords: Int,
    val blankTransactionCodeRecords: Int,

    val invalidQuantityRecords: Int,
    val invalidPriceRecords: Int,
    val invalidAmountRecords: Int,

    val unmatchedSellRecords: Int,

    val warningCount: Int,
    val healthScore: Int,

    val warnings: List<String>
)

object DataIntegrityEngine {

    private val optionCodes =
        setOf(
            "STO",
            "BTC",
            "BTO",
            "STC",
            "OEXP",
            "OASGN"
        )

    private val stockTradeCodes =
        setOf(
            "BUY",
            "SELL"
        )

    private val instrumentRequiredCodes =
        stockTradeCodes +
                optionCodes

    fun analyze(
        activities: List<RobinhoodActivity>,
        trades: List<TradeEpisode>
    ): DataIntegrityReport {

        val buyRecords =
            activities.count {
                it.transCode
                    .trim()
                    .uppercase() ==
                        "BUY"
            }

        val sellRecords =
            activities.count {
                it.transCode
                    .trim()
                    .uppercase() ==
                        "SELL"
            }

        val optionRecords =
            activities.count {
                it.transCode
                    .trim()
                    .uppercase() in
                        optionCodes
            }

        /*
         * IMPORTANT:
         *
         * Robinhood reports legitimately contain account-level
         * records with no instrument/symbol.
         *
         * Examples can include:
         *
         * cash activity
         * deposits
         * withdrawals
         * interest
         * fees
         * dividends
         * some corporate/account events
         *
         * We therefore only flag a missing instrument when the
         * transaction type is one that SHOULD identify a traded
         * security.
         */
        val blankInstrumentRecords =
            activities.count { activity ->

                val code =
                    activity.transCode
                        .trim()
                        .uppercase()

                code in instrumentRequiredCodes &&
                        activity.instrument
                            .isBlank()
            }

        /*
         * Every brokerage activity record should normally
         * have an activity date regardless of transaction type.
         */
        val blankDateRecords =
            activities.count {
                it.activityDate
                    .isBlank()
            }

        /*
         * A record without any transaction code is genuinely
         * difficult to classify, so this remains a warning.
         */
        val blankTransactionCodeRecords =
            activities.count {
                it.transCode
                    .isBlank()
            }

        /*
         * Quantity is required for transactions involving
         * security quantities.
         */
        val invalidQuantityRecords =
            activities.count { activity ->

                val code =
                    activity.transCode
                        .trim()
                        .uppercase()

                val needsQuantity =
                    code in
                            instrumentRequiredCodes

                needsQuantity &&
                        activity.quantity
                            .cleanNumber()
                            .toDoubleOrNull() ==
                        null
            }

        /*
         * Stock BUY/SELL executions should contain a usable
         * execution price.
         *
         * We do not automatically apply this rule to every
         * option event because expiration/assignment records
         * can have different export structures.
         */
        val invalidPriceRecords =
            activities.count { activity ->

                val code =
                    activity.transCode
                        .trim()
                        .uppercase()

                val needsPrice =
                    code in
                            stockTradeCodes

                needsPrice &&
                        activity.price
                            .cleanNumber()
                            .toDoubleOrNull() ==
                        null
            }

        /*
         * Amount is only validated when Robinhood actually
         * supplied a value.
         */
        val invalidAmountRecords =
            activities.count {

                it.amount.isNotBlank() &&
                        it.amount
                            .cleanNumber()
                            .toDoubleOrNull() ==
                        null
            }

        val knownTradeCodes =
            stockTradeCodes +
                    optionCodes

        /*
         * "Other" does NOT mean bad.
         *
         * It means the record is not a normal stock execution
         * or one of the option transaction types currently
         * modeled by TraDNA.
         */
        val otherRecords =
            activities.count {

                it.transCode
                    .trim()
                    .uppercase() !in
                        knownTradeCodes
            }

        val uniqueInstruments =
            activities
                .map {
                    it.instrument
                        .trim()
                        .uppercase()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()
                .size

        val closedTrades =
            trades.count {
                it.status ==
                        TradeStatus.CLOSED
            }

        val openTrades =
            trades.count {
                it.status ==
                        TradeStatus.OPEN
            }

        val partialTrades =
            trades.count {
                it.status ==
                        TradeStatus.PARTIAL
            }

        val unmatchedSellRecords =
            calculateUnmatchedSells(
                activities
            )

        val warnings =
            mutableListOf<String>()

        if (
            blankInstrumentRecords >
            0
        ) {

            warnings.add(
                "$blankInstrumentRecords trade records are missing an instrument or symbol."
            )
        }

        if (
            blankDateRecords >
            0
        ) {

            warnings.add(
                "$blankDateRecords records have no activity date."
            )
        }

        if (
            blankTransactionCodeRecords >
            0
        ) {

            warnings.add(
                "$blankTransactionCodeRecords records have no transaction code."
            )
        }

        if (
            invalidQuantityRecords >
            0
        ) {

            warnings.add(
                "$invalidQuantityRecords trade records contain an unreadable quantity."
            )
        }

        if (
            invalidPriceRecords >
            0
        ) {

            warnings.add(
                "$invalidPriceRecords stock trade records contain an unreadable price."
            )
        }

        if (
            invalidAmountRecords >
            0
        ) {

            warnings.add(
                "$invalidAmountRecords records contain an unreadable amount."
            )
        }

        if (
            unmatchedSellRecords >
            0
        ) {

            warnings.add(
                "$unmatchedSellRecords sells exceed shares acquired within the imported history."
            )
        }

        if (
            activities.isEmpty()
        ) {

            warnings.add(
                "No Robinhood activity is currently stored."
            )
        }

        /*
         * Health scoring should only penalize conditions that
         * actually threaten data reliability.
         *
         * Legitimate account-level rows without symbols are
         * therefore no longer penalized.
         */
        var score =
            100

        score -=
            blankInstrumentRecords *
                    5

        score -=
            blankDateRecords *
                    5

        score -=
            blankTransactionCodeRecords *
                    5

        score -=
            invalidQuantityRecords *
                    4

        score -=
            invalidPriceRecords *
                    4

        score -=
            invalidAmountRecords *
                    2

        score -=
            unmatchedSellRecords *
                    3

        val healthScore =
            score.coerceIn(
                0,
                100
            )

        return DataIntegrityReport(
            totalRecords =
                activities.size,

            buyRecords =
                buyRecords,

            sellRecords =
                sellRecords,

            optionRecords =
                optionRecords,

            otherRecords =
                otherRecords,

            uniqueInstruments =
                uniqueInstruments,

            reconstructedTrades =
                trades.size,

            closedTrades =
                closedTrades,

            openTrades =
                openTrades,

            partialTrades =
                partialTrades,

            blankInstrumentRecords =
                blankInstrumentRecords,

            blankDateRecords =
                blankDateRecords,

            blankTransactionCodeRecords =
                blankTransactionCodeRecords,

            invalidQuantityRecords =
                invalidQuantityRecords,

            invalidPriceRecords =
                invalidPriceRecords,

            invalidAmountRecords =
                invalidAmountRecords,

            unmatchedSellRecords =
                unmatchedSellRecords,

            warningCount =
                warnings.size,

            healthScore =
                healthScore,

            warnings =
                warnings
        )
    }

    private fun calculateUnmatchedSells(
        activities: List<RobinhoodActivity>
    ): Int {

        val balances =
            mutableMapOf<String, Double>()

        var unmatched =
            0

        /*
         * Robinhood exports have commonly been observed in
         * newest-first order in our import workflow.
         *
         * Position checking needs chronological processing.
         */
        activities
            .asReversed()
            .forEach { activity ->

                val symbol =
                    activity.instrument
                        .trim()
                        .uppercase()

                val code =
                    activity.transCode
                        .trim()
                        .uppercase()

                /*
                 * Non-stock account activity is irrelevant to
                 * this particular position-balance check.
                 */
                if (
                    code !in
                    stockTradeCodes
                ) {

                    return@forEach
                }

                /*
                 * If a stock execution itself has no symbol,
                 * that issue has already been captured by the
                 * missing-instrument integrity rule.
                 */
                if (
                    symbol.isBlank()
                ) {

                    return@forEach
                }

                val quantity =
                    activity.quantity
                        .cleanNumber()
                        .toDoubleOrNull()
                        ?: return@forEach

                when (code) {

                    "BUY" -> {

                        balances[symbol] =
                            (
                                    balances[symbol]
                                        ?: 0.0
                                    ) +
                                    quantity
                    }

                    "SELL" -> {

                        val available =
                            balances[symbol]
                                ?: 0.0

                        if (
                            quantity >
                            available +
                            0.000001
                        ) {

                            unmatched++
                        }

                        balances[symbol] =
                            (
                                    available -
                                            quantity
                                    )
                                .coerceAtLeast(
                                    0.0
                                )
                    }
                }
            }

        return unmatched
    }

    private fun String.cleanNumber(): String {

        return this
            .trim()
            .replace(
                "$",
                ""
            )
            .replace(
                ",",
                ""
            )
            .replace(
                "(",
                "-"
            )
            .replace(
                ")",
                ""
            )
            .trim()
    }
}