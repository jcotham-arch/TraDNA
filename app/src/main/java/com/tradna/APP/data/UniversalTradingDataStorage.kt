package com.tradna.APP.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NormalizedImportMergeResult(
    val source: TradingPlatformSource,
    val fileName: String,
    val incomingRecordCount: Int,
    val newRecordCount: Int,
    val duplicateRecordCount: Int,
    val totalStoredCount: Int,
    val mergedActivities: List<NormalizedTradeActivity>
)

object UniversalTradingDataStorage {

    private const val PREFS_NAME =
        "tradna_universal_trading_history"

    private const val KEY_ACTIVITIES =
        "normalized_activities"

    private const val KEY_FILE_NAME =
        "last_file_name"

    private const val KEY_SOURCE =
        "last_source"

    fun loadActivities(
        context: Context
    ): List<NormalizedTradeActivity> {

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val json =
            prefs.getString(
                KEY_ACTIVITIES,
                null
            )
                ?: return emptyList()

        return try {

            val array =
                JSONArray(
                    json
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

                    parseActivity(
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

    fun loadFileName(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_FILE_NAME,
                ""
            )
            .orEmpty()
    }

    fun loadLastSource(
        context: Context
    ): TradingPlatformSource? {

        val raw =
            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_SOURCE,
                    null
                )
                ?: return null

        return enumValueOrNull<TradingPlatformSource>(
            raw
        )
    }

    fun mergeActivities(
        context: Context,
        incomingActivities: List<NormalizedTradeActivity>,
        source: TradingPlatformSource,
        fileName: String
    ): NormalizedImportMergeResult {

        val existing =
            loadActivities(
                context
            )

        val existingIds =
            existing
                .map {
                    it.id
                }
                .toMutableSet()

        val newActivities =
            incomingActivities.filter {
                existingIds.add(
                    it.id
                )
            }

        val merged =
            (
                    existing +
                            newActivities
                    )
                .distinctBy {
                    it.id
                }

        saveActivities(
            context =
                context,
            activities =
                merged,
            source =
                source,
            fileName =
                fileName
        )

        return NormalizedImportMergeResult(
            source =
                source,

            fileName =
                fileName,

            incomingRecordCount =
                incomingActivities.size,

            newRecordCount =
                newActivities.size,

            duplicateRecordCount =
                incomingActivities.size -
                        newActivities.size,

            totalStoredCount =
                merged.size,

            mergedActivities =
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

    private fun saveActivities(
        context: Context,
        activities: List<NormalizedTradeActivity>,
        source: TradingPlatformSource,
        fileName: String
    ) {

        val array =
            JSONArray()

        activities.forEach {
                activity ->

            array.put(
                activityToJson(
                    activity
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
                KEY_ACTIVITIES,
                array.toString()
            )
            .putString(
                KEY_FILE_NAME,
                fileName
            )
            .putString(
                KEY_SOURCE,
                source.name
            )
            .apply()
    }

    private fun activityToJson(
        activity: NormalizedTradeActivity
    ): JSONObject {

        return JSONObject()
            .put(
                "id",
                activity.id
            )
            .put(
                "source",
                activity.source.name
            )
            .putNullable(
                "accountId",
                activity.accountId
            )
            .put(
                "assetClass",
                activity.assetClass.name
            )
            .put(
                "symbol",
                activity.symbol
            )
            .putNullable(
                "underlyingSymbol",
                activity.underlyingSymbol
            )
            .put(
                "side",
                activity.side.name
            )
            .put(
                "quantity",
                activity.quantity
            )
            .putNullable(
                "price",
                activity.price
            )
            .put(
                "activityDate",
                activity.activityDate
            )
            .put(
                "commission",
                activity.commission
            )
            .put(
                "fees",
                activity.fees
            )
            .putNullable(
                "optionExpirationDate",
                activity.optionExpirationDate
            )
            .putNullable(
                "optionStrikePrice",
                activity.optionStrikePrice
            )
            .putNullable(
                "optionRight",
                activity.optionRight
            )
            .putNullable(
                "optionContractMultiplier",
                activity.optionContractMultiplier
            )
            .putNullable(
                "futuresRootSymbol",
                activity.futuresRootSymbol
            )
            .putNullable(
                "futuresExpirationDate",
                activity.futuresExpirationDate
            )
            .putNullable(
                "futuresPointValue",
                activity.futuresPointValue
            )
            .putNullable(
                "futuresTickSize",
                activity.futuresTickSize
            )
            .putNullable(
                "futuresTickValue",
                activity.futuresTickValue
            )
            .putNullable(
                "orderId",
                activity.orderId
            )
            .putNullable(
                "status",
                activity.status
            )
            .putNullable(
                "notes",
                activity.notes
            )
            .putNullable(
                "rawDescription",
                activity.rawDescription
            )
    }

    private fun parseActivity(
        item: JSONObject
    ): NormalizedTradeActivity? {

        val id =
            item.optString(
                "id"
            )
                .takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val source =
            enumValueOrNull<TradingPlatformSource>(
                item.optString(
                    "source"
                )
            )
                ?: TradingPlatformSource.GENERIC_CSV

        val assetClass =
            enumValueOrNull<NormalizedAssetClass>(
                item.optString(
                    "assetClass"
                )
            )
                ?: NormalizedAssetClass.UNKNOWN

        val side =
            enumValueOrNull<NormalizedTradeSide>(
                item.optString(
                    "side"
                )
            )
                ?: NormalizedTradeSide.UNKNOWN

        val symbol =
            item.optString(
                "symbol"
            )

        val activityDate =
            item.optString(
                "activityDate"
            )

        if (
            symbol.isBlank() ||
            activityDate.isBlank()
        ) {
            return null
        }

        return NormalizedTradeActivity(
            id =
                id,

            source =
                source,

            accountId =
                item.optNullableString(
                    "accountId"
                ),

            assetClass =
                assetClass,

            symbol =
                symbol,

            underlyingSymbol =
                item.optNullableString(
                    "underlyingSymbol"
                ),

            side =
                side,

            quantity =
                item.optDouble(
                    "quantity",
                    0.0
                ),

            price =
                item.optNullableDouble(
                    "price"
                ),

            activityDate =
                activityDate,

            commission =
                item.optDouble(
                    "commission",
                    0.0
                ),

            fees =
                item.optDouble(
                    "fees",
                    0.0
                ),

            optionExpirationDate =
                item.optNullableString(
                    "optionExpirationDate"
                ),

            optionStrikePrice =
                item.optNullableDouble(
                    "optionStrikePrice"
                ),

            optionRight =
                item.optNullableString(
                    "optionRight"
                ),

            optionContractMultiplier =
                item.optNullableDouble(
                    "optionContractMultiplier"
                ),

            futuresRootSymbol =
                item.optNullableString(
                    "futuresRootSymbol"
                ),

            futuresExpirationDate =
                item.optNullableString(
                    "futuresExpirationDate"
                ),

            futuresPointValue =
                item.optNullableDouble(
                    "futuresPointValue"
                ),

            futuresTickSize =
                item.optNullableDouble(
                    "futuresTickSize"
                ),

            futuresTickValue =
                item.optNullableDouble(
                    "futuresTickValue"
                ),

            orderId =
                item.optNullableString(
                    "orderId"
                ),

            status =
                item.optNullableString(
                    "status"
                ),

            notes =
                item.optNullableString(
                    "notes"
                ),

            rawDescription =
                item.optNullableString(
                    "rawDescription"
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

    private inline fun <reified T : Enum<T>> enumValueOrNull(
        value: String
    ): T? {

        return enumValues<T>()
            .firstOrNull {
                it.name ==
                        value
            }
    }
}
