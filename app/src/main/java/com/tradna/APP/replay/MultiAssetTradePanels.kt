package com.tradna.APP.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.data.MultiAssetAnalysisState
import com.tradna.APP.data.MultiAssetTradeEpisode
import com.tradna.APP.data.TraDnaAssetType
import java.text.NumberFormat
import java.util.Locale

private val MultiAssetSurface =
    Color(0xFF0E1219)

private val MultiAssetSurface2 =
    Color(0xFF141A23)

private val MultiAssetBorder =
    Color(0xFF222B38)

private val MultiAssetText =
    Color(0xFFF4F7FB)

private val MultiAssetSecondary =
    Color(0xFF8D98A8)

private val MultiAssetCyan =
    Color(0xFF72E7FF)

private val MultiAssetGreen =
    Color(0xFF39D6A0)

private val MultiAssetRed =
    Color(0xFFFF657A)

private val MultiAssetViolet =
    Color(0xFF9B7CFF)

private val MultiAssetGold =
    Color(0xFFFFC857)

@Composable
fun MultiAssetReplayPanel(
    trades: List<MultiAssetTradeEpisode>,
    maxVisibleTrades: Int = 12
) {

    val stocks =
        trades.count {
            it.assetType ==
                    TraDnaAssetType.STOCK
        }

    val options =
        trades.count {
            it.assetType ==
                    TraDnaAssetType.OPTION
        }

    val futures =
        trades.count {
            it.assetType ==
                    TraDnaAssetType.FUTURE
        }

    val closed =
        trades.count {
            it.isClosed
        }

    MultiAssetPanelCard {

        MultiAssetSectionLabel(
            "MULTI-ASSET REPLAY"
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "${trades.size} reconstructed trade episodes available",
            color =
                MultiAssetText,
            fontSize =
                19.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                "$stocks stock • $options option • $futures futures • $closed closed",
            color =
                MultiAssetSecondary,
            fontSize =
                12.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        HorizontalDivider(
            color =
                MultiAssetBorder
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        if (
            trades.isEmpty()
        ) {

            Text(
                text =
                    "No reconstructed trades are available yet.",
                color =
                    MultiAssetSecondary,
                fontSize =
                    13.sp
            )

        } else {

            trades
                .take(
                    maxVisibleTrades
                )
                .forEachIndexed {
                        index,
                        trade ->

                    MultiAssetTradeRow(
                        trade =
                            trade
                    )

                    if (
                        index <
                        minOf(
                            trades.size,
                            maxVisibleTrades
                        ) -
                        1
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    10.dp
                                )
                        )
                    }
                }

            if (
                trades.size >
                maxVisibleTrades
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            14.dp
                        )
                )

                Text(
                    text =
                        "+ ${trades.size - maxVisibleTrades} more reconstructed trades",
                    color =
                        MultiAssetCyan,
                    fontSize =
                        12.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MultiAssetLabPanel(
    trades: List<MultiAssetTradeEpisode>,
    trainedOptionTradeIds: Set<String> = emptySet()
) {

    val readyStocks =
        trades.count {
            it.analysisState ==
                    MultiAssetAnalysisState.READY
        }

    val optionContextTrained =
        trades.count {
            it.assetType ==
                    TraDnaAssetType.OPTION &&
                    it.id in
                    trainedOptionTradeIds
        }

    val optionsWaiting =
        trades.count {
            it.analysisState ==
                    MultiAssetAnalysisState.NEEDS_OPTION_CONTEXT &&
                    it.id !in
                    trainedOptionTradeIds
        }

    val futuresWaiting =
        trades.count {
            it.analysisState ==
                    MultiAssetAnalysisState.NEEDS_FUTURES_MARKET_DATA
        }

    val notReady =
        trades.count {
            it.analysisState ==
                    MultiAssetAnalysisState.NOT_READY
        }

    MultiAssetPanelCard {

        MultiAssetSectionLabel(
            "MULTI-ASSET AGENT INPUT"
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "${trades.size} reconstructed trades visible to TraDNA",
            color =
                MultiAssetText,
            fontSize =
                19.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        MultiAssetMetricRow(
            label =
                "Ready stock trades",
            value =
                readyStocks.toString(),
            valueColor =
                MultiAssetGreen
        )

        MultiAssetMetricRow(
            label =
                "Option context trained",
            value =
                optionContextTrained.toString(),
            valueColor =
                if (
                    optionContextTrained >
                    0
                ) {
                    MultiAssetGreen
                } else {
                    MultiAssetSecondary
                }
        )

        MultiAssetMetricRow(
            label =
                "Options awaiting context",
            value =
                optionsWaiting.toString(),
            valueColor =
                if (
                    optionsWaiting >
                    0
                ) {
                    MultiAssetGold
                } else {
                    MultiAssetSecondary
                }
        )

        MultiAssetMetricRow(
            label =
                "Futures awaiting data",
            value =
                futuresWaiting.toString(),
            valueColor =
                if (
                    futuresWaiting >
                    0
                ) {
                    MultiAssetViolet
                } else {
                    MultiAssetSecondary
                }
        )

        MultiAssetMetricRow(
            label =
                "Not ready",
            value =
                notReady.toString(),
            valueColor =
                if (
                    notReady >
                    0
                ) {
                    MultiAssetRed
                } else {
                    MultiAssetSecondary
                }
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        HorizontalDivider(
            color =
                MultiAssetBorder
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        Text(
            text =
                "Stocks continue through the existing market-context pipeline. Options marked CONTEXT TRAINED now have saved underlying-stock entry context. Futures remain queued until a futures-capable historical data provider is connected.",
            color =
                MultiAssetSecondary,
            fontSize =
                12.sp,
            lineHeight =
                18.sp
        )

        val derivativeTrades =
            trades.filter {
                it.assetType !=
                        TraDnaAssetType.STOCK
            }

        if (
            derivativeTrades.isNotEmpty()
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )

            MultiAssetSectionLabel(
                "DERIVATIVE QUEUE"
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            derivativeTrades
                .take(
                    8
                )
                .forEachIndexed {
                        index,
                        trade ->

                    MultiAssetLabQueueRow(
                        trade =
                            trade,
                        optionContextTrained =
                            trade.assetType ==
                                    TraDnaAssetType.OPTION &&
                                    trade.id in
                                    trainedOptionTradeIds
                    )

                    if (
                        index <
                        minOf(
                            derivativeTrades.size,
                            8
                        ) -
                        1
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )
                    }
                }

            if (
                derivativeTrades.size >
                8
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp
                        )
                )

                Text(
                    text =
                        "+ ${derivativeTrades.size - 8} more derivative trades queued",
                    color =
                        MultiAssetCyan,
                    fontSize =
                        11.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MultiAssetTradeRow(
    trade: MultiAssetTradeEpisode
) {

    val assetColor =
        assetColor(
            trade.assetType
        )

    val pnlColor =
        when {

            trade.realizedPnl >
                    0.0 ->
                MultiAssetGreen

            trade.realizedPnl <
                    0.0 ->
                MultiAssetRed

            else ->
                MultiAssetText
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color =
                        MultiAssetSurface2,
                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                )
                .border(
                    width =
                        1.dp,
                    color =
                        MultiAssetBorder,
                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                )
                .padding(
                    14.dp
                )
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    trade.symbol,
                color =
                    MultiAssetText,
                fontSize =
                    16.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            Text(
                text =
                    trade.displayAssetType,
                color =
                    assetColor,
                fontSize =
                    10.sp,
                fontWeight =
                    FontWeight.Bold,
                letterSpacing =
                    1.0.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    5.dp
                )
        )

        Text(
            text =
                buildDateRange(
                    trade
                ),
            color =
                MultiAssetSecondary,
            fontSize =
                11.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            MiniMetric(
                label =
                    "ENTRY",
                value =
                    formatPrice(
                        trade.averageEntryPrice
                    ),
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            MiniMetric(
                label =
                    "EXIT",
                value =
                    trade.averageExitPrice
                        ?.let {
                            formatPrice(
                                it
                            )
                        }
                        ?: "OPEN",
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            MiniMetric(
                label =
                    "P&L",
                value =
                    formatMoney(
                        trade.realizedPnl
                    ),
                valueColor =
                    pnlColor,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Text(
            text =
                "${trade.direction.name} • ${trade.status.name} • ${trade.executions.size} execution${if (trade.executions.size == 1) "" else "s"}",
            color =
                MultiAssetSecondary,
            fontSize =
                10.sp
        )

        if (
            trade.assetType ==
            TraDnaAssetType.OPTION
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )

            Text(
                text =
                    buildOptionMetadata(
                        trade
                    ),
                color =
                    MultiAssetViolet,
                fontSize =
                    10.sp
            )
        }

        if (
            trade.assetType ==
            TraDnaAssetType.FUTURE
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )

            Text(
                text =
                    buildFutureMetadata(
                        trade
                    ),
                color =
                    MultiAssetGold,
                fontSize =
                    10.sp
            )
        }
    }
}

@Composable
private fun MultiAssetLabQueueRow(
    trade: MultiAssetTradeEpisode,
    optionContextTrained: Boolean
) {

    val stateColor =
        if (
            optionContextTrained
        ) {
            MultiAssetGreen
        } else {
            when (
                trade.analysisState
            ) {

                MultiAssetAnalysisState.READY ->
                    MultiAssetGreen

                MultiAssetAnalysisState.NEEDS_OPTION_CONTEXT ->
                    MultiAssetGold

                MultiAssetAnalysisState.NEEDS_FUTURES_MARKET_DATA ->
                    MultiAssetViolet

                MultiAssetAnalysisState.NOT_READY ->
                    MultiAssetRed
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color =
                        MultiAssetSurface2,
                    shape =
                        RoundedCornerShape(
                            12.dp
                        )
                )
                .border(
                    width =
                        1.dp,
                    color =
                        MultiAssetBorder,
                    shape =
                        RoundedCornerShape(
                            12.dp
                        )
                )
                .padding(
                    12.dp
                )
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    trade.symbol,
                color =
                    MultiAssetText,
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            Text(
                text =
                    if (
                        optionContextTrained
                    ) {
                        "CONTEXT TRAINED"
                    } else {
                        analysisStateLabel(
                            trade.analysisState
                        )
                    },
                color =
                    stateColor,
                fontSize =
                    9.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    5.dp
                )
        )

        Text(
            text =
                if (
                    optionContextTrained
                ) {
                    "Underlying-stock market context around this option entry has been reconstructed and saved for derivative learning."
                } else {
                    trade.analysisNote
                },
            color =
                MultiAssetSecondary,
            fontSize =
                10.sp,
            lineHeight =
                15.sp
        )
    }
}

@Composable
private fun MultiAssetMetricRow(
    label: String,
    value: String,
    valueColor: Color
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        6.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                label,
            color =
                MultiAssetSecondary,
            fontSize =
                12.sp,
            modifier =
                Modifier.weight(
                    1f
                )
        )

        Spacer(
            modifier =
                Modifier.width(
                    12.dp
                )
        )

        Text(
            text =
                value,
            color =
                valueColor,
            fontSize =
                15.sp,
            fontWeight =
                FontWeight.Bold,
            textAlign =
                TextAlign.End
        )
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MultiAssetText
) {

    Column(
        modifier =
            modifier
    ) {

        Text(
            text =
                label,
            color =
                MultiAssetSecondary,
            fontSize =
                8.sp,
            fontWeight =
                FontWeight.Bold,
            letterSpacing =
                0.8.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    3.dp
                )
        )

        Text(
            text =
                value,
            color =
                valueColor,
            fontSize =
                11.sp,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun MultiAssetSectionLabel(
    text: String
) {

    Text(
        text =
            text,
        color =
            MultiAssetCyan,
        fontSize =
            10.sp,
        fontWeight =
            FontWeight.Bold,
        letterSpacing =
            1.4.sp
    )
}

@Composable
private fun MultiAssetPanelCard(
    content:
    @Composable
        () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color =
                        MultiAssetSurface,
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                )
                .border(
                    width =
                        1.dp,
                    color =
                        MultiAssetBorder,
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                )
                .padding(
                    18.dp
                )
    ) {

        content()
    }
}

private fun assetColor(
    assetType: TraDnaAssetType
): Color {

    return when (
        assetType
    ) {

        TraDnaAssetType.STOCK ->
            MultiAssetCyan

        TraDnaAssetType.OPTION ->
            MultiAssetViolet

        TraDnaAssetType.FUTURE ->
            MultiAssetGold
    }
}

private fun analysisStateLabel(
    state: MultiAssetAnalysisState
): String {

    return when (
        state
    ) {

        MultiAssetAnalysisState.READY ->
            "READY"

        MultiAssetAnalysisState.NEEDS_OPTION_CONTEXT ->
            "OPTION CONTEXT"

        MultiAssetAnalysisState.NEEDS_FUTURES_MARKET_DATA ->
            "FUTURES DATA"

        MultiAssetAnalysisState.NOT_READY ->
            "NOT READY"
    }
}

private fun buildDateRange(
    trade: MultiAssetTradeEpisode
): String {

    return if (
        trade.closeDate !=
        null
    ) {

        "${trade.openDate} → ${trade.closeDate}"

    } else {

        "${trade.openDate} → OPEN"
    }
}

private fun buildOptionMetadata(
    trade: MultiAssetTradeEpisode
): String {

    val pieces =
        mutableListOf<String>()

    trade.underlyingSymbol
        ?.takeIf {
            it.isNotBlank()
        }
        ?.let {
            pieces.add(
                "Underlying $it"
            )
        }

    trade.expirationDate
        ?.takeIf {
            it.isNotBlank()
        }
        ?.let {
            pieces.add(
                "Exp $it"
            )
        }

    trade.strikePrice
        ?.let {
            pieces.add(
                "Strike ${formatPrice(it)}"
            )
        }

    trade.optionRight
        ?.takeIf {
            it.isNotBlank()
        }
        ?.let {
            pieces.add(
                it
            )
        }

    return pieces
        .joinToString(
            " • "
        )
        .ifBlank {
            "Option contract"
        }
}

private fun buildFutureMetadata(
    trade: MultiAssetTradeEpisode
): String {

    val pieces =
        mutableListOf<String>()

    trade.futuresRootSymbol
        ?.takeIf {
            it.isNotBlank()
        }
        ?.let {
            pieces.add(
                "Root $it"
            )
        }

    trade.futuresPointValue
        ?.let {
            pieces.add(
                "Point ${formatMoney(it)}"
            )
        }

    trade.futuresTickSize
        ?.let {
            pieces.add(
                "Tick ${formatPrice(it)}"
            )
        }

    trade.futuresTickValue
        ?.let {
            pieces.add(
                "Tick value ${formatMoney(it)}"
            )
        }

    return pieces
        .joinToString(
            " • "
        )
        .ifBlank {
            "Futures contract"
        }
}

private fun formatPrice(
    value: Double
): String {

    return if (
        kotlin.math.abs(
            value
        ) >=
        1000.0
    ) {

        String.format(
            Locale.US,
            "%,.2f",
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

private fun formatMoney(
    value: Double
): String {

    return NumberFormat
        .getCurrencyInstance(
            Locale.US
        )
        .format(
            value
        )
}
