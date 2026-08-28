package com.tradna.APP.lab

import android.content.Context
import com.tradna.APP.data.MultiAssetTradeEpisode
import com.tradna.APP.data.TraDnaAssetType
import com.tradna.APP.market.AlpacaMarketData
import com.tradna.APP.market.TechnicalSignalEngine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class OptionContextTrainingRecord(
    val tradeId: String,
    val contractSymbol: String,
    val underlyingSymbol: String,

    val openDate: String,
    val closeDate: String?,

    val expirationDate: String?,
    val strikePrice: Double?,
    val optionRight: String?,
    val direction: String,

    val averageEntryPremium: Double,
    val averageExitPremium: Double?,
    val realizedPnl: Double,
    val profitableTrade: Boolean,

    /*
     * This market snapshot represents the UNDERLYING stock context
     * around the option entry. It is deliberately not described as
     * option-contract candle data.
     */
    val contextTimeframe: String,
    val candleCount: Int,

    val underlyingPrice: Double,
    val technicalScore: Int,

    val vwap: Double?,
    val distanceFromVwapPercent: Double?,
    val ema9: Double?,
    val ema20: Double?,
    val relativeVolume: Double?,

    val signals: List<String>,

    val trainedAtEpochMillis: Long
)

data class OptionContextTrainingAttempt(
    val success: Boolean,
    val record: OptionContextTrainingRecord? = null,
    val reason: String? = null,
    val candleCount: Int = 0,
    val timeframe: String? = null
)

object OptionContextTrainingStorage {

    private const val PREFS_NAME =
        "tradna_option_context_training"

    private const val KEY_RECORDS =
        "records"

    fun loadRecords(
        context: Context
    ): List<OptionContextTrainingRecord> {

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val raw =
            prefs.getString(
                KEY_RECORDS,
                null
            )
                ?: return emptyList()

        return try {

            val array =
                JSONArray(
                    raw
                )

            buildList {

                for (
                index in
                0 until array.length()
                ) {

                    val item =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    parseRecord(
                        item
                    )
                        ?.let {
                            add(
                                it
                            )
                        }
                }
            }

        } catch (
            _: Exception
        ) {
            emptyList()
        }
    }

    fun containsTrade(
        context: Context,
        tradeId: String
    ): Boolean {

        return loadRecords(
            context
        )
            .any {
                it.tradeId ==
                        tradeId
            }
    }

    fun saveRecord(
        context: Context,
        record: OptionContextTrainingRecord
    ) {

        val existing =
            loadRecords(
                context
            )

        val merged =
            (
                    existing
                        .filterNot {
                            it.tradeId ==
                                    record.tradeId
                        } +
                            record
                    )
                .sortedBy {
                    it.openDate
                }

        saveRecords(
            context =
                context,
            records =
                merged
        )
    }

    fun clear(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .apply()
    }

