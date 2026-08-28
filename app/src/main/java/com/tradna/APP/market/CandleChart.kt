package com.tradna.APP.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ChartBackground =
    Color(0xFF0E1219)

private val ChartGrid =
    Color(0xFF202734)

private val ChartText =
    Color(0xFF8D98A8)

private val BullColor =
    Color(0xFF39D6A0)

private val BearColor =
    Color(0xFFFF657A)

private val VwapColor =
    Color(0xFF72E7FF)

private val EmaColor =
    Color(0xFF9B7CFF)

private val EntryColor =
    Color(0xFF39D6A0)

private val ExitColor =
    Color(0xFFFFC857)

private val CrosshairColor =
    Color(0xFFB7C0CC)

@Composable
fun CandleChart(
    candles: List<Candle>,
    entryPrice: Double? = null,
    exitPrice: Double? = null,
    modifier: Modifier = Modifier
) {

    val textMeasurer =
        rememberTextMeasurer()

    var zoom by remember {
        mutableFloatStateOf(1f)
    }

    var horizontalOffset by remember {
        mutableFloatStateOf(0f)
    }

    var crosshair by remember {
        mutableStateOf<Offset?>(null)
    }

    val ema20 =
        remember(candles) {
            MarketIndicators.ema(
                candles = candles,
                period = 20
            )
        }

    val vwap =
        remember(candles) {
            MarketIndicators.vwap(
                candles
            )
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(
                RoundedCornerShape(
                    20.dp
                )
            )
            .background(
                ChartBackground
            )
    ) {

        if (candles.isEmpty()) {
            return@Box
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()

                .pointerInput(
                    candles
                ) {

                    detectTransformGestures {
                            _,
                            pan,
                            gestureZoom,
                            _ ->

                        zoom =
                            (
                                    zoom *
                                            gestureZoom
                                    )
                                .coerceIn(
                                    1f,
                                    8f
                                )

                        horizontalOffset +=
                            pan.x
                    }
                }

                .pointerInput(
                    candles,
                    zoom
                ) {

                    detectDragGestures(
                        onDragStart = {
                            crosshair = it
                        },

                        onDragEnd = {
                            crosshair = null
                        },

                        onDragCancel = {
                            crosshair = null
                        }
                    ) {
                            change,
                            dragAmount ->

                        change.consume()

                        crosshair =
                            change.position

                        horizontalOffset +=
                            dragAmount.x
                    }
                }
        ) {

            val width =
                size.width

            val height =
                size.height

            val priceAxisWidth =
                64f

            val timeAxisHeight =
                34f

            val volumeHeight =
                78f

            val chartLeft =
                8f

            val chartRight =
                width -
                        priceAxisWidth

            val chartTop =
                12f

            val priceChartBottom =
                height -
                        volumeHeight -
                        timeAxisHeight -
                        12f

            val volumeTop =
                priceChartBottom +
                        12f

            val volumeBottom =
                height -
                        timeAxisHeight

            val availableWidth =
                chartRight -
                        chartLeft

            val baseVisibleCount =
                min(
                    candles.size,
                    70
                )

            val visibleCount =
                max(
                    8,
                    (
                            baseVisibleCount /
                                    zoom
                            )
                        .toInt()
                )

            val slotWidth =
                availableWidth /
                        visibleCount.toFloat()

            val maxOffset =
                max(
                    0f,
                    (
                            candles.size -
                                    visibleCount
                            ) *
                            slotWidth
                )

            horizontalOffset =
                horizontalOffset
                    .coerceIn(
                        -maxOffset,
                        0f
                    )

            val shiftCandles =
                if (
                    slotWidth > 0f
                ) {
                    (
                            -horizontalOffset /
                                    slotWidth
                            )
                        .toInt()
                } else {
                    0
                }

            val startIndex =
                (
                        candles.size -
                                visibleCount -
                                shiftCandles
                        )
                    .coerceIn(
                        0,
                        max(
                            0,
                            candles.size -
                                    visibleCount
                        )
                    )

            val endIndex =
                min(
                    candles.size,
                    startIndex +
                            visibleCount
                )

            val visibleCandles =
                candles.subList(
                    startIndex,
                    endIndex
                )

            val visibleEma =
                ema20.subList(
                    startIndex,
                    endIndex
                )

            val visibleVwap =
                vwap.subList(
                    startIndex,
                    endIndex
                )

            if (
                visibleCandles.isEmpty()
            ) {
                return@Canvas
            }

            var highest =
                visibleCandles
                    .maxOf {
                        it.high
                    }

            var lowest =
                visibleCandles
                    .minOf {
                        it.low
                    }

            entryPrice?.let {

                highest =
                    max(
                        highest,
                        it
                    )

                lowest =
                    min(
                        lowest,
                        it
                    )
            }

            exitPrice?.let {

                highest =
                    max(
                        highest,
                        it
                    )

                lowest =
                    min(
                        lowest,
                        it
                    )
            }

            val rawRange =
                max(
                    highest -
                            lowest,
                    0.0001
                )

            val padding =
                rawRange *
                        0.08

            highest +=
                padding

            lowest -=
                padding

            val priceRange =
                highest -
                        lowest

            fun priceToY(
                price: Double
            ): Float {

                val normalized =
                    (
                            price -
                                    lowest
                            ) /
                            priceRange

                return (
                        priceChartBottom -
                                (
                                        normalized *
                                                (
                                                        priceChartBottom -
                                                                chartTop
                                                        )
                                        )
                        ).toFloat()
            }

            fun xForIndex(
                visibleIndex: Int
            ): Float {

                return chartLeft +
                        (
                                visibleIndex +
                                        0.5f
                                ) *
                        slotWidth
            }

            drawPriceGrid(
                lowest = lowest,
                highest = highest,
                chartLeft = chartLeft,
                chartRight = chartRight,
                chartTop = chartTop,
                chartBottom =
                    priceChartBottom,
                textMeasurer =
                    textMeasurer
            )

            val maxVolume =
                visibleCandles
                    .maxOf {
                        it.volume
                    }
                    .coerceAtLeast(1L)

            visibleCandles
                .forEachIndexed {
                        index,
                        candle ->

                    val centerX =
                        xForIndex(
                            index
                        )

                    val openY =
                        priceToY(
                            candle.open
                        )

                    val closeY =
                        priceToY(
                            candle.close
                        )

                    val highY =
                        priceToY(
                            candle.high
                        )

                    val lowY =
                        priceToY(
                            candle.low
                        )

                    val bullish =
                        candle.close >=
                                candle.open

                    val color =
                        if (bullish) {
                            BullColor
                        } else {
                            BearColor
                        }

                    drawLine(
                        color = color,
                        start =
                            Offset(
                                centerX,
                                highY
                            ),
                        end =
                            Offset(
                                centerX,
                                lowY
                            ),
                        strokeWidth =
                            1.5f
                    )

                    val bodyTop =
                        min(
                            openY,
                            closeY
                        )

                    val bodyBottom =
                        max(
                            openY,
                            closeY
                        )

                    val bodyWidth =
                        max(
                            2f,
                            slotWidth *
                                    0.62f
                        )

                    val bodyHeight =
                        max(
                            2f,
                            bodyBottom -
                                    bodyTop
                        )

                    drawRect(
                        color = color,
                        topLeft =
                            Offset(
                                centerX -
                                        bodyWidth /
                                        2f,
                                bodyTop
                            ),
                        size =
                            Size(
                                bodyWidth,
                                bodyHeight
                            )
                    )

                    val volumeRatio =
                        candle.volume
                            .toFloat() /
                                maxVolume
                                    .toFloat()

                    val barHeight =
                        (
                                volumeBottom -
                                        volumeTop
                                ) *
                                volumeRatio

                    drawRect(
                        color =
                            color.copy(
                                alpha =
                                    0.45f
                            ),
                        topLeft =
                            Offset(
                                centerX -
                                        bodyWidth /
                                        2f,
                                volumeBottom -
                                        barHeight
                            ),
                        size =
                            Size(
                                bodyWidth,
                                barHeight
                            )
                    )
                }

            drawIndicatorLine(
                values =
                    visibleVwap,
                color =
                    VwapColor,
                xForIndex =
                    ::xForIndex,
                priceToY =
                    ::priceToY
            )

            drawIndicatorLine(
                values =
                    visibleEma,
                color =
                    EmaColor,
                xForIndex =
                    ::xForIndex,
                priceToY =
                    ::priceToY
            )

            entryPrice?.let {

                val y =
                    priceToY(it)

                drawLine(
                    color =
                        EntryColor,
                    start =
                        Offset(
                            chartLeft,
                            y
                        ),
                    end =
                        Offset(
                            chartRight,
                            y
                        ),
                    strokeWidth =
                        2f
                )

                drawText(
                    textMeasurer =
                        textMeasurer,
                    text =
                        "ENTRY ${formatPrice(it)}",
                    topLeft =
                        Offset(
                            chartLeft +
                                    6f,
                            y -
                                    22f
                        ),
                    style =
                        TextStyle(
                            color =
                                EntryColor,
                            fontSize =
                                10.sp
                        )
                )
            }

            exitPrice?.let {

                val y =
                    priceToY(it)

                drawLine(
                    color =
                        ExitColor,
                    start =
                        Offset(
                            chartLeft,
                            y
                        ),
                    end =
                        Offset(
                            chartRight,
                            y
                        ),
                    strokeWidth =
                        2f
                )

                drawText(
                    textMeasurer =
                        textMeasurer,
                    text =
                        "EXIT ${formatPrice(it)}",
                    topLeft =
                        Offset(
                            chartLeft +
                                    6f,
                            y +
                                    3f
                        ),
                    style =
                        TextStyle(
                            color =
                                ExitColor,
                            fontSize =
                                10.sp
                        )
                )
            }

            drawTimeLabels(
                candles =
                    visibleCandles,
                chartLeft =
                    chartLeft,
                slotWidth =
                    slotWidth,
                y =
                    height -
                            timeAxisHeight +
                            5f,
                textMeasurer =
                    textMeasurer
            )

            drawText(
                textMeasurer =
                    textMeasurer,
                text =
                    "VWAP",
                topLeft =
                    Offset(
                        chartLeft +
                                8f,
                        chartTop +
                                6f
                    ),
                style =
                    TextStyle(
                        color =
                            VwapColor,
                        fontSize =
                            10.sp
                    )
            )

            drawText(
                textMeasurer =
                    textMeasurer,
                text =
                    "EMA 20",
                topLeft =
                    Offset(
                        chartLeft +
                                58f,
                        chartTop +
                                6f
                    ),
                style =
                    TextStyle(
                        color =
                            EmaColor,
                        fontSize =
                            10.sp
                    )
            )

            crosshair?.let {
                    point ->

                if (
                    point.x >= chartLeft &&
                    point.x <= chartRight &&
                    point.y >= chartTop &&
                    point.y <=
                    priceChartBottom
                ) {

                    drawLine(
                        color =
                            CrosshairColor
                                .copy(
                                    alpha =
                                        0.65f
                                ),
                        start =
                            Offset(
                                point.x,
                                chartTop
                            ),
                        end =
                            Offset(
                                point.x,
                                volumeBottom
                            ),
                        strokeWidth =
                            1f
                    )

                    drawLine(
                        color =
                            CrosshairColor
                                .copy(
                                    alpha =
                                        0.65f
                                ),
                        start =
                            Offset(
                                chartLeft,
                                point.y
                            ),
                        end =
                            Offset(
                                chartRight,
                                point.y
                            ),
                        strokeWidth =
                            1f
                    )

                    val normalized =
                        1.0 -
                                (
                                        (
                                                point.y -
                                                        chartTop
                                                ) /
                                                (
                                                        priceChartBottom -
                                                                chartTop
                                                        )
                                        )

                    val price =
                        lowest +
                                normalized *
                                priceRange

                    drawText(
                        textMeasurer =
                            textMeasurer,
                        text =
                            formatPrice(
                                price
                            ),
                        topLeft =
                            Offset(
                                chartRight +
                                        5f,
                                point.y -
                                        10f
                            ),
                        style =
                            TextStyle(
                                color =
                                    TraChartWhite,
                                fontSize =
                                    10.sp
                            )
                    )
                }
            }
        }
    }
}

