package com.tradna.APP.data

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

object TradingViewCsvParser : TradingPlatformImporter {

    override val source: TradingPlatformSource =
        TradingPlatformSource.TRADINGVIEW

    override fun canParse(
        csvText: String
    ): Boolean {

        val header =
            csvText
                .lineSequence()
                .firstOrNull()
                ?.lowercase()
                ?: return false

        return header.contains(
            "symbol"
        ) &&
                header.contains(
                    "side"
                ) &&
                (
                        header.contains(
                            "qty"
                        ) ||
                                header.contains(
                                    "quantity"
                                )
                        ) &&
                header.contains(
                    "price"
                )
    }

    override fun parse(
        csvText: String
    ): PlatformImportResult {

        val rows =
            parseCsv(
                csvText
            )

        if (
            rows.isEmpty()
        ) {

            return PlatformImportResult(
                source =
                    source,
                rowsDetected =
                    0,
                rowsParsed =
                    0,
                rowsSkipped =
                    0,
                activities =
                    emptyList(),
                warnings =
                    listOf(
                        "The TradingView CSV contained no readable rows."
                    )
            )
        }

        val header =
            rows.first()
                .mapIndexed {
                        index,
                        value ->

                    normalizeHeader(
                        value
                    ) to index
                }
                .toMap()

        val dataRows =
            rows.drop(
                1
            )

        val parsed =
            mutableListOf<NormalizedTradeActivity>()

        val warnings =
            mutableListOf<String>()

        dataRows.forEachIndexed {
                index,
                row ->

            if (
                row.all {
                    it.isBlank()
                }
            ) {
                return@forEachIndexed
            }

            val symbol =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "symbol",
                            "ticker",
                            "instrument"
                        )
                )
                    .trim()
                    .uppercase(
                        Locale.US
                    )

