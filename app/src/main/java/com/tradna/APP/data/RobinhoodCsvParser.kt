package com.tradna.APP.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RobinhoodCsvParser {

    fun parse(
        csvText: String,
        fileName: String
    ): ImportSummary {

        val rows = parseCsvRows(csvText)

        if (rows.isEmpty()) {
            throw IllegalArgumentException(
                "This file contains no CSV data."
            )
        }

        val header = rows.first().map {
            it.trim().removePrefix("\uFEFF")
        }

        val expected = listOf(
            "Activity Date",
            "Process Date",
            "Settle Date",
            "Instrument",
            "Description",
            "Trans Code",
            "Quantity",
            "Price",
            "Amount"
        )

        if (!header.containsAll(expected)) {
            throw IllegalArgumentException(
                "This does not appear to be a supported Robinhood Account Activity CSV."
            )
        }

        val indexMap = header
            .mapIndexed { index, value ->
                value to index
            }
            .toMap()

        val activities = rows
            .drop(1)
            .mapNotNull { row ->

                fun value(column: String): String {
                    val position = indexMap[column] ?: return ""
                    return row.getOrNull(position)?.trim() ?: ""
                }

                val activityDate = value("Activity Date")
                val transCode = value("Trans Code")

                // Filters blank rows and Robinhood disclaimer rows.
                if (
                    activityDate.isBlank() ||
                    transCode.isBlank() ||
                    parseDate(activityDate) == null
                ) {
                    return@mapNotNull null
                }

                RobinhoodActivity(
                    activityDate = activityDate,
                    processDate = value("Process Date"),
                    settleDate = value("Settle Date"),
                    instrument = value("Instrument"),
                    description = value("Description"),
                    transCode = transCode,
                    quantity = value("Quantity"),
                    price = value("Price"),
                    amount = value("Amount")
                )
            }

        if (activities.isEmpty()) {
            throw IllegalArgumentException(
                "No brokerage activity records were found."
            )
        }

        val buyCount = activities.count {
            it.transCode.equals("Buy", ignoreCase = true)
        }

        val sellCount = activities.count {
            it.transCode.equals("Sell", ignoreCase = true)
        }

        val optionCodes = setOf(
            "STO",
            "BTC",
            "BTO",
            "STC",
            "OEXP",
            "OASGN"
        )

        val optionCount = activities.count {
            it.transCode.uppercase() in optionCodes
        }

        val instrumentCount = activities
            .map { it.instrument }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        val dates = activities
            .mapNotNull {
                parseDate(it.activityDate)
            }

        return ImportSummary(
            activities = activities,
            activityCount = activities.size,
            buyCount = buyCount,
            sellCount = sellCount,
            optionCount = optionCount,
            instrumentCount = instrumentCount,
            startDate = dates.minOrNull()
                ?.let(::formatDate)
                ?: "Unknown",
            endDate = dates.maxOrNull()
                ?.let(::formatDate)
                ?: "Unknown",
            fileName = fileName
        )
    }

    private fun parseDate(value: String): Date? {
        return try {
            SimpleDateFormat(
                "M/d/yyyy",
                Locale.US
            ).apply {
                isLenient = false
            }.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatDate(date: Date): String {
        return SimpleDateFormat(
            "MMM d, yyyy",
            Locale.US
        ).format(date)
    }

    private fun parseCsvRows(
        text: String
    ): List<List<String>> {

        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()

        var insideQuotes = false
        var index = 0

        while (index < text.length) {

            val char = text[index]

            when {

                char == '"' -> {

                    if (
                        insideQuotes &&
                        index + 1 < text.length &&
                        text[index + 1] == '"'
                    ) {
                        currentField.append('"')
                        index++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }

                char == ',' && !insideQuotes -> {

                    currentRow.add(
                        currentField.toString()
                    )

                    currentField.clear()
                }

                char == '\n' && !insideQuotes -> {

                    currentRow.add(
                        currentField
                            .toString()
                            .trimEnd('\r')
                    )

                    currentField.clear()

                    if (
                        currentRow.any {
                            it.isNotBlank()
                        }
                    ) {
                        rows.add(
                            currentRow.toList()
                        )
                    }

                    currentRow.clear()
                }

                else -> {
                    currentField.append(char)
                }
            }

            index++
        }

        if (
            currentField.isNotEmpty() ||
            currentRow.isNotEmpty()
        ) {

            currentRow.add(
                currentField.toString()
            )

            if (
                currentRow.any {
                    it.isNotBlank()
                }
            ) {
                rows.add(
                    currentRow.toList()
                )
            }
        }

        return rows
    }
}