private val TraChartWhite =
    Color(0xFFF4F7FB)

private fun DrawScope.drawPriceGrid(
    lowest: Double,
    highest: Double,
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    textMeasurer:
    androidx.compose.ui.text.TextMeasurer
) {

    val levels = 5

    for (
    level in 0..levels
    ) {

        val fraction =
            level.toFloat() /
                    levels.toFloat()

        val y =
            chartTop +
                    (
                            chartBottom -
                                    chartTop
                            ) *
                    fraction

        drawLine(
            color =
                ChartGrid,
            start =
                Offset(
                    chartLeft,
                    y
                ),
            end =
                Offset(
                    chartRight,
                    y
                ),
            strokeWidth =
                1f
        )

        val price =
            highest -
                    (
                            highest -
                                    lowest
                            ) *
                    fraction

        drawText(
            textMeasurer =
                textMeasurer,
            text =
                formatPrice(
                    price
                ),
            topLeft =
                Offset(
                    chartRight +
                            5f,
                    y -
                            9f
                ),
            style =
                TextStyle(
                    color =
                        ChartText,
                    fontSize =
                        9.sp
                )
        )
    }
}

private fun DrawScope.drawIndicatorLine(
    values: List<Double?>,
    color: Color,
    xForIndex:
        (Int) -> Float,
    priceToY:
        (Double) -> Float
) {

    val path =
        Path()

    var started =
        false

    values.forEachIndexed {
            index,
            value ->

        if (value == null) {

            started =
                false

            return@forEachIndexed
        }

        val x =
            xForIndex(index)

        val y =
            priceToY(value)

        if (!started) {

            path.moveTo(
                x,
                y
            )

            started =
                true

        } else {

            path.lineTo(
                x,
                y
            )
        }
    }

    drawPath(
        path = path,
        color = color,
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(
                    width = 2f
                )
    )
}

