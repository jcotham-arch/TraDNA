package com.tradna.APP.data

import com.tradna.APP.market.OptionRight
import java.util.Locale

data class NormalizedOptionConversionResult(
    val activities: List<RawOptionActivity>,
    val rowsDetected: Int,
    val rowsConverted: Int,
    val rowsSkipped: Int,
    val warnings: List<String>
)

object NormalizedOptionActivityAdapter {

    fun convert(
        activities: List<NormalizedTradeActivity>
    ): NormalizedOptionConversionResult {

        val optionRows =
            activities.filter {
                it.assetClass ==
                        NormalizedAssetClass.OPTION
            }

        val converted =
            mutableListOf<RawOptionActivity>()

        val warnings =
            mutableListOf<String>()

        optionRows.forEachIndexed {
                index,
                activity ->

            val contract =
                resolveContract(
                    activity
                )

            if (
                contract == null
            ) {

                warnings.add(
                    "Skipped option row ${index + 1}: TraDNA could not determine the underlying, expiration, strike, and call/put information for ${activity.symbol}."
                )

                return@forEachIndexed
            }

            val transactionType =
                mapTransactionType(
                    activity
                )

            if (
                transactionType ==
                OptionTransactionType.UNKNOWN
            ) {

                warnings.add(
                    "Skipped option row ${index + 1}: the option side/open-close action is ambiguous for ${activity.symbol}."
                )

                return@forEachIndexed
            }

            val premium =
                when (
                    transactionType
                ) {

                    OptionTransactionType.EXPIRATION,
                    OptionTransactionType.ASSIGNMENT,
                    OptionTransactionType.EXERCISE ->
                        activity.price
                            ?: 0.0

                    else ->
                        activity.price
                            ?: run {

                                warnings.add(
                                    "Skipped option row ${index + 1}: missing option premium for ${activity.symbol}."
                                )

                                return@forEachIndexed
                            }
                }

            converted.add(
                RawOptionActivity(
                    id =
                        activity.id,

                    contractSymbol =
                        canonicalContractSymbol(
                            underlying =
                                contract.underlying,
                            expiration =
                                contract.expiration,
                            right =
                                contract.right,
                            strike =
                                contract.strike
                        ),

                    underlyingSymbol =
                        contract.underlying,

                    expirationDate =
                        contract.expiration,

                    strikePrice =
                        contract.strike,

                    right =
                        contract.right,

                    transactionType =
                        transactionType,

                    contracts =
                        activity.quantity,

                    premium =
                        kotlin.math.abs(
                            premium
                        ),

                    activityDate =
                        activity.activityDate,

                    fees =
                        activity.commission +
                                activity.fees,

                    contractMultiplier =
                        activity.optionContractMultiplier
                            ?: 100.0
                )
            )
        }

        return NormalizedOptionConversionResult(
            activities =
                converted,

            rowsDetected =
                optionRows.size,

            rowsConverted =
                converted.size,

            rowsSkipped =
                optionRows.size -
                        converted.size,

            warnings =
                warnings
        )
    }

    private fun resolveContract(
        activity: NormalizedTradeActivity
    ): ResolvedOptionContract? {

        val explicitUnderlying =
            activity.underlyingSymbol
                ?.trim()
                ?.uppercase(
                    Locale.US
                )
                ?.takeIf {
                    it.isNotBlank()
                }

        val explicitExpiration =
            activity.optionExpirationDate
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        val explicitStrike =
            activity.optionStrikePrice

        val explicitRight =
            parseRight(
                activity.optionRight
            )

        if (
            explicitUnderlying != null &&
            explicitExpiration != null &&
            explicitStrike != null &&
            explicitRight != null
        ) {

            return ResolvedOptionContract(
                underlying =
                    explicitUnderlying,

                expiration =
                    explicitExpiration,

                strike =
                    explicitStrike,

                right =
                    explicitRight
            )
        }

        parseOccSymbol(
            activity.symbol
        )
            ?.let {
                return it
            }

        parseHumanReadableSymbol(
            activity.symbol
        )
            ?.let {
                return it
            }

        activity.rawDescription
            ?.let {
                    description ->

                parseHumanReadableSymbol(
                    description
                )
                    ?.let {
                        return it
                    }
            }

        return null
    }

    /*
     * OCC compact format example:
     *
     * AAPL260918C00200000
     *
     * root + YYMMDD + C/P + strike x 1000
     */
    private fun parseOccSymbol(
        value: String
    ): ResolvedOptionContract? {

        val compact =
            value
                .trim()
                .uppercase(
                    Locale.US
                )
                .replace(
                    " ",
                    ""
                )

        val match =
            Regex(
                """^([A-Z]{1,6})(\d{2})(\d{2})(\d{2})([CP])(\d{8})$"""
            )
                .matchEntire(
                    compact
                )
                ?: return null

        val underlying =
            match.groupValues[1]

        val year =
            2000 +
                    match.groupValues[2]
                        .toIntOrNull()
                        .orZero()

        val month =
            match.groupValues[3]
                .toIntOrNull()
                ?: return null

        val day =
            match.groupValues[4]
                .toIntOrNull()
                ?: return null

        val right =
            when (
                match.groupValues[5]
            ) {

                "C" ->
                    OptionRight.CALL

                "P" ->
                    OptionRight.PUT

                else ->
                    return null
            }

        val strike =
            match.groupValues[6]
                .toLongOrNull()
                ?.div(
                    1000.0
                )
                ?: return null

        val expiration =
            String.format(
                Locale.US,
                "%d/%d/%04d",
                month,
                day,
                year
            )

        return ResolvedOptionContract(
            underlying =
                underlying,

            expiration =
                expiration,

            strike =
                strike,

            right =
                right
        )
    }