            val sideText =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "side",
                            "action",
                            "direction",
                            "type"
                        )
                )
                    .trim()

            val quantity =
                parseNumber(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "qty",
                                "quantity",
                                "contracts",
                                "shares"
                            )
                    )
                )
                    ?.let {
                        abs(
                            it
                        )
                    }

            val price =
                parseMoney(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "price",
                                "fill price",
                                "filled price",
                                "execution price",
                                "avg price"
                            )
                    )
                )

            val date =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "date",
                            "time",
                            "date/time",
                            "datetime",
                            "created time",
                            "execution time"
                        )
                )
                    .trim()

            if (
                symbol.isBlank() ||
                quantity == null ||
                quantity <= 0.0 ||
                date.isBlank()
            ) {

                warnings.add(
                    "Skipped TradingView row ${index + 2}: missing symbol, date/time, or quantity."
                )

                return@forEachIndexed
            }

            val assetClass =
                detectAssetClass(
                    row =
                        row,
                    header =
                        header,
                    symbol =
                        symbol
                )

            val side =
                detectSide(
                    sideText
                )

            val accountId =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "account",
                            "account id",
                            "broker account"
                        )
                )
                    .trim()
                    .ifBlank {
                        null
                    }

            val orderId =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "order id",
                            "order",
                            "id",
                            "ticket"
                        )
                )
                    .trim()
                    .ifBlank {
                        null
                    }

            val status =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "status",
                            "order status"
                        )
                )
                    .trim()
                    .ifBlank {
                        null
                    }

            val commission =
                parseMoneySigned(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "commission",
                                "commissions"
                            )
                    )
                )
                    ?.let {
                        abs(
                            it
                        )
                    }
                    ?: 0.0

            val fees =
                parseMoneySigned(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "fee",
                                "fees"
                            )
                    )
                )
                    ?.let {
                        abs(
                            it
                        )
                    }
                    ?: 0.0

            val notes =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "notes",
                            "comment",
                            "description"
                        )
                )
                    .trim()
                    .ifBlank {
                        null
                    }

            val optionExpiration =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "expiration",
                            "expiry",
                            "expiration date"
                        )
                )
                    .trim()
                    .ifBlank {
                        null
                    }

            val optionStrike =
                parseMoney(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "strike",
                                "strike price"
                            )
                    )
                )

            val optionRight =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "option type",
                            "right",
                            "call put",
                            "call/put"
                        )
                )
                    .trim()
                    .uppercase(
                        Locale.US
                    )
                    .takeIf {
                        it ==
                                "CALL" ||
                                it ==
                                "PUT"
                    }

            val futureRoot =
                if (
                    assetClass ==
                    NormalizedAssetClass.FUTURE
                ) {

                    detectFutureRoot(
                        symbol
                    )

                } else {
                    null
                }

            val futureExpiration =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "contract expiration",
                            "futures expiration",
                            "expiry"
                        )
                )
                    .trim()
                    .ifBlank {
                        null
                    }

            val stableId =
                orderId
                    ?: UUID.nameUUIDFromBytes(
                        listOf(
                            source.name,
                            accountId.orEmpty(),
                            symbol,
                            date,
                            side.name,
                            quantity.toString(),
                            price?.toString().orEmpty()
                        )
                            .joinToString(
                                "|"
                            )
                            .toByteArray(
                                StandardCharsets.UTF_8
                            )
                    )
                        .toString()

            parsed.add(
                NormalizedTradeActivity(
                    id =
                        stableId,

                    source =
                        source,

                    accountId =
                        accountId,

                    assetClass =
                        assetClass,

                    symbol =
                        symbol,

                    underlyingSymbol =
                        if (
                            assetClass ==
                            NormalizedAssetClass.OPTION
                        ) {
                            detectOptionUnderlying(
                                symbol
                            )
                        } else {
                            null
                        },

                    side =
                        side,

                    quantity =
                        quantity,

                    price =
                        price,

                    activityDate =
                        date,

                    commission =
                        commission,

                    fees =
                        fees,

                    optionExpirationDate =
                        optionExpiration,

                    optionStrikePrice =
                        optionStrike,

                    optionRight =
                        optionRight,

                    optionContractMultiplier =
                        if (
                            assetClass ==
                            NormalizedAssetClass.OPTION
                        ) {
                            100.0
                        } else {
                            null
                        },

                    futuresRootSymbol =
                        futureRoot,

                    futuresExpirationDate =
                        futureExpiration,

                    futuresPointValue =
                        null,

                    futuresTickSize =
                        null,

                    futuresTickValue =
                        null,

                    orderId =
                        orderId,

                    status =
                        status,

                    notes =
                        notes,

                    rawDescription =
                        row.joinToString(
                            ","
                        )
                )
            )
        }

        val detectedRows =
            dataRows.count {
                    row ->
                row.any {
                    it.isNotBlank()
                }
            }

        return PlatformImportResult(
            source =
                source,

            rowsDetected =
                detectedRows,

            rowsParsed =
                parsed.size,

            rowsSkipped =
                detectedRows -
                        parsed.size,

            activities =
                parsed,

            warnings =
                warnings
        )
    }

    private fun detectAssetClass(
        row: List<String>,
        header: Map<String, Int>,
        symbol: String
    ): NormalizedAssetClass {

        val explicit =
            value(
                row =
                    row,
                header =
                    header,
                names =
                    listOf(
                        "asset class",
                        "instrument type",
                        "security type",
                        "product type",
                        "market"
                    )
            )
                .lowercase()

        if (
            explicit.contains(
                "future"
            )
        ) {
            return NormalizedAssetClass.FUTURE
        }

        if (
            explicit.contains(
                "option"
            )
        ) {
            return NormalizedAssetClass.OPTION
        }

        if (
            explicit.contains(
                "stock"
            ) ||
            explicit.contains(
                "equity"
            )
        ) {
            return NormalizedAssetClass.STOCK
        }

        val futuresPattern =
            Regex(
                """^[A-Z]{1,4}[FGHJKMNQUVXZ]\d{1,4}$"""
            )

        if (
            futuresPattern.matches(
                symbol
            )
        ) {
            return NormalizedAssetClass.FUTURE
        }

        /*
         * Basic OCC-style equity option symbol detection.
         * Example:
         * AAPL260918C00200000
         */
        val optionPattern =
            Regex(
                """^[A-Z]{1,6}\d{6}[CP]\d{8}$"""
            )

        if (
            optionPattern.matches(
                symbol.replace(
                    " ",
                    ""
                )
            )
        ) {
            return NormalizedAssetClass.OPTION
        }

        return NormalizedAssetClass.UNKNOWN
    }

    private fun detectSide(
        sideText: String
    ): NormalizedTradeSide {

        val value =
            sideText
                .trim()
                .uppercase(
                    Locale.US
                )

        return when {

            value.contains(
                "BUY TO OPEN"
            ) ||
                    value ==
                    "BTO" ->
                NormalizedTradeSide.BUY_TO_OPEN

            value.contains(
                "SELL TO CLOSE"
            ) ||
                    value ==
                    "STC" ->
                NormalizedTradeSide.SELL_TO_CLOSE

            value.contains(
                "SELL TO OPEN"
            ) ||
                    value ==
                    "STO" ->
                NormalizedTradeSide.SELL_TO_OPEN

            value.contains(
                "BUY TO CLOSE"
            ) ||
                    value ==
                    "BTC" ->
                NormalizedTradeSide.BUY_TO_CLOSE

            value.contains(
                "COVER"
            ) ->
                NormalizedTradeSide.COVER

            value.contains(
                "SHORT"
            ) ->
                NormalizedTradeSide.SHORT

            value.contains(
                "BUY"
            ) ||
                    value ==
                    "LONG" ->
                NormalizedTradeSide.BUY

            value.contains(
                "SELL"
            ) ->
                NormalizedTradeSide.SELL

            else ->
                NormalizedTradeSide.UNKNOWN
        }
    }

    private fun detectFutureRoot(
        symbol: String
    ): String {

        val letters =
            symbol.takeWhile {
                it.isLetter()
            }

        if (
            letters.isBlank()
        ) {
            return symbol
        }

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

        return if (
            letters.last() in
            monthCodes &&
            letters.length >
            1
        ) {
            letters.dropLast(
                1
            )
        } else {
            letters
        }
    }

    private fun detectOptionUnderlying(
        symbol: String
    ): String? {

        val compact =
            symbol.replace(
                " ",
                ""
            )

        val match =
            Regex(
                """^([A-Z]{1,6})\d{6}[CP]\d{8}$"""
            )
                .find(
                    compact
                )

        return match
            ?.groupValues
            ?.getOrNull(
                1
            )
    }

    private fun value(
        row: List<String>,
        header: Map<String, Int>,
        names: List<String>
    ): String {

        names.forEach {
                name ->

            val index =
                header[
                    normalizeHeader(
                        name
                    )
                ]

            if (
                index != null &&
                index in row.indices
            ) {

                return row[index]
            }
        }

        return ""
    }

    private fun normalizeHeader(
        value: String
    ): String {

        return value
            .trim()
            .lowercase()
            .replace(
                "_",
                " "
            )
            .replace(
                "-",
                " "
            )
            .replace(
                Regex(
                    """\s+"""
                ),
                " "
            )
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
                .replace(
                    "$",
                    ""
                )
                .replace(
                    "%",
                    ""
                )
                .trim()

        return cleaned
            .toDoubleOrNull()
    }

    private fun parseMoney(
        value: String
    ): Double? {

        return parseNumber(
            value
        )
            ?.let {
                abs(
                    it
                )
            }
    }

    private fun parseMoneySigned(
        value: String
    ): Double? {

        return parseNumber(
            value
        )
    }

    private fun parseCsv(
        csvText: String
    ): List<List<String>> {

        val rows =
            mutableListOf<List<String>>()

        var currentRow =
            mutableListOf<String>()

        val currentField =
            StringBuilder()

        var insideQuotes =
            false

        var index =
            0

        while (
            index <
            csvText.length
        ) {

            val char =
                csvText[index]

            when {

                char ==
                        '"' -> {

                    if (
                        insideQuotes &&
                        index + 1 <
                        csvText.length &&
                        csvText[index + 1] ==
                        '"'
                    ) {

                        currentField.append(
                            '"'
                        )

                        index++

                    } else {

                        insideQuotes =
                            !insideQuotes
                    }
                }

                char ==
                        ',' &&
                        !insideQuotes -> {

                    currentRow.add(
                        currentField
                            .toString()
                            .trim()
                    )

                    currentField.clear()
                }

                (
                        char ==
                                '\n' ||
                                char ==
                                '\r'
                        ) &&
                        !insideQuotes -> {

                    if (
                        char ==
                        '\r' &&
                        index + 1 <
                        csvText.length &&
                        csvText[index + 1] ==
                        '\n'
                    ) {
                        index++
                    }

                    currentRow.add(
                        currentField
                            .toString()
                            .trim()
                    )

                    currentField.clear()

                    if (
                        currentRow.any {
                            it.isNotBlank()
                        }
                    ) {

                        rows.add(
                            currentRow
                        )
                    }

                    currentRow =
                        mutableListOf()
                }

                else ->
                    currentField.append(
                        char
                    )
            }

            index++
        }

        if (
            currentField.isNotEmpty() ||
            currentRow.isNotEmpty()
        ) {

            currentRow.add(
                currentField
                    .toString()
                    .trim()
            )

            if (
                currentRow.any {
                    it.isNotBlank()
                }
            ) {

                rows.add(
                    currentRow
                )
            }
        }

        return rows
    }
}
