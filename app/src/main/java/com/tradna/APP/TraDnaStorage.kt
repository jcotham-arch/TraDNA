package com.tradna.APP.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ImportMergeResult(
    val reportRecordCount: Int,
    val newRecordCount: Int,
    val duplicateRecordCount: Int,
    val totalStoredCount: Int,
    val mergedActivities: List<RobinhoodActivity>,
    val fileName: String
)

object TraDnaStorage {

    private const val PREFS =
        "tradna_local_storage"

    private const val KEY_ACTIVITY =
        "robinhood_activity"

    private const val KEY_FILE_NAME =
        "robinhood_file_name"

    private const val KEY_LAST_IMPORT_NEW =
        "last_import_new"

    private const val KEY_LAST_IMPORT_DUPLICATES =
        "last_import_duplicates"

    private const val KEY_LAST_IMPORT_REPORT_COUNT =
        "last_import_report_count"

    /*
     * Use this only when you intentionally want to completely
     * replace all stored brokerage history.
     */
    fun saveActivities(
        context: Context,
        activities: List<RobinhoodActivity>,
        fileName: String
    ) {

        saveActivityList(
            context = context,
            activities = activities,
            fileName = fileName
        )
    }

    /*
     * DAILY / INCREMENTAL ROBINHOOD IMPORT
     *
     * Existing history is preserved.
     *
     * Incoming report rows are compared against existing
     * brokerage activity.
     *
     * Overlapping records are ignored.
     *
     * Only genuinely new activity is added.
     */
    fun mergeActivities(
        context: Context,
        incomingActivities: List<RobinhoodActivity>,
        fileName: String
    ): ImportMergeResult {

        val existing =
            loadActivities(context)

        /*
         * Robinhood can theoretically contain two genuinely
         * separate executions whose exported values are identical.
         *
         * Therefore we do NOT simply throw every fingerprint
         * into a Set.
         *
         * Instead we treat duplicate rows as a multiset and
         * preserve the maximum occurrence count found in either
         * report.
         *
         * Example:
         *
         * Old report contains 2 identical fills.
         * New overlapping report contains 2 identical fills.
         *
         * Result remains 2, not 4 and not 1.
         */
        val existingCounts =
            existing
                .groupingBy {
                    fingerprint(it)
                }
                .eachCount()

        val incomingCounts =
            incomingActivities
                .groupingBy {
                    fingerprint(it)
                }
                .eachCount()

        val merged =
            incomingActivities
                .toMutableList()

        val mergedCounts =
            incomingCounts
                .toMutableMap()

        /*
         * Determine the desired number of occurrences
         * for every fingerprint.
         */
        val fingerprints =
            (
                    existingCounts.keys +
                            incomingCounts.keys
                    )
                .toSet()

        val desiredCounts =
            fingerprints.associateWith {
                    key ->

                maxOf(
                    existingCounts[key]
                        ?: 0,

                    incomingCounts[key]
                        ?: 0
                )
            }

        /*
         * Incoming report stays first because Robinhood exports
         * are normally newest-first.
         *
         * Older records that are not represented in the new
         * report are appended afterward.
         *
         * This preserves the ordering expected by the existing
         * TradeReconstructor.
         */
        existing.forEach {
                activity ->

            val key =
                fingerprint(activity)

            val currentCount =
                mergedCounts[key]
                    ?: 0

            val desiredCount =
                desiredCounts[key]
                    ?: 0

            if (
                currentCount <
                desiredCount
            ) {

                merged.add(
                    activity
                )

                mergedCounts[key] =
                    currentCount + 1
            }
        }

        /*
         * Number of genuinely new brokerage rows.
         *
         * We compare occurrence counts rather than only unique
         * fingerprints so legitimate identical executions remain
         * possible.
         */
        var newRecordCount =
            0

        incomingCounts.forEach {
                (key, incomingCount) ->

            val existingCount =
                existingCounts[key]
                    ?: 0

            if (
                incomingCount >
                existingCount
            ) {

                newRecordCount +=
                    incomingCount -
                            existingCount
            }
        }

        val duplicateRecordCount =
            (
                    incomingActivities.size -
                            newRecordCount
                    )
                .coerceAtLeast(0)

        saveActivityList(
            context = context,
            activities = merged,
            fileName = fileName
        )

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putInt(
                KEY_LAST_IMPORT_NEW,
                newRecordCount
            )
            .putInt(
                KEY_LAST_IMPORT_DUPLICATES,
                duplicateRecordCount
            )
            .putInt(
                KEY_LAST_IMPORT_REPORT_COUNT,
                incomingActivities.size
            )
            .apply()

        return ImportMergeResult(
            reportRecordCount =
                incomingActivities.size,

            newRecordCount =
                newRecordCount,

            duplicateRecordCount =
                duplicateRecordCount,

            totalStoredCount =
                merged.size,

            mergedActivities =
                merged,

            fileName =
                fileName
        )
    }

