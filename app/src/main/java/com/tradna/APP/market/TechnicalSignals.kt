package com.tradna.APP.market

import kotlin.math.abs

data class TechnicalSnapshot(
    val timestamp: String,
    val price: Double,

    val ema9: Double?,
    val ema20: Double?,
    val vwap: Double?,

    val aboveEma9: Boolean?,
    val aboveEma20: Boolean?,
    val aboveVwap: Boolean?,

    val volumeRatio: Double?,

    val breakout: Boolean,
    val failedBreakout: Boolean,

    val bullishLiquiditySweep: Boolean,
    val bearishLiquiditySweep: Boolean,

    val bullishStructure: Boolean,
    val bearishStructure: Boolean,

    val distanceFromVwapPercent: Double?,

    val technicalScore: Int,

    val signals: List<String>
)

object TechnicalSignalEngine {

    fun analyze(
        candles: List<Candle>
    ): TechnicalSnapshot? {

        if (candles.size < 5) {
            return null
        }

        val currentIndex =
            candles.lastIndex

        val current =
            candles[currentIndex]

        val ema9Series =
            MarketIndicators.ema(
                candles = candles,
                period = 9
            )

        val ema20Series =
            MarketIndicators.ema(
                candles = candles,
                period = 20
            )

        val vwapSeries =
            MarketIndicators.vwap(
                candles
            )

        val ema9 =
            ema9Series
                .getOrNull(currentIndex)

        val ema20 =
            ema20Series
                .getOrNull(currentIndex)

        val vwap =
            vwapSeries
                .getOrNull(currentIndex)

        val aboveEma9 =
            ema9?.let {
                current.close > it
            }

        val aboveEma20 =
            ema20?.let {
                current.close > it
            }

        val aboveVwap =
            vwap?.let {
                current.close > it
            }

        val volumeRatio =
            calculateVolumeRatio(
                candles = candles,
                currentIndex = currentIndex,
                lookback = 20
            )

        val breakout =
            detectBreakout(
                candles = candles,
                currentIndex = currentIndex,
                lookback = 20
            )

        val failedBreakout =
            detectFailedBreakout(
                candles = candles,
                currentIndex = currentIndex,
                lookback = 20
            )

        val bullishSweep =
            detectBullishLiquiditySweep(
                candles = candles,
                currentIndex = currentIndex,
                lookback = 10
            )

        val bearishSweep =
            detectBearishLiquiditySweep(
                candles = candles,
                currentIndex = currentIndex,
                lookback = 10
            )

        val structure =
            detectStructure(
                candles = candles,
                currentIndex = currentIndex
            )

        val bullishStructure =
            structure ==
                    MarketStructure.BULLISH

        val bearishStructure =
            structure ==
                    MarketStructure.BEARISH

        val distanceFromVwap =
            if (
                vwap != null &&
                abs(vwap) > 0.000001
            ) {

                (
                        (
                                current.close -
                                        vwap
                                ) /
                                vwap
                        ) *
                        100.0

            } else {
                null
            }

        val signals =
            mutableListOf<String>()

        if (breakout) {
            signals.add(
                "Breakout"
            )
        }

        if (failedBreakout) {
            signals.add(
                "Failed breakout"
            )
        }

        if (bullishSweep) {
            signals.add(
                "Bullish liquidity sweep"
            )
        }

        if (bearishSweep) {
            signals.add(
                "Bearish liquidity sweep"
            )
        }

        if (
            volumeRatio != null &&
            volumeRatio >= 1.5
        ) {
            signals.add(
                "Volume expansion"
            )
        }

        if (aboveVwap == true) {
            signals.add(
                "Above VWAP"
            )
        }

        if (aboveVwap == false) {
            signals.add(
                "Below VWAP"
            )
        }

        if (
            aboveEma9 == true &&
            aboveEma20 == true
        ) {
            signals.add(
                "Above EMA 9 / EMA 20"
            )
        }

        if (bullishStructure) {
            signals.add(
                "Bullish structure"
            )
        }

        if (bearishStructure) {
            signals.add(
                "Bearish structure"
            )
        }

        val score =
            calculateTechnicalScore(
                aboveVwap =
                    aboveVwap,
                aboveEma9 =
                    aboveEma9,
                aboveEma20 =
                    aboveEma20,
                volumeRatio =
                    volumeRatio,
                breakout =
                    breakout,
                failedBreakout =
                    failedBreakout,
                bullishSweep =
                    bullishSweep,
                bearishSweep =
                    bearishSweep,
                bullishStructure =
                    bullishStructure,
                bearishStructure =
                    bearishStructure
            )

        return TechnicalSnapshot(
            timestamp =
                current.timestamp,

            price =
                current.close,

            ema9 =
                ema9,

            ema20 =
                ema20,

            vwap =
                vwap,

            aboveEma9 =
                aboveEma9,

            aboveEma20 =
                aboveEma20,

            aboveVwap =
                aboveVwap,

            volumeRatio =
                volumeRatio,

            breakout =
                breakout,

            failedBreakout =
                failedBreakout,

            bullishLiquiditySweep =
                bullishSweep,

            bearishLiquiditySweep =
                bearishSweep,

            bullishStructure =
                bullishStructure,

            bearishStructure =
                bearishStructure,

            distanceFromVwapPercent =
                distanceFromVwap,

            technicalScore =
                score,

            signals =
                signals
        )
    }

