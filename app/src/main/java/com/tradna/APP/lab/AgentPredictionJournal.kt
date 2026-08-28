package com.tradna.APP.lab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentPredictionRecord(
    val id: String,
    val createdAtEpochMillis: Long,
    val symbol: String,
    val decision: String,
    val overallScore: Int,
    val confidencePercent: Int,
    val technicalScore: Int,
    val historyMatchScore: Int,
    val entryQualityScore: Int,
    val riskQualityScore: Int,
    val evidenceConfidenceScore: Int,
    val marketPrice: Double,
    val vwap: Double?,
    val ema9: Double?,
    val ema20: Double?,
    val relativeVolume: Double?,
    val distanceFromVwapPercent: Double?,
    val currentSignals: List<String>,
    val matchedTradeCount: Int,
    val historicalProfitableRatePercent: Double?,
    val historicalAverageReturnPercent: Double?,
    val historicalAverageMfePercent: Double?,
    val historicalAverageMaePercent: Double?,
    val preferredEntryMethod: String,
    val preferredExitMethod: String,
    val strengths: List<String>,
    val warnings: List<String>,
    val reasoning: List<String>,
    val proposedStopPrice: Double?,
    val proposedTargetPrice: Double?,
    val outcomeLinked: Boolean = false,
    val linkedTradeId: String? = null,
    val actualEntryPrice: Double? = null,
    val actualExitPrice: Double? = null,
    val actualReturnPercent: Double? = null,
    val actualRealizedPnl: Double? = null,
    val predictionDirectionCorrect: Boolean? = null,
    val agentEntryBetterThanActual: Boolean? = null,
    val agentExitBetterThanActual: Boolean? = null,
    val schemaVersion: Int = 1
)

object AgentPredictionJournal {

    private const val PREFS = "tradna_agent_prediction_journal"
    private const val KEY_RECORDS = "prediction_records"

    fun createRecord(
        symbol: String,
        result: AgentTradeDecisionResult,
        snapshot: com.tradna.APP.market.TechnicalSnapshot,
        proposedStopPrice: Double?,
        proposedTargetPrice: Double?
    ): AgentPredictionRecord {

        return AgentPredictionRecord(
            id = UUID.randomUUID().toString(),
            createdAtEpochMillis = System.currentTimeMillis(),
            symbol = symbol,
            decision = result.decision.name,
            overallScore = result.overallScore,
            confidencePercent = result.confidencePercent,
            technicalScore = result.scoreBreakdown.technicalScore,
            historyMatchScore = result.scoreBreakdown.historyMatchScore,
            entryQualityScore = result.scoreBreakdown.entryQualityScore,
            riskQualityScore = result.scoreBreakdown.riskQualityScore,
            evidenceConfidenceScore = result.scoreBreakdown.evidenceConfidenceScore,
            marketPrice = snapshot.price,
            vwap = snapshot.vwap,
            ema9 = snapshot.ema9,
            ema20 = snapshot.ema20,
            relativeVolume = snapshot.volumeRatio,
            distanceFromVwapPercent = snapshot.distanceFromVwapPercent,
            currentSignals = snapshot.signals,
            matchedTradeCount = result.matchedTradeCount,
            historicalProfitableRatePercent = result.historicalProfitableRatePercent,
            historicalAverageReturnPercent = result.historicalAverageReturnPercent,
            historicalAverageMfePercent = result.historicalAverageMfePercent,
            historicalAverageMaePercent = result.historicalAverageMaePercent,
            preferredEntryMethod = result.preferredEntryMethod,
            preferredExitMethod = result.preferredExitMethod,
            strengths = result.strengths,
            warnings = result.warnings,
            reasoning = result.reasoning,
            proposedStopPrice = proposedStopPrice,
            proposedTargetPrice = proposedTargetPrice
        )
    }

    fun savePrediction(
        context: Context,
        record: AgentPredictionRecord
    ) {
        val records = loadPredictions(context).toMutableList()
        records.add(0, record)
        saveAll(context, records)
    }