    private fun saveRecords(
        context: Context,
        records: List<OptionContextTrainingRecord>
    ) {

        val array =
            JSONArray()

        records.forEach {
                record ->

            array.put(
                recordToJson(
                    record
                )
            )
        }

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_RECORDS,
                array.toString()
            )
            .apply()
    }

    private fun recordToJson(
        record: OptionContextTrainingRecord
    ): JSONObject {

        return JSONObject()
            .put(
                "tradeId",
                record.tradeId
            )
            .put(
                "contractSymbol",
                record.contractSymbol
            )
            .put(
                "underlyingSymbol",
                record.underlyingSymbol
            )
            .put(
                "openDate",
                record.openDate
            )
            .putNullable(
                "closeDate",
                record.closeDate
            )
            .putNullable(
                "expirationDate",
                record.expirationDate
            )
            .putNullable(
                "strikePrice",
                record.strikePrice
            )
            .putNullable(
                "optionRight",
                record.optionRight
            )
            .put(
                "direction",
                record.direction
            )
            .put(
                "averageEntryPremium",
                record.averageEntryPremium
            )
            .putNullable(
                "averageExitPremium",
                record.averageExitPremium
            )
            .put(
                "realizedPnl",
                record.realizedPnl
            )
            .put(
                "profitableTrade",
                record.profitableTrade
            )
            .put(
                "contextTimeframe",
                record.contextTimeframe
            )
            .put(
                "candleCount",
                record.candleCount
            )
            .put(
                "underlyingPrice",
                record.underlyingPrice
            )
            .put(
                "technicalScore",
                record.technicalScore
            )
            .putNullable(
                "vwap",
                record.vwap
            )
            .putNullable(
                "distanceFromVwapPercent",
                record.distanceFromVwapPercent
            )
            .putNullable(
                "ema9",
                record.ema9
            )
            .putNullable(
                "ema20",
                record.ema20
            )
            .putNullable(
                "relativeVolume",
                record.relativeVolume
            )
            .put(
                "signals",
                JSONArray(
                    record.signals
                )
            )
            .put(
                "trainedAtEpochMillis",
                record.trainedAtEpochMillis
            )
    }

    private fun parseRecord(
        item: JSONObject
    ): OptionContextTrainingRecord? {

        val tradeId =
            item.optString(
                "tradeId"
            )
                .takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val contractSymbol =
            item.optString(
                "contractSymbol"
            )
                .takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val underlyingSymbol =
            item.optString(
                "underlyingSymbol"
            )
                .takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val openDate =
            item.optString(
                "openDate"
            )
                .takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val signalsArray =
            item.optJSONArray(
                "signals"
            )

        val signals =
            buildList {

                if (
                    signalsArray !=
                    null
                ) {

                    for (
                    index in
                    0 until signalsArray.length()
                    ) {

                        signalsArray
                            .optString(
                                index
                            )
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                add(
                                    it
                                )
                            }
                    }
                }
            }

        return OptionContextTrainingRecord(
            tradeId =
                tradeId,

            contractSymbol =
                contractSymbol,

            underlyingSymbol =
                underlyingSymbol,

            openDate =
                openDate,

            closeDate =
                item.optNullableString(
                    "closeDate"
                ),

            expirationDate =
                item.optNullableString(
                    "expirationDate"
                ),

            strikePrice =
                item.optNullableDouble(
                    "strikePrice"
                ),

            optionRight =
                item.optNullableString(
                    "optionRight"
                ),

            direction =
                item.optString(
                    "direction",
                    "UNKNOWN"
                ),

            averageEntryPremium =
                item.optDouble(
                    "averageEntryPremium",
                    0.0
                ),

            averageExitPremium =
                item.optNullableDouble(
                    "averageExitPremium"
                ),

            realizedPnl =
                item.optDouble(
                    "realizedPnl",
                    0.0
                ),

            profitableTrade =
                item.optBoolean(
                    "profitableTrade",
                    false
                ),

            contextTimeframe =
                item.optString(
                    "contextTimeframe",
                    "15Min"
                ),

            candleCount =
                item.optInt(
                    "candleCount",
                    0
                ),

            underlyingPrice =
                item.optDouble(
                    "underlyingPrice",
                    0.0
                ),

            technicalScore =
                item.optInt(
                    "technicalScore",
                    0
                ),

            vwap =
                item.optNullableDouble(
                    "vwap"
                ),

            distanceFromVwapPercent =
                item.optNullableDouble(
                    "distanceFromVwapPercent"
                ),

            ema9 =
                item.optNullableDouble(
                    "ema9"
                ),

            ema20 =
                item.optNullableDouble(
                    "ema20"
                ),

            relativeVolume =
                item.optNullableDouble(
                    "relativeVolume"
                ),

            signals =
                signals,

            trainedAtEpochMillis =
                item.optLong(
                    "trainedAtEpochMillis",
                    0L
                )
        )
    }

    private fun JSONObject.putNullable(
        key: String,
        value: Any?
    ): JSONObject {

        put(
            key,
            value ?: JSONObject.NULL
        )

        return this
    }

    private fun JSONObject.optNullableString(
        key: String
    ): String? {

        if (
            !has(
                key
            ) ||
            isNull(
                key
            )
        ) {
            return null
        }

        return optString(
            key
        )
            .takeIf {
                it.isNotBlank()
            }
    }

    private fun JSONObject.optNullableDouble(
        key: String
    ): Double? {

        if (
            !has(
                key
            ) ||
            isNull(
                key
            )
        ) {
            return null
        }

        val value =
            optDouble(
                key,
                Double.NaN
            )

        return value
            .takeUnless {
                it.isNaN()
            }
    }
}