    private fun calculateVolumeRatio(
        candles: List<Candle>,
        currentIndex: Int,
        lookback: Int
    ): Double? {

        if (currentIndex <= 0) {
            return null
        }

        val start =
            maxOf(
                0,
                currentIndex -
                        lookback
            )

        val previous =
            candles.subList(
                start,
                currentIndex
            )

        if (previous.isEmpty()) {
            return null
        }

        val averageVolume =
            previous
                .map {
                    it.volume.toDouble()
                }
                .average()

        if (averageVolume <= 0.0) {
            return null
        }

        return candles[currentIndex]
            .volume
            .toDouble() /
                averageVolume
    }

    private fun detectBreakout(
        candles: List<Candle>,
        currentIndex: Int,
        lookback: Int
    ): Boolean {

        if (currentIndex < 2) {
            return false
        }

        val start =
            maxOf(
                0,
                currentIndex -
                        lookback
            )

        val previous =
            candles.subList(
                start,
                currentIndex
            )

        if (previous.isEmpty()) {
            return false
        }

        val previousHigh =
            previous.maxOf {
                it.high
            }

        val current =
            candles[currentIndex]

        return current.close >
                previousHigh
    }

    private fun detectFailedBreakout(
        candles: List<Candle>,
        currentIndex: Int,
        lookback: Int
    ): Boolean {

        if (currentIndex < 2) {
            return false
        }

        val start =
            maxOf(
                0,
                currentIndex -
                        lookback
            )

        val previous =
            candles.subList(
                start,
                currentIndex
            )

        if (previous.isEmpty()) {
            return false
        }

        val previousHigh =
            previous.maxOf {
                it.high
            }

        val current =
            candles[currentIndex]

        return (
                current.high >
                        previousHigh &&
                        current.close <
                        previousHigh
                )
    }

    private fun detectBullishLiquiditySweep(
        candles: List<Candle>,
        currentIndex: Int,
        lookback: Int
    ): Boolean {

        if (currentIndex < 2) {
            return false
        }

        val start =
            maxOf(
                0,
                currentIndex -
                        lookback
            )

        val previous =
            candles.subList(
                start,
                currentIndex
            )

        if (previous.isEmpty()) {
            return false
        }

        val previousLow =
            previous.minOf {
                it.low
            }

        val current =
            candles[currentIndex]

        return (
                current.low <
                        previousLow &&
                        current.close >
                        previousLow
                )
    }

    private fun detectBearishLiquiditySweep(
        candles: List<Candle>,
        currentIndex: Int,
        lookback: Int
    ): Boolean {

        if (currentIndex < 2) {
            return false
        }

        val start =
            maxOf(
                0,
                currentIndex -
                        lookback
            )

        val previous =
            candles.subList(
                start,
                currentIndex
            )

        if (previous.isEmpty()) {
            return false
        }

        val previousHigh =
            previous.maxOf {
                it.high
            }

        val current =
            candles[currentIndex]

        return (
                current.high >
                        previousHigh &&
                        current.close <
                        previousHigh
                )
    }

    private enum class MarketStructure {
        BULLISH,
        BEARISH,
        MIXED
    }

    private fun detectStructure(
        candles: List<Candle>,
        currentIndex: Int
    ): MarketStructure {

        if (currentIndex < 4) {
            return MarketStructure.MIXED
        }

        val recent =
            candles.subList(
                currentIndex - 4,
                currentIndex + 1
            )

        val highs =
            recent.map {
                it.high
            }

        val lows =
            recent.map {
                it.low
            }

        val higherHighs =
            highs.zipWithNext()
                .count {
                        pair ->

                    pair.second >
                            pair.first
                }

        val higherLows =
            lows.zipWithNext()
                .count {
                        pair ->

                    pair.second >
                            pair.first
                }

        val lowerHighs =
            highs.zipWithNext()
                .count {
                        pair ->

                    pair.second <
                            pair.first
                }

        val lowerLows =
            lows.zipWithNext()
                .count {
                        pair ->

                    pair.second <
                            pair.first
                }

        return when {

            higherHighs >= 3 &&
                    higherLows >= 3 -> {

                MarketStructure.BULLISH
            }

            lowerHighs >= 3 &&
                    lowerLows >= 3 -> {

                MarketStructure.BEARISH
            }

            else -> {
                MarketStructure.MIXED
            }
        }
    }

    private fun calculateTechnicalScore(
        aboveVwap: Boolean?,
        aboveEma9: Boolean?,
        aboveEma20: Boolean?,
        volumeRatio: Double?,
        breakout: Boolean,
        failedBreakout: Boolean,
        bullishSweep: Boolean,
        bearishSweep: Boolean,
        bullishStructure: Boolean,
        bearishStructure: Boolean
    ): Int {

        var score =
            50

        if (aboveVwap == true) {
            score += 8
        }

        if (aboveVwap == false) {
            score -= 8
        }

        if (aboveEma9 == true) {
            score += 5
        }

        if (aboveEma9 == false) {
            score -= 5
        }

        if (aboveEma20 == true) {
            score += 7
        }

        if (aboveEma20 == false) {
            score -= 7
        }

        if (
            volumeRatio != null &&
            volumeRatio >= 1.5
        ) {
            score += 8
        }

        if (breakout) {
            score += 10
        }

        if (failedBreakout) {
            score -= 12
        }

        if (bullishSweep) {
            score += 8
        }

        if (bearishSweep) {
            score -= 8
        }

        if (bullishStructure) {
            score += 10
        }

        if (bearishStructure) {
            score -= 10
        }

        return score.coerceIn(
            0,
            100
        )
    }
}

