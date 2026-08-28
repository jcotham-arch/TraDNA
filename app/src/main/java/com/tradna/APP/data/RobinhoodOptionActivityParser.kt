package com.tradna.APP.data

import com.tradna.APP.market.OptionRight
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

data class RobinhoodOptionParseResult(
    val optionActivities: List<RawOptionActivity>,
    val optionTrades: List<OptionTradeEpisode>,
    val optionRowsDetected: Int,
    val optionRowsParsed: Int,
    val optionRowsSkipped: Int,
    val warnings: List<String>
)

object RobinhoodOptionActivityParser {

    private val supportedOptionCodes =
        setOf(
            "BTO",
            "STC",
            "STO",
            "BTC",
            "OEXP",
            "OASGN",
            "OEXER",
            "OEXC"
        )

    /*
     * Handles Robinhood descriptions such as:
     *
     * ONDS 7/24/2026 Call $7.00
     *
     * Option Expiration for RXT 7/24/2026 Call $4.50
     *
     * The parser deliberately uses the description for expiry/right/strike
     * and the Instrument column for the underlying symbol.
     */
    private val optionDescriptionRegex =
        Regex(
            pattern =
                """(?i)(?:Option\s+(?:Expiration|Assignment|Exercise)\s+for\s+)?([A-Z0-9.\-]+)\s+(\d{1,2}/\d{1,2}/\d{4})\s+(Call|Put)\s+\$?([0-9]+(?:\.[0-9]+)?)"""
        )

    fun parseAndReconstruct(
        activities: List<RobinhoodActivity>
    ): RobinhoodOptionParseResult {

        val optionRows =
            activities.filter {
                it.transCode
                    .trim()
                    .uppercase(
                        Locale.US
                    ) in
                        supportedOptionCodes
            }

        val parsed =
            mutableListOf<RawOptionActivity>()

        val warnings =
            mutableListOf<String>()

        optionRows.forEachIndexed {
                index,
                activity ->

            val result =
                parseActivity(
                    activity
                )

            if (
                result == null
            ) {

                warnings.add(
                    "Skipped option row ${index + 1}: ${activity.instrument} • ${activity.activityDate} • ${activity.transCode} • ${activity.description}"
                )

            } else {

                parsed.add(
                    result
                )
            }
        }

        val trades =
            OptionTradeReconstructor
                .reconstruct(
                    parsed
                )

        return RobinhoodOptionParseResult(
            optionActivities =
                parsed,

            optionTrades =
                trades,

            optionRowsDetected =
                optionRows.size,

            optionRowsParsed =
                parsed.size,

            optionRowsSkipped =
                optionRows.size -
                        parsed.size,

            warnings =
                warnings
        )
    }

    fun parseActivity(
        activity: RobinhoodActivity
    ): RawOptionActivity? {

        val code =
            activity.transCode
                .trim()
                .uppercase(
                    Locale.US
                )

        if (
            code !in
            supportedOptionCodes
        ) {
            return null
        }

        val description =
            activity.description
                .trim()

        val match =
            optionDescriptionRegex
                .find(
                    description
                )
                ?: return null

        val descriptionUnderlying =
            match.groupValues[1]
                .trim()
                .uppercase(
                    Locale.US
                )

        val underlying =
            activity.instrument
                .trim()
                .uppercase(
                    Locale.US
                )
                .ifBlank {
                    descriptionUnderlying
                }

        val expirationDate =
            match.groupValues[2]
                .trim()

        val right =
            when (
                match.groupValues[3]
                    .trim()
                    .uppercase(
                        Locale.US
                    )
            ) {

                "CALL" ->
                    OptionRight.CALL

                "PUT" ->
                    OptionRight.PUT

                else ->
                    return null
            }

        val strike =
            match.groupValues[4]
                .toDoubleOrNull()
                ?: return null

        val contracts =
            parseNumber(
                activity.quantity
            )
                ?.let {
                    kotlin.math.abs(
                        it
                    )
                }
                ?: return null

        if (
            contracts <=
            0.0
        ) {
            return null
        }

        val transactionType =
            transactionTypeForCode(
                code
            )

        if (
            transactionType ==
            OptionTransactionType.UNKNOWN
        ) {
            return null
        }

        /*
         * Expiration / assignment / exercise rows can legitimately
         * have an empty Price field.
         */
        val premium =
            when (
                transactionType
            ) {

                OptionTransactionType.EXPIRATION,
                OptionTransactionType.ASSIGNMENT,
                OptionTransactionType.EXERCISE ->
                    parseMoney(
                        activity.price
                    )
                        ?: 0.0

                else ->
                    parseMoney(
                        activity.price
                    )
                        ?: return null
            }

        /*
         * Robinhood's Instrument field is only the underlying
         * (for example ONDS), so TraDNA creates its own stable
         * contract identity for grouping executions.
         */
        val contractSymbol =
            canonicalContractSymbol(
                underlying =
                    underlying,
                expirationDate =
                    expirationDate,
                right =
                    right,
                strike =
                    strike
            )

        val stableIdSource =
            listOf(
                activity.activityDate,
                underlying,
                description,
                code,
                activity.quantity,
                activity.price,
                activity.amount
            )
                .joinToString(
                    "|"
                )

        val id =
            UUID.nameUUIDFromBytes(
                stableIdSource
                    .toByteArray(
                        StandardCharsets.UTF_8
                    )
            )
                .toString()

        return RawOptionActivity(
            id =
                id,

            contractSymbol =
                contractSymbol,

            underlyingSymbol =
                underlying,

            expirationDate =
                expirationDate,

            strikePrice =
                strike,

            right =
                right,

            transactionType =
                transactionType,

            contracts =
                contracts,

            premium =
                premium,

            activityDate =
                activity.activityDate,

            fees =
                0.0,

            contractMultiplier =
                100.0
        )
    }

    private fun transactionTypeForCode(
        code: String
    ): OptionTransactionType {

        return when (
            code
        ) {

            "BTO" ->
                OptionTransactionType.BUY_TO_OPEN

            "STC" ->
                OptionTransactionType.SELL_TO_CLOSE

            "STO" ->
                OptionTransactionType.SELL_TO_OPEN

            "BTC" ->
                OptionTransactionType.BUY_TO_CLOSE

            "OEXP" ->
                OptionTransactionType.EXPIRATION

            "OASGN" ->
                OptionTransactionType.ASSIGNMENT

            "OEXER",
            "OEXC" ->
                OptionTransactionType.EXERCISE

            else ->
                OptionTransactionType.UNKNOWN
        }
    }

    private fun canonicalContractSymbol(
        underlying: String,
        expirationDate: String,
        right: OptionRight,
        strike: Double
    ): String {

        val strikeText =
            String.format(
                Locale.US,
                "%.4f",
                strike
            )
                .trimEnd(
                    '0'
                )
                .trimEnd(
                    '.'
                )

        return buildString {

            append(
                underlying
            )

            append(
                "|"
            )

            append(
                expirationDate
            )

            append(
                "|"
            )

            append(
                right.name
            )

            append(
                "|"
            )

            append(
                strikeText
            )
        }
    }

    private fun parseNumber(
        value: String
    ): Double? {

        val cleaned =
            value
                .trim()
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
                .filter {
                    it.isDigit() ||
                            it == '.' ||
                            it == '-'
                }

        if (
            cleaned.isBlank() ||
            cleaned ==
            "-"
        ) {
            return null
        }

        return cleaned
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

        return parseNumber(
            value
        )
            ?.let {
                kotlin.math.abs(
                    it
                )
            }
    }
}