object OptionContextTrainingEngine {

    private const val CONTEXT_TIMEFRAME =
        "15Min"

    private const val MAX_CONTEXT_CANDLES =
        220

    /*
     * Analyze one historical OPTION trade using the underlying stock's
     * market context around the option entry.
     *
     * This deliberately DOES NOT place the resulting record inside the
     * existing stock AgentTrainingStorage. The schemas remain separate
     * until TraDNA's pattern/scoring engines become asset-aware.
     */
    suspend fun analyzeAndStore(
        context: Context,
        trade: MultiAssetTradeEpisode
    ): OptionContextTrainingAttempt {

        if (
            trade.assetType !=
            TraDnaAssetType.OPTION
        ) {

            return OptionContextTrainingAttempt(
                success =
                    false,
                reason =
                    "${trade.symbol} is not an option trade."
            )
        }

        if (
            OptionContextTrainingStorage
                .containsTrade(
                    context =
                        context,
                    tradeId =
                        trade.id
                )
        ) {

            return OptionContextTrainingAttempt(
                success =
                    true,
                record =
                    OptionContextTrainingStorage
                        .loadRecords(
                            context
                        )
                        .firstOrNull {
                            it.tradeId ==
                                    trade.id
                        },
                reason =
                    "This option trade already has a saved underlying-context record."
            )
        }

        val underlying =
            trade.underlyingSymbol
                ?.trim()
                ?.uppercase(
                    Locale.US
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: trade.analysisSymbol
                    .trim()
                    .uppercase(
                        Locale.US
                    )
                    .takeIf {
                        it.isNotBlank()
                    }

        if (
            underlying ==
            null
        ) {

            return OptionContextTrainingAttempt(
                success =
                    false,
                reason =
                    "${trade.symbol}: no underlying stock symbol is available."
            )
        }

        val openDate =
            parseTradeDate(
                trade.openDate
            )
                ?: return OptionContextTrainingAttempt(
                    success =
                        false,
                    reason =
                        "${trade.symbol}: TraDNA could not parse the option open date '${trade.openDate}'."
                )

        val start =
            openDate
                .minusDays(
                    7
                )
                .atStartOfDay(
                    ZoneOffset.UTC
                )
                .toInstant()
                .toString()

        /*
         * End one day after entry so the final available candles include
         * the entry session. This is entry-context analysis, not full
         * option-contract price reconstruction.
         */
        val end =
            openDate
                .plusDays(
                    1
                )
                .atStartOfDay(
                    ZoneOffset.UTC
                )
                .toInstant()
                .toString()

        return try {

            val loadedCandles =
                AlpacaMarketData
                    .getBars(
                        symbol =
                            underlying,
                        start =
                            start,
                        end =
                            end,
                        timeframe =
                            CONTEXT_TIMEFRAME
                    )

            if (
                loadedCandles
                    .isEmpty()
            ) {

                OptionContextTrainingAttempt(
                    success =
                        false,
                    reason =
                        "$underlying: no underlying-stock candles were returned around ${trade.openDate}.",
                    candleCount =
                        0,
                    timeframe =
                        CONTEXT_TIMEFRAME
                )

            } else {

                val analysisCandles =
                    loadedCandles
                        .takeLast(
                            MAX_CONTEXT_CANDLES
                        )

                val snapshot =
                    TechnicalSignalEngine
                        .analyze(
                            analysisCandles
                        )

                if (
                    snapshot ==
                    null
                ) {

                    OptionContextTrainingAttempt(
                        success =
                            false,
                        reason =
                            "$underlying: TraDNA could not create a technical snapshot around the option entry.",
                        candleCount =
                            analysisCandles.size,
                        timeframe =
                            CONTEXT_TIMEFRAME
                    )

                } else {

                    val record =
                        OptionContextTrainingRecord(
                            tradeId =
                                trade.id,

                            contractSymbol =
                                trade.symbol,

                            underlyingSymbol =
                                underlying,

                            openDate =
                                trade.openDate,

                            closeDate =
                                trade.closeDate,

                            expirationDate =
                                trade.expirationDate,

                            strikePrice =
                                trade.strikePrice,

                            optionRight =
                                trade.optionRight,

                            direction =
                                trade.direction.name,

                            averageEntryPremium =
                                trade.averageEntryPrice,

                            averageExitPremium =
                                trade.averageExitPrice,

                            realizedPnl =
                                trade.realizedPnl,

                            profitableTrade =
                                trade.realizedPnl >
                                        0.0,

                            contextTimeframe =
                                CONTEXT_TIMEFRAME,

                            candleCount =
                                analysisCandles.size,

                            underlyingPrice =
                                snapshot.price,

                            technicalScore =
                                snapshot.technicalScore,

                            vwap =
                                snapshot.vwap,

                            distanceFromVwapPercent =
                                snapshot.distanceFromVwapPercent,

                            ema9 =
                                snapshot.ema9,

                            ema20 =
                                snapshot.ema20,

                            relativeVolume =
                                snapshot.volumeRatio,

                            signals =
                                snapshot.signals,

                            trainedAtEpochMillis =
                                System.currentTimeMillis()
                        )

                    OptionContextTrainingStorage
                        .saveRecord(
                            context =
                                context,
                            record =
                                record
                        )

                    OptionContextTrainingAttempt(
                        success =
                            true,
                        record =
                            record,
                        candleCount =
                            analysisCandles.size,
                        timeframe =
                            CONTEXT_TIMEFRAME
                    )
                }
            }

        } catch (
            error: Exception
        ) {

            OptionContextTrainingAttempt(
                success =
                    false,
                reason =
                    error.message
                        ?: "$underlying: unable to reconstruct underlying-stock context for this option trade.",
                timeframe =
                    CONTEXT_TIMEFRAME
            )
        }
    }

    fun untrainedOptionTrades(
        context: Context,
        trades: List<MultiAssetTradeEpisode>
    ): List<MultiAssetTradeEpisode> {

        val trainedIds =
            OptionContextTrainingStorage
                .loadRecords(
                    context
                )
                .map {
                    it.tradeId
                }
                .toSet()

        return trades
            .filter {
                it.assetType ==
                        TraDnaAssetType.OPTION &&
                        it.id !in
                        trainedIds
            }
    }

    private fun parseTradeDate(
        rawValue: String
    ): LocalDate? {

        val value =
            rawValue
                .trim()

        if (
            value.isBlank()
        ) {
            return null
        }

        /*
         * ISO-like exports:
         * 2026-08-20
         * 2026-08-20 09:30:00
         * 2026-08-20T09:30:00Z
         */
        if (
            value.length >=
            10 &&
            value[4] ==
            '-' &&
            value[7] ==
            '-'
        ) {

            try {

                return LocalDate
                    .parse(
                        value.substring(
                            0,
                            10
                        ),
                        DateTimeFormatter.ISO_LOCAL_DATE
                    )

            } catch (
                _: DateTimeParseException
            ) {
                // Continue through broker-style formats below.
            }
        }

        val dateOnly =
            value
                .substringBefore(
                    ' '
                )
                .substringBefore(
                    'T'
                )

        val formatters =
            listOf(
                DateTimeFormatter.ofPattern(
                    "M/d/yyyy",
                    Locale.US
                ),
                DateTimeFormatter.ofPattern(
                    "MM/dd/yyyy",
                    Locale.US
                ),
                DateTimeFormatter.ofPattern(
                    "M/d/yy",
                    Locale.US
                ),
                DateTimeFormatter.ofPattern(
                    "MM/dd/yy",
                    Locale.US
                )
            )

        formatters.forEach {
                formatter ->

            try {

                return LocalDate
                    .parse(
                        dateOnly,
                        formatter
                    )

            } catch (
                _: DateTimeParseException
            ) {
                // Try the next supported format.
            }
        }

        return null
    }
}
