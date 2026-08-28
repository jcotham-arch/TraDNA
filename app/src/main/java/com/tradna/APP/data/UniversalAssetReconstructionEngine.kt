package com.tradna.APP.data

data class UniversalAssetReconstructionResult(
    val source: TradingPlatformSource,
    val fileName: String,

    /*
     * Robinhood currently uses TraDNA's established stock-history
     * model and persistence pipeline.
     */
    val robinhoodActivities: List<RobinhoodActivity> =
        emptyList(),

    val robinhoodStockTrades: List<TradeEpisode> =
        emptyList(),

    /*
     * WealthCharts / TradingView stock trades use the new
     * platform-neutral stock model.
     */
    val normalizedActivities: List<NormalizedTradeActivity> =
        emptyList(),

    val normalizedStockTrades: List<NormalizedStockTradeEpisode> =
        emptyList(),

    val optionActivities: List<RawOptionActivity> =
        emptyList(),

    val optionTrades: List<OptionTradeEpisode> =
        emptyList(),

    val futuresTrades: List<FutureTradeEpisode> =
        emptyList(),

    val rowsDetected: Int = 0,
    val rowsParsed: Int = 0,
    val rowsSkipped: Int = 0,

    val warnings: List<String> =
        emptyList()
) {

    val stockTradeCount: Int
        get() =
            robinhoodStockTrades.size +
                    normalizedStockTrades.size

    val optionTradeCount: Int
        get() =
            optionTrades.size

    val futuresTradeCount: Int
        get() =
            futuresTrades.size

    val totalTradeCount: Int
        get() =
            stockTradeCount +
                    optionTradeCount +
                    futuresTradeCount

    val hasStockTrades: Boolean
        get() =
            stockTradeCount >
                    0

    val hasOptionTrades: Boolean
        get() =
            optionTradeCount >
                    0

    val hasFuturesTrades: Boolean
        get() =
            futuresTradeCount >
                    0
}

sealed interface UniversalAssetReconstructionOutcome {

    data class Success(
        val result: UniversalAssetReconstructionResult
    ) : UniversalAssetReconstructionOutcome

    data class Failure(
        val source: TradingPlatformSource,
        val fileName: String,
        val reason: String
    ) : UniversalAssetReconstructionOutcome
}

object UniversalAssetReconstructionEngine {

    fun reconstruct(
        importResult: UniversalTradeImportResult
    ): UniversalAssetReconstructionOutcome {

        return when (
            importResult
        ) {

            is UniversalTradeImportResult.Robinhood ->
                reconstructRobinhood(
                    importResult
                )

            is UniversalTradeImportResult.Normalized ->
                reconstructNormalized(
                    importResult
                )

            is UniversalTradeImportResult.Unsupported ->
                UniversalAssetReconstructionOutcome.Failure(
                    source =
                        importResult.source,

                    fileName =
                        importResult.fileName,

                    reason =
                        importResult.reason
                )
        }
    }

    private fun reconstructRobinhood(
        importResult: UniversalTradeImportResult.Robinhood
    ): UniversalAssetReconstructionOutcome {

        return try {

            val activities =
                importResult.summary.activities

            val stockTrades =
                TradeReconstructor.reconstruct(
                    activities
                )

            /*
             * Robinhood options already have a dedicated adapter
             * because the export stores expiry/right/strike inside
             * the Description column rather than the Instrument field.
             */
            val optionResult =
                RobinhoodOptionActivityParser
                    .parseAndReconstruct(
                        activities
                    )

            UniversalAssetReconstructionOutcome.Success(
                UniversalAssetReconstructionResult(
                    source =
                        TradingPlatformSource.ROBINHOOD,

                    fileName =
                        importResult.fileName,

                    robinhoodActivities =
                        activities,

                    robinhoodStockTrades =
                        stockTrades,

                    normalizedActivities =
                        emptyList(),

                    normalizedStockTrades =
                        emptyList(),

                    optionActivities =
                        optionResult.optionActivities,

                    optionTrades =
                        optionResult.optionTrades,

                    futuresTrades =
                        emptyList(),

                    rowsDetected =
                        activities.size,

                    rowsParsed =
                        activities.size -
                                optionResult.optionRowsSkipped,

                    rowsSkipped =
                        optionResult.optionRowsSkipped,

                    warnings =
                        optionResult.warnings
                )
            )

        } catch (
            error: Exception
        ) {

            UniversalAssetReconstructionOutcome.Failure(
                source =
                    TradingPlatformSource.ROBINHOOD,

                fileName =
                    importResult.fileName,

                reason =
                    error.message
                        ?: "TraDNA could not reconstruct this Robinhood trading history."
            )
        }
    }

    private fun reconstructNormalized(
        importResult: UniversalTradeImportResult.Normalized
    ): UniversalAssetReconstructionOutcome {

        return try {

            val normalized =
                importResult.result.activities

            val stockRows =
                normalized.filter {
                    it.assetClass ==
                            NormalizedAssetClass.STOCK
                }

            val optionRows =
                normalized.filter {
                    it.assetClass ==
                            NormalizedAssetClass.OPTION
                }

            val futuresRows =
                normalized.filter {
                    it.assetClass ==
                            NormalizedAssetClass.FUTURE
                }

            val unknownRows =
                normalized.filter {
                    it.assetClass ==
                            NormalizedAssetClass.UNKNOWN
                }

            val stockTrades =
                NormalizedStockTradeReconstructor
                    .reconstruct(
                        stockRows
                    )

            val optionConversion =
                NormalizedOptionActivityAdapter
                    .convert(
                        optionRows
                    )

            val optionTrades =
                OptionTradeReconstructor
                    .reconstruct(
                        optionConversion.activities
                    )

            val futuresTrades =
                FuturesTradeReconstructor
                    .reconstruct(
                        futuresRows
                    )

            val warnings =
                buildList {

                    addAll(
                        importResult.result.warnings
                    )

                    addAll(
                        optionConversion.warnings
                    )

                    if (
                        unknownRows.isNotEmpty()
                    ) {

                        add(
                            "${unknownRows.size} normalized row${if (unknownRows.size == 1) "" else "s"} could not yet be classified as stock, option, or future."
                        )
                    }
                }

            UniversalAssetReconstructionOutcome.Success(
                UniversalAssetReconstructionResult(
                    source =
                        importResult.result.source,

                    fileName =
                        importResult.fileName,

                    robinhoodActivities =
                        emptyList(),

                    robinhoodStockTrades =
                        emptyList(),

                    normalizedActivities =
                        normalized,

                    normalizedStockTrades =
                        stockTrades,

                    optionActivities =
                        optionConversion.activities,

                    optionTrades =
                        optionTrades,

                    futuresTrades =
                        futuresTrades,

                    rowsDetected =
                        importResult.result.rowsDetected,

                    rowsParsed =
                        importResult.result.rowsParsed,

                    rowsSkipped =
                        importResult.result.rowsSkipped,

                    warnings =
                        warnings
                )
            )

        } catch (
            error: Exception
        ) {

            UniversalAssetReconstructionOutcome.Failure(
                source =
                    importResult.result.source,

                fileName =
                    importResult.fileName,

                reason =
                    error.message
                        ?: "TraDNA could not reconstruct the imported trading history."
            )
        }
    }

    fun importAndReconstruct(
        csvText: String,
        fileName: String
    ): UniversalAssetReconstructionOutcome {

        val routed =
            UniversalTradeImportRouter
                .importCsv(
                    csvText =
                        csvText,
                    fileName =
                        fileName
                )

        return reconstruct(
            routed
        )
    }
}