    /*
     * Handles common readable forms such as:
     *
     * AAPL 9/18/2026 Call $200
     * AAPL 09/18/2026 PUT 200
     * Option AAPL 9/18/2026 C $200.00
     */
    private fun parseHumanReadableSymbol(
        value: String
    ): ResolvedOptionContract? {

        val match =
            Regex(
                pattern =
                    """(?i)([A-Z0-9.\-]{1,12})\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+(CALL|PUT|C|P)\s+\$?([0-9]+(?:\.[0-9]+)?)"""
            )
                .find(
                    value
                )
                ?: return null

        val underlying =
            match.groupValues[1]
                .trim()
                .uppercase(
                    Locale.US
                )

        val expiration =
            normalizeExpiration(
                match.groupValues[2]
            )
                ?: return null

        val right =
            when (
                match.groupValues[3]
                    .trim()
                    .uppercase(
                        Locale.US
                    )
            ) {

                "CALL",
                "C" ->
                    OptionRight.CALL

                "PUT",
                "P" ->
                    OptionRight.PUT

                else ->
                    return null
            }

        val strike =
            match.groupValues[4]
                .toDoubleOrNull()
                ?: return null

        return ResolvedOptionContract(
            underlying =
                underlying,

            expiration =
                expiration,

            strike =
                strike,

            right =
                right
        )
    }

    private fun mapTransactionType(
        activity: NormalizedTradeActivity
    ): OptionTransactionType {

        return when (
            activity.side
        ) {

            NormalizedTradeSide.BUY_TO_OPEN ->
                OptionTransactionType.BUY_TO_OPEN

            NormalizedTradeSide.SELL_TO_CLOSE ->
                OptionTransactionType.SELL_TO_CLOSE

            NormalizedTradeSide.SELL_TO_OPEN ->
                OptionTransactionType.SELL_TO_OPEN

            NormalizedTradeSide.BUY_TO_CLOSE ->
                OptionTransactionType.BUY_TO_CLOSE

            NormalizedTradeSide.EXPIRATION ->
                OptionTransactionType.EXPIRATION

            NormalizedTradeSide.ASSIGNMENT ->
                OptionTransactionType.ASSIGNMENT

            NormalizedTradeSide.EXERCISE ->
                OptionTransactionType.EXERCISE

            /*
             * Plain BUY/SELL do not tell us whether the contract
             * is opening or closing. We intentionally refuse to
             * guess because that can corrupt reconstructed P&L.
             */
            NormalizedTradeSide.BUY,
            NormalizedTradeSide.SELL,
            NormalizedTradeSide.SHORT,
            NormalizedTradeSide.COVER,
            NormalizedTradeSide.UNKNOWN ->
                inferFromText(
                    activity
                )
        }
    }

    private fun inferFromText(
        activity: NormalizedTradeActivity
    ): OptionTransactionType {

        val combined =
            listOfNotNull(
                activity.status,
                activity.notes,
                activity.rawDescription
            )
                .joinToString(
                    " "
                )
                .uppercase(
                    Locale.US
                )

        return when {

            combined.contains(
                "BUY TO OPEN"
            ) ||
                    containsToken(
                        combined,
                        "BTO"
                    ) ->
                OptionTransactionType.BUY_TO_OPEN

            combined.contains(
                "SELL TO CLOSE"
            ) ||
                    containsToken(
                        combined,
                        "STC"
                    ) ->
                OptionTransactionType.SELL_TO_CLOSE

            combined.contains(
                "SELL TO OPEN"
            ) ||
                    containsToken(
                        combined,
                        "STO"
                    ) ->
                OptionTransactionType.SELL_TO_OPEN

            combined.contains(
                "BUY TO CLOSE"
            ) ||
                    containsToken(
                        combined,
                        "BTC"
                    ) ->
                OptionTransactionType.BUY_TO_CLOSE

            combined.contains(
                "EXPIR"
            ) ->
                OptionTransactionType.EXPIRATION

            combined.contains(
                "ASSIGN"
            ) ->
                OptionTransactionType.ASSIGNMENT

            combined.contains(
                "EXERCI"
            ) ->
                OptionTransactionType.EXERCISE

            else ->
                OptionTransactionType.UNKNOWN
        }
    }

    private fun containsToken(
        text: String,
        token: String
    ): Boolean {

        return Regex(
            """(^|[^A-Z])${Regex.escape(token)}([^A-Z]|$)"""
        )
            .containsMatchIn(
                text
            )
    }

    private fun parseRight(
        value: String?
    ): OptionRight? {

        return when (
            value
                ?.trim()
                ?.uppercase(
                    Locale.US
                )
        ) {

            "CALL",
            "C" ->
                OptionRight.CALL

            "PUT",
            "P" ->
                OptionRight.PUT

            else ->
                null
        }
    }

    private fun normalizeExpiration(
        value: String
    ): String? {

        val parts =
            value
                .trim()
                .split(
                    "/"
                )

        if (
            parts.size !=
            3
        ) {
            return null
        }

        val month =
            parts[0]
                .toIntOrNull()
                ?: return null

        val day =
            parts[1]
                .toIntOrNull()
                ?: return null

        var year =
            parts[2]
                .toIntOrNull()
                ?: return null

        if (
            year <
            100
        ) {
            year +=
                2000
        }

        return String.format(
            Locale.US,
            "%d/%d/%04d",
            month,
            day,
            year
        )
    }

    private fun canonicalContractSymbol(
        underlying: String,
        expiration: String,
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
                expiration
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

    private fun Int?.orZero(): Int =
        this ?: 0

    private data class ResolvedOptionContract(
        val underlying: String,
        val expiration: String,
        val strike: Double,
        val right: OptionRight
    )
}
