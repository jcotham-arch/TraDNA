package com.tradna.APP.data

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

object WealthChartsCsvParser : TradingPlatformImporter {

    override val source: TradingPlatformSource =
        TradingPlatformSource.WEALTHCHARTS

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
                    "created time"
                ) &&
                header.contains(
                    "order id"
                ) &&
                (
                        header.contains(
                            "filled price"
                        ) ||
                                header.contains(
                                    "fill price"
                                )
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
                        "The WealthCharts CSV contained no readable rows."
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
                            "instrument",
                            "contract"
                        )
                )
                    .trim()
                    .uppercase(
                        Locale.US
                    )

            val createdTime =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "created time",
                            "created",
                            "time",
                            "date"
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
                                "filled qty",
                                "filled quantity"
                            )
                    )
                )
                    ?.let {
                        abs(
                            it
                        )
                    }

            val filledPrice =
                parseMoney(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "filled price",
                                "fill price",
                                "avg fill price",
                                "average fill price",
                                "price"
                            )
                    )
                )

            val orderType =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "order type",
                            "type"
                        )
                )
                    .trim()

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
                            "direction"
                        )
                )
                    .trim()

            val orderId =
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "order id",
                            "orderid",
                            "id"
                        )
                )
                    .trim()
                    .ifBlank {
                        null
                    }

            if (
                symbol.isBlank() ||
                createdTime.isBlank() ||
                quantity == null ||
                quantity <= 0.0
            ) {

                warnings.add(
                    "Skipped WealthCharts row ${index + 2}: missing symbol, date/time, or quantity."
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
                    sideText =
                        sideText,
                    orderType =
                        orderType,
                    row =
                        row,
                    header =
                        header
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
                            "account number"
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

            val pnl =
                parseMoneySigned(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "pnl",
                                "p&l",
                                "profit",
                                "profit loss"
                            )
                    )
                )

            val points =
                parseNumber(
                    value(
                        row =
                            row,
                        header =
                            header,
                        names =
                            listOf(
                                "points",
                                "pts"
                            )
                    )
                )

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
                                "fees",
                                "fee"
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
                buildString {

                    if (
                        orderType.isNotBlank()
                    ) {

                        append(
                            "Order type: $orderType"
                        )
                    }

                    pnl
                        ?.let {

                            if (
                                isNotEmpty()
                            ) {
                                append(
                                    " • "
                                )
                            }

                            append(
                                "PnL: $it"
                            )
                        }

                    points
                        ?.let {

                            if (
                                isNotEmpty()
                            ) {
                                append(
                                    " • "
                                )
                            }

                            append(
                                "Points: $it"
                            )
                        }
                }
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
                            createdTime,
                            side.name,
                            quantity.toString(),
                            filledPrice?.toString().orEmpty()
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
                        null,

                    side =
                        side,

                    quantity =
                        quantity,

                    price =
                        filledPrice,

                    activityDate =
                        createdTime,

                    commission =
                        commission,

                    fees =
                        fees,

                    optionExpirationDate =
                        null,

                    optionStrikePrice =
                        null,

                    optionRight =
                        null,

                    optionContractMultiplier =
                        null,

                    futuresRootSymbol =
                        if (
                            assetClass ==
                            NormalizedAssetClass.FUTURE
                        ) {
                            detectFutureRoot(
                                symbol
                            )
                        } else {
                            null
                        },

                    futuresExpirationDate =
                        null,

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

        return PlatformImportResult(
            source =
                source,

            rowsDetected =
                dataRows.count {
                        row ->
                    row.any {
                        it.isNotBlank()
                    }
                },

            rowsParsed =
                parsed.size,

            rowsSkipped =
                dataRows.count {
                        row ->
                    row.any {
                        it.isNotBlank()
                    }
                } -
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
                        "asset",
                        "instrument type",
                        "security type",
                        "product"
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

        /*
         * Common futures symbols may appear as:
         * ESU6, MESU6, NQU6, MNQU6, CLV6, GCV6
         * or platform forms containing a futures root + month code + year.
         */
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

        return NormalizedAssetClass.UNKNOWN
    }

    private fun detectSide(
        sideText: String,
        orderType: String,
        row: List<String>,
        header: Map<String, Int>
    ): NormalizedTradeSide {

        val combined =
            listOf(
                sideText,
                orderType,
                value(
                    row =
                        row,
                    header =
                        header,
                    names =
                        listOf(
                            "position effect",
                            "open close",
                            "open/close"
                        )
                )
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
                    combined.contains(
                        "BTO"
                    ) ->
                NormalizedTradeSide.BUY_TO_OPEN

            combined.contains(
                "SELL TO CLOSE"
            ) ||
                    combined.contains(
                        "STC"
                    ) ->
                NormalizedTradeSide.SELL_TO_CLOSE

            combined.contains(
                "SELL TO OPEN"
            ) ||
                    combined.contains(
                        "STO"
                    ) ->
                NormalizedTradeSide.SELL_TO_OPEN

            combined.contains(
                "BUY TO CLOSE"
            ) ||
                    combined.contains(
                        "BTC"
                    ) ->
                NormalizedTradeSide.BUY_TO_CLOSE

            combined.contains(
                "COVER"
            ) ->
                NormalizedTradeSide.COVER

            combined.contains(
                "SHORT"
            ) ->
                NormalizedTradeSide.SHORT

            combined.contains(
                "BUY"
            ) ->
                NormalizedTradeSide.BUY

            combined.contains(
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

        return symbol
            .takeWhile {
                it.isLetter()
            }
            .dropLastWhile {
                it in
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
            }
            .ifBlank {
                symbol.takeWhile {
                    it.isLetter()
                }
            }
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
