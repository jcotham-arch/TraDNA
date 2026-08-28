package com.tradna.APP.replay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ReplayStorage {

    private const val PREFS_NAME =
        "tradna_replay_storage"

    private const val KEY_DECISIONS =
        "replay_decisions"

    fun saveDecision(
        context: Context,
        decision: ReplayDecision
    ) {

        val existing =
            loadAllDecisions(context)
                .toMutableList()

        existing.add(decision)

        saveAll(
            context = context,
            decisions = existing
        )
    }

    fun loadAllDecisions(
        context: Context
    ): List<ReplayDecision> {

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val raw =
            prefs.getString(
                KEY_DECISIONS,
                null
            ) ?: return emptyList()

        return try {

            val array =
                JSONArray(raw)

            buildList {

                for (
                index in 0 until array.length()
                ) {

                    val json =
                        array.getJSONObject(
                            index
                        )

                    add(
                        ReplayDecision(
                            tradeId =
                                json.optString(
                                    "tradeId"
                                ),

                            symbol =
                                json.optString(
                                    "symbol"
                                ),

                            candleNumber =
                                json.optInt(
                                    "candleNumber"
                                ),

                            timestamp =
                                json.optString(
                                    "timestamp"
                                ),

                            marketPrice =
                                json.optDouble(
                                    "marketPrice"
                                ),

                            choice =
                                ReplayChoice.valueOf(
                                    json.optString(
                                        "choice",
                                        "WAIT"
                                    )
                                ),

                            confidence =
                                json.optInt(
                                    "confidence"
                                ),

                            setup =
                                json.optString(
                                    "setup"
                                ),

                            plannedEntry =
                                json.optNullableDouble(
                                    "plannedEntry"
                                ),

                            plannedStop =
                                json.optNullableDouble(
                                    "plannedStop"
                                ),

                            plannedTarget =
                                json.optNullableDouble(
                                    "plannedTarget"
                                )
                        )
                    )
                }
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    fun loadForTrade(
        context: Context,
        tradeId: String
    ): List<ReplayDecision> {

        return loadAllDecisions(
            context
        ).filter {
            it.tradeId == tradeId
        }
    }

    fun clearTrade(
        context: Context,
        tradeId: String
    ) {

        val remaining =
            loadAllDecisions(
                context
            ).filterNot {
                it.tradeId == tradeId
            }

        saveAll(
            context = context,
            decisions = remaining
        )
    }

    private fun saveAll(
        context: Context,
        decisions: List<ReplayDecision>
    ) {

        val array =
            JSONArray()

        decisions.forEach {
                decision ->

            val json =
                JSONObject()

            json.put(
                "tradeId",
                decision.tradeId
            )

            json.put(
                "symbol",
                decision.symbol
            )

            json.put(
                "candleNumber",
                decision.candleNumber
            )

            json.put(
                "timestamp",
                decision.timestamp
            )

            json.put(
                "marketPrice",
                decision.marketPrice
            )

            json.put(
                "choice",
                decision.choice.name
            )

            json.put(
                "confidence",
                decision.confidence
            )

            json.put(
                "setup",
                decision.setup
            )

            if (
                decision.plannedEntry != null
            ) {

                json.put(
                    "plannedEntry",
                    decision.plannedEntry
                )
            }

            if (
                decision.plannedStop != null
            ) {

                json.put(
                    "plannedStop",
                    decision.plannedStop
                )
            }

            if (
                decision.plannedTarget != null
            ) {

                json.put(
                    "plannedTarget",
                    decision.plannedTarget
                )
            }

            array.put(json)
        }

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_DECISIONS,
                array.toString()
            )
            .apply()
    }
}

private fun JSONObject.optNullableDouble(
    key: String
): Double? {

    if (!has(key)) {
        return null
    }

    if (isNull(key)) {
        return null
    }

    return optDouble(key)
}