package com.tradna.APP.lab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AgentTrainingStorage {

    private const val PREFS =
        "tradna_agent_lab"

    private const val KEY_RECORDS =
        "agent_training_records"

    /*
     * Save or update one historical training record.
     *
     * Trade reviews can be regenerated as our analysis
     * improves, so records are replaced by ID rather than
     * blindly duplicated.
     */
    fun saveRecord(
        context: Context,
        record: AgentTrainingRecord
    ) {

        val existing =
            loadRecords(
                context
            )
                .toMutableList()

        val existingIndex =
            existing.indexOfFirst {
                it.id ==
                        record.id
            }

        if (
            existingIndex >= 0
        ) {

            existing[
                existingIndex
            ] =
                record

        } else {

            existing.add(
                record
            )
        }

        saveAll(
            context =
                context,
            records =
                existing
        )
    }

    fun loadRecords(
        context: Context
    ): List<AgentTrainingRecord> {

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

            val array =
                JSONArray(
                    raw
                )

            buildList {

                for (
                index in
                0 until array.length()
                ) {

                    add(
                        jsonToRecord(
                            array.getJSONObject(
                                index
                            )
                        )
                    )
                }
            }

        } catch (
            _: Exception
        ) {

            emptyList()
        }
    }

    fun recordCount(
        context: Context
    ): Int {

        return loadRecords(
            context
        )
            .size
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
            .remove(
                KEY_RECORDS
            )
            .apply()
    }

    private fun saveAll(
        context: Context,
        records: List<AgentTrainingRecord>
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
        record: AgentTrainingRecord
    ): JSONObject {

        return JSONObject()
            .put(
                "id",
                record.id
            )
            .put(
                "tradeId",
                record.tradeId
            )
            .put(
                "symbol",
                record.symbol
            )
            .put(
                "openDate",
                record.openDate
            )
            .putNullable(
                "closeDate",
                record.closeDate
            )
            .put(
                "actualEntryPrice",
                record.actualEntryPrice
            )
            .putNullable(
                "actualExitPrice",
                record.actualExitPrice
            )
            .putNullable(
                "actualReturnPercent",
                record.actualReturnPercent
            )
            .put(
                "actualRealizedPnl",
                record.actualRealizedPnl
            )
            .putNullable(
                "entryTechnicalScore",
                record.entryTechnicalScore
            )
            .putNullable(
                "entryVwap",
                record.entryVwap
            )
            .putNullable(
                "entryEma9",
                record.entryEma9
            )
            .putNullable(
                "entryEma20",
                record.entryEma20
            )
            .putNullable(
                "entryRelativeVolume",
                record.entryRelativeVolume
            )
            .putNullable(
                "entryDistanceFromVwapPercent",
                record.entryDistanceFromVwapPercent
            )
            .put(
                "entrySignals",
                stringListToJson(
                    record.entrySignals
                )
            )
            .putNullable(
                "entryEfficiencyScore",
                record.entryEfficiencyScore
            )
            .putNullable(
                "exitEfficiencyScore",
                record.exitEfficiencyScore
            )
            .putNullable(
                "totalEfficiencyScore",
                record.totalEfficiencyScore
            )
            .putNullable(
                "maximumFavorableExcursionPercent",
                record.maximumFavorableExcursionPercent
            )
            .putNullable(
                "maximumAdverseExcursionPercent",
                record.maximumAdverseExcursionPercent
            )
            .putNullable(
                "missedUpsidePercent",
                record.missedUpsidePercent
            )
            .putNullable(
                "bestAlternativeId",
                record.bestAlternativeId
            )
            .putNullable(
                "bestAlternativeTitle",
                record.bestAlternativeTitle
            )
            .putNullable(
                "bestAlternativeReturnPercent",
                record.bestAlternativeReturnPercent
            )
            .putNullable(
                "bestAlternativeEstimatedPnl",
                record.bestAlternativeEstimatedPnl
            )
            .putNullable(
                "improvementVsActualPercent",
                record.improvementVsActualPercent
            )
            .putNullable(
                "improvementVsActualDollars",
                record.improvementVsActualDollars
            )
            .put(
                "strengths",
                stringListToJson(
                    record.strengths
                )
            )
            .put(
                "weaknesses",
                stringListToJson(
                    record.weaknesses
                )
            )
            .put(
                "recommendations",
                stringListToJson(
                    record.recommendations
                )
            )
            .put(
                "lessonTitle",
                record.lessonTitle
            )
            .put(
                "lessonSummary",
                record.lessonSummary
            )
            .put(
                "profitableTrade",
                record.profitableTrade
            )
            .put(
                "strongTechnicalEntry",
                record.strongTechnicalEntry
            )
            .put(
                "efficientEntry",
                record.efficientEntry
            )
            .put(
                "efficientExit",
                record.efficientExit
            )
            .put(
                "earlyExitCandidate",
                record.earlyExitCandidate
            )
            .put(
                "highAdverseExcursion",
                record.highAdverseExcursion
            )
            .put(
                "alternativeOutperformedActual",
                record.alternativeOutperformedActual
            )
            .put(
                "schemaVersion",
                record.schemaVersion
            )
    }

    private fun jsonToRecord(
        json: JSONObject
    ): AgentTrainingRecord {

        return AgentTrainingRecord(

            id =
                json.optString(
                    "id"
                ),

            tradeId =
                json.optString(
                    "tradeId"
                ),

            symbol =
                json.optString(
                    "symbol"
                ),

            openDate =
                json.optString(
                    "openDate"
                ),

            closeDate =
                json.optNullableString(
                    "closeDate"
                ),

            actualEntryPrice =
                json.optDouble(
                    "actualEntryPrice",
                    0.0
                ),

            actualExitPrice =
                json.optNullableDouble(
                    "actualExitPrice"
                ),

            actualReturnPercent =
                json.optNullableDouble(
                    "actualReturnPercent"
                ),

            actualRealizedPnl =
                json.optDouble(
                    "actualRealizedPnl",
                    0.0
                ),

            entryTechnicalScore =
                json.optNullableInt(
                    "entryTechnicalScore"
                ),

            entryVwap =
                json.optNullableDouble(
                    "entryVwap"
                ),

            entryEma9 =
                json.optNullableDouble(
                    "entryEma9"
                ),

            entryEma20 =
                json.optNullableDouble(
                    "entryEma20"
                ),

            entryRelativeVolume =
                json.optNullableDouble(
                    "entryRelativeVolume"
                ),

            entryDistanceFromVwapPercent =
                json.optNullableDouble(
                    "entryDistanceFromVwapPercent"
                ),

            entrySignals =
                json.optStringList(
                    "entrySignals"
                ),

            entryEfficiencyScore =
                json.optNullableInt(
                    "entryEfficiencyScore"
                ),

            exitEfficiencyScore =
                json.optNullableInt(
                    "exitEfficiencyScore"
                ),

            totalEfficiencyScore =
                json.optNullableInt(
                    "totalEfficiencyScore"
                ),

            maximumFavorableExcursionPercent =
                json.optNullableDouble(
                    "maximumFavorableExcursionPercent"
                ),

            maximumAdverseExcursionPercent =
                json.optNullableDouble(
                    "maximumAdverseExcursionPercent"
                ),

            missedUpsidePercent =
                json.optNullableDouble(
                    "missedUpsidePercent"
                ),

            bestAlternativeId =
                json.optNullableString(
                    "bestAlternativeId"
                ),

            bestAlternativeTitle =
                json.optNullableString(
                    "bestAlternativeTitle"
                ),

            bestAlternativeReturnPercent =
                json.optNullableDouble(
                    "bestAlternativeReturnPercent"
                ),

            bestAlternativeEstimatedPnl =
                json.optNullableDouble(
                    "bestAlternativeEstimatedPnl"
                ),

            improvementVsActualPercent =
                json.optNullableDouble(
                    "improvementVsActualPercent"
                ),

            improvementVsActualDollars =
                json.optNullableDouble(
                    "improvementVsActualDollars"
                ),

            strengths =
                json.optStringList(
                    "strengths"
                ),

            weaknesses =
                json.optStringList(
                    "weaknesses"
                ),

            recommendations =
                json.optStringList(
                    "recommendations"
                ),

            lessonTitle =
                json.optString(
                    "lessonTitle"
                ),

            lessonSummary =
                json.optString(
                    "lessonSummary"
                ),

            profitableTrade =
                json.optBoolean(
                    "profitableTrade",
                    false
                ),

            strongTechnicalEntry =
                json.optBoolean(
                    "strongTechnicalEntry",
                    false
                ),

            efficientEntry =
                json.optBoolean(
                    "efficientEntry",
                    false
                ),

            efficientExit =
                json.optBoolean(
                    "efficientExit",
                    false
                ),

            earlyExitCandidate =
                json.optBoolean(
                    "earlyExitCandidate",
                    false
                ),

            highAdverseExcursion =
                json.optBoolean(
                    "highAdverseExcursion",
                    false
                ),

            alternativeOutperformedActual =
                json.optBoolean(
                    "alternativeOutperformedActual",
                    false
                ),

            schemaVersion =
                json.optInt(
                    "schemaVersion",
                    1
                )
        )
    }

    private fun stringListToJson(
        values: List<String>
    ): JSONArray {

        val array =
            JSONArray()

        values.forEach {
            array.put(it)
        }

        return array
    }

    private fun JSONObject.optStringList(
        key: String
    ): List<String> {

        val array =
            optJSONArray(
                key
            )
                ?: return emptyList()

        return buildList {

            for (
            index in
            0 until array.length()
            ) {

                add(
                    array.optString(
                        index
                    )
                )
            }
        }
    }

    private fun JSONObject.putNullable(
        key: String,
        value: Any?
    ): JSONObject {

        if (
            value == null
        ) {

            put(
                key,
                JSONObject.NULL
            )

        } else {

            put(
                key,
                value
            )
        }

        return this
    }

    private fun JSONObject.optNullableString(
        key: String
    ): String? {

        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optString(
            key
        )
    }

    private fun JSONObject.optNullableDouble(
        key: String
    ): Double? {

        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        val value =
            optDouble(
                key,
                Double.NaN
            )

        return if (
            value.isNaN()
        ) {
            null
        } else {
            value
        }
    }

    private fun JSONObject.optNullableInt(
        key: String
    ): Int? {

        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optInt(
            key
        )
    }
}