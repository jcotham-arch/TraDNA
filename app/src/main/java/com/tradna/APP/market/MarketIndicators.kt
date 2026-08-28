package com.tradna.APP.market

object MarketIndicators {

    fun ema(
        candles: List<Candle>,
        period: Int
    ): List<Double?> {

        if (candles.isEmpty() || period <= 0) {
            return emptyList()
        }

        val result =
            MutableList<Double?>(
                candles.size
            ) { null }

        if (candles.size < period) {
            return result
        }

        val initialAverage =
            candles
                .take(period)
                .map { it.close }
                .average()

        result[period - 1] =
            initialAverage

        val multiplier =
            2.0 / (period + 1.0)

        var previous =
            initialAverage

        for (
        index in period until candles.size
        ) {

            val current =
                (
                        candles[index].close -
                                previous
                        ) *
                        multiplier +
                        previous

            result[index] =
                current

            previous =
                current
        }

        return result
    }

    fun vwap(
        candles: List<Candle>
    ): List<Double?> {

        if (candles.isEmpty()) {
            return emptyList()
        }

        val result =
            MutableList<Double?>(
                candles.size
            ) { null }

        var cumulativeVolume =
            0.0

        var cumulativePriceVolume =
            0.0

        var currentSession =
            ""

        candles.forEachIndexed {
                index,
                candle ->

            val session =
                candle.timestamp
                    .take(10)

            if (
                session != currentSession
            ) {

                currentSession =
                    session

                cumulativeVolume =
                    0.0

                cumulativePriceVolume =
                    0.0
            }

            val typicalPrice =
                (
                        candle.high +
                                candle.low +
                                candle.close
                        ) / 3.0

            cumulativeVolume +=
                candle.volume.toDouble()

            cumulativePriceVolume +=
                typicalPrice *
                        candle.volume.toDouble()

            if (
                cumulativeVolume > 0.0
            ) {

                result[index] =
                    cumulativePriceVolume /
                            cumulativeVolume
            }
        }

        return result
    }
}