    fun loadPredictions(
        context: Context
    ): List<AgentPredictionRecord> {

        val raw =
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_RECORDS,
                    null
                )
                ?: return emptyList()

        return try {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    add(
                        jsonToRecord(
                            array.getJSONObject(index)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun predictionCount(
        context: Context
    ): Int =
        loadPredictions(context).size

    fun linkedOutcomeCount(
        context: Context
    ): Int =
        loadPredictions(context)
            .count {
                it.outcomeLinked
            }

    fun updatePrediction(
        context: Context,
        updated: AgentPredictionRecord
    ) {
        val records = loadPredictions(context).toMutableList()

        val index =
            records.indexOfFirst {
                it.id == updated.id
            }

        if (index >= 0) {
            records[index] = updated
            saveAll(context, records)
        }
    }

    fun clear(
        context: Context
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_RECORDS)
            .apply()
    }

    private fun saveAll(
        context: Context,
        records: List<AgentPredictionRecord>
    ) {
        val array = JSONArray()

        records.forEach {
            array.put(
                recordToJson(it)
            )
        }

        context
            .getSharedPreferences(
                PREFS,
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
        record: AgentPredictionRecord
    ): JSONObject {
        return JSONObject()
            .put("id", record.id)
            .put("createdAtEpochMillis", record.createdAtEpochMillis)
            .put("symbol", record.symbol)
            .put("decision", record.decision)
            .put("overallScore", record.overallScore)
            .put("confidencePercent", record.confidencePercent)
            .put("technicalScore", record.technicalScore)
            .put("historyMatchScore", record.historyMatchScore)
            .put("entryQualityScore", record.entryQualityScore)
            .put("riskQualityScore", record.riskQualityScore)
            .put("evidenceConfidenceScore", record.evidenceConfidenceScore)
            .put("marketPrice", record.marketPrice)
            .putNullable("vwap", record.vwap)
            .putNullable("ema9", record.ema9)
            .putNullable("ema20", record.ema20)
            .putNullable("relativeVolume", record.relativeVolume)
            .putNullable("distanceFromVwapPercent", record.distanceFromVwapPercent)
            .put("currentSignals", stringListToJson(record.currentSignals))
            .put("matchedTradeCount", record.matchedTradeCount)
            .putNullable("historicalProfitableRatePercent", record.historicalProfitableRatePercent)
            .putNullable("historicalAverageReturnPercent", record.historicalAverageReturnPercent)
            .putNullable("historicalAverageMfePercent", record.historicalAverageMfePercent)
            .putNullable("historicalAverageMaePercent", record.historicalAverageMaePercent)
            .put("preferredEntryMethod", record.preferredEntryMethod)
            .put("preferredExitMethod", record.preferredExitMethod)
            .put("strengths", stringListToJson(record.strengths))
            .put("warnings", stringListToJson(record.warnings))
            .put("reasoning", stringListToJson(record.reasoning))
            .putNullable("proposedStopPrice", record.proposedStopPrice)
            .putNullable("proposedTargetPrice", record.proposedTargetPrice)
            .put("outcomeLinked", record.outcomeLinked)
            .putNullable("linkedTradeId", record.linkedTradeId)
            .putNullable("actualEntryPrice", record.actualEntryPrice)
            .putNullable("actualExitPrice", record.actualExitPrice)
            .putNullable("actualReturnPercent", record.actualReturnPercent)
            .putNullable("actualRealizedPnl", record.actualRealizedPnl)
            .putNullable("predictionDirectionCorrect", record.predictionDirectionCorrect)
            .putNullable("agentEntryBetterThanActual", record.agentEntryBetterThanActual)
            .putNullable("agentExitBetterThanActual", record.agentExitBetterThanActual)
            .put("schemaVersion", record.schemaVersion)
    }

    private fun jsonToRecord(
        json: JSONObject
    ): AgentPredictionRecord {
        return AgentPredictionRecord(
            id = json.optString("id"),
            createdAtEpochMillis = json.optLong("createdAtEpochMillis", 0L),
            symbol = json.optString("symbol"),
            decision = json.optString("decision"),
            overallScore = json.optInt("overallScore", 0),
            confidencePercent = json.optInt("confidencePercent", 0),
            technicalScore = json.optInt("technicalScore", 0),
            historyMatchScore = json.optInt("historyMatchScore", 0),
            entryQualityScore = json.optInt("entryQualityScore", 0),
            riskQualityScore = json.optInt("riskQualityScore", 0),
            evidenceConfidenceScore = json.optInt("evidenceConfidenceScore", 0),
            marketPrice = json.optDouble("marketPrice", 0.0),
            vwap = json.optNullableDouble("vwap"),
            ema9 = json.optNullableDouble("ema9"),
            ema20 = json.optNullableDouble("ema20"),
            relativeVolume = json.optNullableDouble("relativeVolume"),
            distanceFromVwapPercent = json.optNullableDouble("distanceFromVwapPercent"),
            currentSignals = json.optStringList("currentSignals"),
            matchedTradeCount = json.optInt("matchedTradeCount", 0),
            historicalProfitableRatePercent = json.optNullableDouble("historicalProfitableRatePercent"),
            historicalAverageReturnPercent = json.optNullableDouble("historicalAverageReturnPercent"),
            historicalAverageMfePercent = json.optNullableDouble("historicalAverageMfePercent"),
            historicalAverageMaePercent = json.optNullableDouble("historicalAverageMaePercent"),
            preferredEntryMethod = json.optString("preferredEntryMethod"),
            preferredExitMethod = json.optString("preferredExitMethod"),
            strengths = json.optStringList("strengths"),
            warnings = json.optStringList("warnings"),
            reasoning = json.optStringList("reasoning"),
            proposedStopPrice = json.optNullableDouble("proposedStopPrice"),
            proposedTargetPrice = json.optNullableDouble("proposedTargetPrice"),
            outcomeLinked = json.optBoolean("outcomeLinked", false),
            linkedTradeId = json.optNullableString("linkedTradeId"),
            actualEntryPrice = json.optNullableDouble("actualEntryPrice"),
            actualExitPrice = json.optNullableDouble("actualExitPrice"),
            actualReturnPercent = json.optNullableDouble("actualReturnPercent"),
            actualRealizedPnl = json.optNullableDouble("actualRealizedPnl"),
            predictionDirectionCorrect = json.optNullableBoolean("predictionDirectionCorrect"),
            agentEntryBetterThanActual = json.optNullableBoolean("agentEntryBetterThanActual"),
            agentExitBetterThanActual = json.optNullableBoolean("agentExitBetterThanActual"),
            schemaVersion = json.optInt("schemaVersion", 1)
        )
    }

    private fun stringListToJson(
        values: List<String>
    ): JSONArray {
        val array = JSONArray()

        values.forEach {
            array.put(it)
        }

        return array
    }

    private fun JSONObject.optStringList(
        key: String
    ): List<String> {
        val array = optJSONArray(key)
            ?: return emptyList()

        return buildList {
            for (index in 0 until array.length()) {
                add(array.optString(index))
            }
        }
    }

    private fun JSONObject.putNullable(
        key: String,
        value: Any?
    ): JSONObject {
        if (value == null) {
            put(key, JSONObject.NULL)
        } else {
            put(key, value)
        }

        return this
    }

    private fun JSONObject.optNullableString(
        key: String
    ): String? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return optString(key)
    }

    private fun JSONObject.optNullableDouble(
        key: String
    ): Double? {
        if (!has(key) || isNull(key)) {
            return null
        }

        val value = optDouble(key, Double.NaN)

        return if (value.isNaN()) {
            null
        } else {
            value
        }
    }

    private fun JSONObject.optNullableBoolean(
        key: String
    ): Boolean? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return optBoolean(key)
    }
}
