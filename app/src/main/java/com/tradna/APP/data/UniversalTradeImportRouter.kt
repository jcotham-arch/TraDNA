package com.tradna.APP.data

sealed interface UniversalTradeImportResult {

    val source: TradingPlatformSource
    val fileName: String

    data class Robinhood(
        override val fileName: String,
        val summary: ImportSummary
    ) : UniversalTradeImportResult {

        override val source: TradingPlatformSource =
            TradingPlatformSource.ROBINHOOD
    }

    data class Normalized(
        override val fileName: String,
        val result: PlatformImportResult
    ) : UniversalTradeImportResult {

        override val source: TradingPlatformSource =
            result.source
    }

    data class Unsupported(
        override val fileName: String,
        val reason: String
    ) : UniversalTradeImportResult {

        override val source: TradingPlatformSource =
            TradingPlatformSource.GENERIC_CSV
    }
}

object UniversalTradeImportRouter {

    fun importCsv(
        csvText: String,
        fileName: String
    ): UniversalTradeImportResult {

        if (
            csvText.isBlank()
        ) {

            return UniversalTradeImportResult.Unsupported(
                fileName =
                    fileName,
                reason =
                    "The selected trading-history file is empty."
            )
        }

        /*
         * First use the lightweight header detector.
         */
        val detectedSource =
            TradingPlatformDetector.detect(
                csvText
            )

        when (
            detectedSource
        ) {

            TradingPlatformSource.ROBINHOOD -> {

                return parseRobinhood(
                    csvText =
                        csvText,
                    fileName =
                        fileName
                )
            }

            TradingPlatformSource.WEALTHCHARTS -> {

                return parseNormalized(
                    importer =
                        WealthChartsCsvParser,
                    csvText =
                        csvText,
                    fileName =
                        fileName
                )
            }

            TradingPlatformSource.TRADINGVIEW -> {

                return parseNormalized(
                    importer =
                        TradingViewCsvParser,
                    csvText =
                        csvText,
                    fileName =
                        fileName
                )
            }

            TradingPlatformSource.GENERIC_CSV -> {
                /*
                 * Continue into fallback probing below.
                 */
            }
        }

        /*
         * Fallback probing protects us from harmless column-order or
         * export-format changes that may prevent the first detector
         * from identifying a platform.
         */
        if (
            WealthChartsCsvParser.canParse(
                csvText
            )
        ) {

            return parseNormalized(
                importer =
                    WealthChartsCsvParser,
                csvText =
                    csvText,
                fileName =
                    fileName
            )
        }

        if (
            TradingViewCsvParser.canParse(
                csvText
            )
        ) {

            return parseNormalized(
                importer =
                    TradingViewCsvParser,
                csvText =
                    csvText,
                fileName =
                    fileName
            )
        }

        if (
            looksLikeRobinhood(
                csvText
            )
        ) {

            return parseRobinhood(
                csvText =
                    csvText,
                fileName =
                    fileName
            )
        }

        return UniversalTradeImportResult.Unsupported(
            fileName =
                fileName,
            reason =
                "TraDNA could not identify this CSV as a supported Robinhood, WealthCharts, or TradingView trading-history export."
        )
    }

    fun detectSource(
        csvText: String
    ): TradingPlatformSource {

        val primary =
            TradingPlatformDetector.detect(
                csvText
            )

        if (
            primary !=
            TradingPlatformSource.GENERIC_CSV
        ) {
            return primary
        }

        return when {

            WealthChartsCsvParser.canParse(
                csvText
            ) ->
                TradingPlatformSource.WEALTHCHARTS

            TradingViewCsvParser.canParse(
                csvText
            ) ->
                TradingPlatformSource.TRADINGVIEW

            looksLikeRobinhood(
                csvText
            ) ->
                TradingPlatformSource.ROBINHOOD

            else ->
                TradingPlatformSource.GENERIC_CSV
        }
    }

    private fun parseRobinhood(
        csvText: String,
        fileName: String
    ): UniversalTradeImportResult {

        return try {

            val summary =
                RobinhoodCsvParser.parse(
                    csvText,
                    fileName
                )

            UniversalTradeImportResult.Robinhood(
                fileName =
                    fileName,
                summary =
                    summary
            )

        } catch (
            error: Exception
        ) {

            UniversalTradeImportResult.Unsupported(
                fileName =
                    fileName,
                reason =
                    error.message
                        ?: "TraDNA recognized a Robinhood export but could not parse it."
            )
        }
    }

    private fun parseNormalized(
        importer: TradingPlatformImporter,
        csvText: String,
        fileName: String
    ): UniversalTradeImportResult {

        return try {

            val result =
                importer.parse(
                    csvText
                )

            if (
                result.rowsDetected >
                0 &&
                result.rowsParsed ==
                0
            ) {

                UniversalTradeImportResult.Unsupported(
                    fileName =
                        fileName,
                    reason =
                        buildString {

                            append(
                                "TraDNA recognized this as a "
                            )

                            append(
                                platformName(
                                    importer.source
                                )
                            )

                            append(
                                " export, but none of its trading rows could be normalized."
                            )

                            result.warnings
                                .firstOrNull()
                                ?.let {
                                        warning ->

                                    append(
                                        " "
                                    )

                                    append(
                                        warning
                                    )
                                }
                        }
                )

            } else {

                UniversalTradeImportResult.Normalized(
                    fileName =
                        fileName,
                    result =
                        result
                )
            }

        } catch (
            error: Exception
        ) {

            UniversalTradeImportResult.Unsupported(
                fileName =
                    fileName,
                reason =
                    buildString {

                        append(
                            "TraDNA recognized a "
                        )

                        append(
                            platformName(
                                importer.source
                            )
                        )

                        append(
                            " export but could not parse it."
                        )

                        error.message
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                    message ->

                                append(
                                    " "
                                )

                                append(
                                    message
                                )
                            }
                    }
            )
        }
    }

    private fun looksLikeRobinhood(
        csvText: String
    ): Boolean {

        val header =
            csvText
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?.lowercase()
                ?: return false

        return header.contains(
            "activity date"
        ) &&
                header.contains(
                    "trans code"
                ) &&
                header.contains(
                    "instrument"
                ) &&
                header.contains(
                    "description"
                )
    }

    fun platformName(
        source: TradingPlatformSource
    ): String {

        return when (
            source
        ) {

            TradingPlatformSource.ROBINHOOD ->
                "Robinhood"

            TradingPlatformSource.TRADINGVIEW ->
                "TradingView"

            TradingPlatformSource.WEALTHCHARTS ->
                "WealthCharts"

            TradingPlatformSource.GENERIC_CSV ->
                "Generic CSV"
        }
    }
}