private fun DrawScope.drawTimeLabels(
    candles: List<Candle>,
    chartLeft: Float,
    slotWidth: Float,
    y: Float,
    textMeasurer:
    androidx.compose.ui.text.TextMeasurer
) {

    if (candles.isEmpty()) {
        return
    }

    val desiredLabels = 4

    val step =
        max(
            1,
            candles.size /
                    desiredLabels
        )

    candles.forEachIndexed {
            index,
            candle ->

        if (
            index % step != 0 &&
            index !=
            candles.lastIndex
        ) {
            return@forEachIndexed
        }

        val x =
            chartLeft +
                    (
                            index +
                                    0.5f
                            ) *
                    slotWidth

        drawText(
            textMeasurer =
                textMeasurer,
            text =
                formatTimestamp(
                    candle.timestamp
                ),
            topLeft =
                Offset(
                    x -
                            24f,
                    y
                ),
            style =
                TextStyle(
                    color =
                        ChartText,
                    fontSize =
                        8.sp
                )
        )
    }
}

private fun formatTimestamp(
    value: String
): String {

    return try {

        val dateTime =
            OffsetDateTime.parse(
                value
            )

        dateTime.format(
            DateTimeFormatter.ofPattern(
                "M/d HH:mm",
                Locale.US
            )
        )

    } catch (_: Exception) {

        value.take(10)
    }
}

private fun formatPrice(
    value: Double
): String {

    return if (
        abs(value) <
        10.0
    ) {

        String.format(
            Locale.US,
            "%.3f",
            value
        )

    } else {

        String.format(
            Locale.US,
            "%.2f",
            value
        )
    }
}