    fun loadActivities(
        context: Context
    ): List<RobinhoodActivity> {

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val raw =
            prefs.getString(
                KEY_ACTIVITY,
                null
            )
                ?: return emptyList()

        return try {

            val array =
                JSONArray(raw)

            buildList {

                for (
                index in
                0 until array.length()
                ) {

                    val json =
                        array.getJSONObject(
                            index
                        )

                    add(
                        RobinhoodActivity(
                            activityDate =
                                json.optString(
                                    "activityDate"
                                ),

                            processDate =
                                json.optString(
                                    "processDate"
                                ),

                            settleDate =
                                json.optString(
                                    "settleDate"
                                ),

                            instrument =
                                json.optString(
                                    "instrument"
                                ),

                            description =
                                json.optString(
                                    "description"
                                ),

                            transCode =
                                json.optString(
                                    "transCode"
                                ),

                            quantity =
                                json.optString(
                                    "quantity"
                                ),

                            price =
                                json.optString(
                                    "price"
                                ),

                            amount =
                                json.optString(
                                    "amount"
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

    fun loadFileName(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_FILE_NAME,
                "Robinhood CSV"
            )
            ?: "Robinhood CSV"
    }

    fun loadLastImportNewCount(
        context: Context
    ): Int {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getInt(
                KEY_LAST_IMPORT_NEW,
                0
            )
    }

    fun loadLastImportDuplicateCount(
        context: Context
    ): Int {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getInt(
                KEY_LAST_IMPORT_DUPLICATES,
                0
            )
    }

    fun loadLastImportReportCount(
        context: Context
    ): Int {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getInt(
                KEY_LAST_IMPORT_REPORT_COUNT,
                0
            )
    }

    /*
     * This clears ALL locally stored Robinhood history.
     *
     * We will eventually put this behind a confirmation
     * dialog in Settings/Data Management.
     */
    fun clear(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .apply()
    }

    private fun saveActivityList(
        context: Context,
        activities: List<RobinhoodActivity>,
        fileName: String
    ) {

        val array =
            JSONArray()

        activities.forEach {
                activity ->

            val json =
                JSONObject()

            json.put(
                "activityDate",
                activity.activityDate
            )

            json.put(
                "processDate",
                activity.processDate
            )

            json.put(
                "settleDate",
                activity.settleDate
            )

            json.put(
                "instrument",
                activity.instrument
            )

            json.put(
                "description",
                activity.description
            )

            json.put(
                "transCode",
                activity.transCode
            )

            json.put(
                "quantity",
                activity.quantity
            )

            json.put(
                "price",
                activity.price
            )

            json.put(
                "amount",
                activity.amount
            )

            array.put(
                json
            )
        }

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_ACTIVITY,
                array.toString()
            )
            .putString(
                KEY_FILE_NAME,
                fileName
            )
            .apply()
    }

    /*
     * Stable brokerage-activity fingerprint.
     *
     * Whitespace and case are normalized so harmless formatting
     * differences don't create fake new transactions.
     */
    private fun fingerprint(
        activity: RobinhoodActivity
    ): String {

        fun normalize(
            value: String
        ): String {

            return value
                .trim()
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .uppercase()
        }

        return listOf(
            normalize(
                activity.activityDate
            ),

            normalize(
                activity.processDate
            ),

            normalize(
                activity.settleDate
            ),

            normalize(
                activity.instrument
            ),

            normalize(
                activity.description
            ),

            normalize(
                activity.transCode
            ),

            normalize(
                activity.quantity
            ),

            normalize(
                activity.price
            ),

            normalize(
                activity.amount
            )
        )
            .joinToString(
                separator = "|"
            )
    }
}