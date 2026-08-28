package com.tradna.APP.market

import com.tradna.APP.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AlpacaMarketData {

    suspend fun getBars(
        symbol: String,
        start: String,
        end: String,
        timeframe: String = "5Min"
    ): List<Candle> {

        return withContext(Dispatchers.IO) {

            if (BuildConfig.ALPACA_API_KEY.isBlank()) {
                throw IllegalStateException(
                    "Alpaca API key is missing."
                )
            }

            if (BuildConfig.ALPACA_SECRET_KEY.isBlank()) {
                throw IllegalStateException(
                    "Alpaca secret key is missing."
                )
            }

            val encodedSymbol =
                URLEncoder.encode(
                    symbol,
                    "UTF-8"
                )

            val requestUrl =
                "https://data.alpaca.markets/v2/stocks/" +
                        "$encodedSymbol/bars" +
                        "?timeframe=$timeframe" +
                        "&start=$start" +
                        "&end=$end" +
                        "&feed=iex" +
                        "&adjustment=all" +
                        "&limit=10000"

            val connection =
                URL(requestUrl)
                    .openConnection()
                        as HttpURLConnection

            try {

                connection.requestMethod =
                    "GET"

                connection.setRequestProperty(
                    "APCA-API-KEY-ID",
                    BuildConfig.ALPACA_API_KEY
                )

                connection.setRequestProperty(
                    "APCA-API-SECRET-KEY",
                    BuildConfig.ALPACA_SECRET_KEY
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                connection.connectTimeout =
                    15_000

                connection.readTimeout =
                    15_000

                val responseCode =
                    connection.responseCode

                val responseText =
                    if (responseCode in 200..299) {

                        connection
                            .inputStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    } else {

                        connection
                            .errorStream
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }
                            ?: "Unknown API error"
                    }

                if (responseCode !in 200..299) {

                    throw IllegalStateException(
                        "Market data error $responseCode: $responseText"
                    )
                }

                val root =
                    JSONObject(
                        responseText
                    )

                val bars =
                    root.optJSONArray(
                        "bars"
                    )
                        ?: return@withContext emptyList()

                buildList {

                    for (
                    index in 0 until bars.length()
                    ) {

                        val bar =
                            bars.getJSONObject(
                                index
                            )

                        add(
                            Candle(
                                timestamp =
                                    bar.getString("t"),

                                open =
                                    bar.getDouble("o"),

                                high =
                                    bar.getDouble("h"),

                                low =
                                    bar.getDouble("l"),

                                close =
                                    bar.getDouble("c"),

                                volume =
                                    bar.getLong("v")
                            )
                        )
                    }
                }

            } finally {

                connection.disconnect()
            }
        }
    }
}

