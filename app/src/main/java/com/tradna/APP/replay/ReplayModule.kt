package com.tradna.APP.replay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.market.AlpacaMarketData
import com.tradna.APP.market.Candle
import com.tradna.APP.market.CandleChart
import com.tradna.APP.market.TechnicalSignalEngine
import com.tradna.APP.market.TechnicalSnapshot
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val ReplayBackground =
    Color(0xFF07090D)

private val ReplaySurface =
    Color(0xFF0E1219)

private val ReplaySurface2 =
    Color(0xFF141A23)

private val ReplayBorder =
    Color(0xFF222B38)

private val ReplayText =
    Color(0xFFF4F7FB)

private val ReplaySecondary =
    Color(0xFF8D98A8)

private val ReplayCyan =
    Color(0xFF72E7FF)

private val ReplayGreen =
    Color(0xFF39D6A0)

private val ReplayRed =
    Color(0xFFFF657A)

private val ReplayViolet =
    Color(0xFF9B7CFF)

private val ReplayGold =
    Color(0xFFFFC857)

enum class ReplayChoice {
    BUY,
    WAIT,
    PASS
}

data class ReplayDecision(
    val tradeId: String,
    val symbol: String,
    val candleNumber: Int,
    val timestamp: String,
    val marketPrice: Double,
    val choice: ReplayChoice,
    val confidence: Int,
    val setup: String,
    val plannedEntry: Double?,
    val plannedStop: Double?,
    val plannedTarget: Double?
)

@Composable
fun ReplayModule(
    trades: List<TradeEpisode>
) {

    var selectedTrade by remember {
        mutableStateOf<TradeEpisode?>(
            null
        )
    }

    BackHandler(
        enabled =
            selectedTrade != null
    ) {

        selectedTrade =
            null
    }

    if (selectedTrade == null) {

        ReplayTradePicker(
            trades = trades,
            onTradeSelected = {
                selectedTrade = it
            }
        )

    } else {

        HistoricalReplayScreen(
            trade =
                selectedTrade!!,
            onBack = {
                selectedTrade = null
            }
        )
    }
}

@Composable
private fun ReplayTradePicker(
    trades: List<TradeEpisode>,
    onTradeSelected:
        (TradeEpisode) -> Unit
) {

    val context =
        LocalContext.current

    val savedCount =
        remember {
            ReplayStorage
                .loadAllDecisions(
                    context
                )
                .size
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                ReplayBackground
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 22.dp,
                    bottom = 16.dp
                )
        ) {

            Text(
                text = "REPLAY",
                color = ReplayText,
                fontSize = 30.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        5.dp
                    )
            )

            Text(
                text =
                    "Train your decision process without seeing the future.",
                color =
                    ReplaySecondary,
                fontSize =
                    14.sp
            )

            Spacer(
                modifier =
                    Modifier.height(
                        20.dp
                    )
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                ) {

                    SmallLabel(
                        "TRADE EPISODES"
                    )

                    Text(
                        text =
                            trades.size
                                .toString(),
                        color =
                            ReplayText,
                        fontSize =
                            22.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment =
                        Alignment.End
                ) {

                    SmallLabel(
                        "DNA DECISIONS"
                    )

                    Text(
                        text =
                            savedCount
                                .toString(),
                        color =
                            ReplayCyan,
                        fontSize =
                            22.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }

        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    bottom = 32.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            items(
                items = trades,
                key = {
                    it.id
                }
            ) { trade ->

                ReplayTradeRow(
                    trade =
                        trade,
                    onClick = {
                        onTradeSelected(
                            trade
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ReplayTradeRow(
    trade: TradeEpisode,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    18.dp
                )
            )
            .background(
                ReplaySurface
            )
            .border(
                width = 1.dp,
                color =
                    ReplayBorder,
                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            )
            .clickable {
                onClick()
            }
            .padding(
                17.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier =
                Modifier
                    .weight(1f)
        ) {

            Text(
                text =
                    trade.symbol,
                color =
                    ReplayText,
                fontSize =
                    20.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        3.dp
                    )
            )

            Text(
                text =
                    "${trade.openDate} → ${trade.closeDate ?: "OPEN"}",
                color =
                    ReplaySecondary,
                fontSize =
                    12.sp
            )
        }

        Text(
            text =
                "TRAIN →",
            color =
                ReplayCyan,
            fontSize =
                11.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun HistoricalReplayScreen(
    trade: TradeEpisode,
    onBack: () -> Unit
) {

    val context =
        LocalContext.current

    var candles by remember(
        trade.id
    ) {
        mutableStateOf<
                List<Candle>
                >(
            emptyList()
        )
    }

    var loading by remember(
        trade.id
    ) {
        mutableStateOf(
            true
        )
    }

    var error by remember(
        trade.id
    ) {
        mutableStateOf<String?>(
            null
        )
    }

    var timeframe by remember(
        trade.id
    ) {
        mutableStateOf(
            "5Min"
        )
    }

    var visibleCount by remember(
        trade.id
    ) {
        mutableIntStateOf(
            0
        )
    }

    var decisions by remember(
        trade.id
    ) {
        mutableStateOf(
            ReplayStorage
                .loadForTrade(
                    context,
                    trade.id
                )
        )
    }

    var selectedChoice by remember(
        trade.id
    ) {
        mutableStateOf<
                ReplayChoice?
                >(
            null
        )
    }

    var confidence by remember(
        trade.id
    ) {
        mutableIntStateOf(
            5
        )
    }

    var selectedSetup by remember(
        trade.id
    ) {
        mutableStateOf(
            "No setup"
        )
    }

    var entryText by remember(
        trade.id
    ) {
        mutableStateOf("")
    }

    var stopText by remember(
        trade.id
    ) {
        mutableStateOf("")
    }

    var targetText by remember(
        trade.id
    ) {
        mutableStateOf("")
    }

    LaunchedEffect(
        trade.id,
        timeframe
    ) {

        loading = true
        error = null
        candles =
            emptyList()
        visibleCount =
            0

        try {

            val range =
                replayDateRange(
                    trade
                )

            val loaded =
                AlpacaMarketData
                    .getBars(
                        symbol =
                            trade.symbol,
                        start =
                            range.first,
                        end =
                            range.second,
                        timeframe =
                            timeframe
                    )

            candles =
                loaded

            if (loaded.isEmpty()) {

                error =
                    "No market history was returned."

            } else {

                visibleCount =
                    minOf(
                        20,
                        loaded.size
                    )
            }

        } catch (
            e: Exception
        ) {

            error =
                e.message
                    ?: "Unable to load replay data."

        } finally {

            loading =
                false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                ReplayBackground
            )
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                bottom =
                    32.dp
            )
    ) {

        ReplayHeader(
            trade =
                trade,
            onBack =
                onBack
        )

        Column(
            modifier =
                Modifier
                    .padding(
                        horizontal =
                            20.dp
                    )
        ) {

            ReplayCard {

                Text(
                    text =
                        "BLIND REPLAY",
                    color =
                        ReplayViolet,
                    fontSize =
                        10.sp,
                    fontWeight =
                        FontWeight.Bold,
                    letterSpacing =
                        1.3.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Text(
                    text =
                        "The future is hidden.",
                    color =
                        ReplayText,
                    fontSize =
                        22.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )

                Text(
                    text =
                        "Read the chart, form a plan, compare your interpretation with TraDNA, then grade the outcome.",
                    color =
                        ReplaySecondary,
                    fontSize =
                        14.sp,
                    lineHeight =
                        20.sp
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )

            ReplayTimeframeSelector(
                selected =
                    timeframe,
                onSelected = {
                    timeframe =
                        it
                }
            )

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )

            when {

                loading -> {

                    ReplayLoading(
                        symbol =
                            trade.symbol
                    )
                }

                error != null -> {

                    ReplayCard {

                        Text(
                            text =
                                "REPLAY DATA ERROR",
                            color =
                                ReplayRed,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        Text(
                            text =
                                error!!,
                            color =
                                ReplayText
                        )
                    }
                }

                candles.isNotEmpty() -> {

                    val visibleCandles =
                        candles.take(
                            visibleCount
                        )

                    val technicalSnapshot =
                        TechnicalSignalEngine
                            .analyze(
                                visibleCandles
                            )

                    CandleChart(
                        candles =
                            visibleCandles,
                        entryPrice =
                            null,
                        exitPrice =
                            null
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    ReplayStatusCard(
                        totalCandles =
                            candles.size,
                        visibleCount =
                            visibleCount
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    TechnicalEngineCard(
                        snapshot =
                            technicalSnapshot
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    DecisionBuilder(
                        currentCandle =
                            candles.getOrNull(
                                visibleCount -
                                        1
                            ),

                        selectedChoice =
                            selectedChoice,

                        onChoice = {
                            selectedChoice =
                                it
                        },

                        confidence =
                            confidence,

                        onConfidence = {
                            confidence =
                                it
                        },

                        selectedSetup =
                            selectedSetup,

                        onSetup = {
                            selectedSetup =
                                it
                        },

                        entryText =
                            entryText,

                        onEntryText = {
                            entryText =
                                it
                        },

                        stopText =
                            stopText,

                        onStopText = {
                            stopText =
                                it
                        },

                        targetText =
                            targetText,

                        onTargetText = {
                            targetText =
                                it
                        },

                        onSave = {

                            val candle =
                                candles
                                    .getOrNull(
                                        visibleCount -
                                                1
                                    )

                            val choice =
                                selectedChoice

                            if (
                                candle != null &&
                                choice != null
                            ) {

                                val decision =
                                    ReplayDecision(
                                        tradeId =
                                            trade.id,

                                        symbol =
                                            trade.symbol,

                                        candleNumber =
                                            visibleCount,

                                        timestamp =
                                            candle.timestamp,

                                        marketPrice =
                                            candle.close,

                                        choice =
                                            choice,

                                        confidence =
                                            confidence,

                                        setup =
                                            selectedSetup,

                                        plannedEntry =
                                            entryText
                                                .toDoubleOrNull(),

                                        plannedStop =
                                            stopText
                                                .toDoubleOrNull(),

                                        plannedTarget =
                                            targetText
                                                .toDoubleOrNull()
                                    )

                                ReplayStorage
                                    .saveDecision(
                                        context,
                                        decision
                                    )

                                decisions =
                                    decisions +
                                            decision

                                selectedChoice =
                                    null

                                confidence =
                                    5

                                selectedSetup =
                                    "No setup"

                                entryText =
                                    ""

                                stopText =
                                    ""

                                targetText =
                                    ""
                            }
                        }
                    )

                    if (
                        selectedSetup !=
                        "No setup" &&
                        technicalSnapshot !=
                        null
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    14.dp
                                )
                        )

                        SetupComparisonCard(
                            selectedSetup =
                                selectedSetup,
                            snapshot =
                                technicalSnapshot
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    ReplayControls(
                        visibleCount =
                            visibleCount,

                        totalCandles =
                            candles.size,

                        onNext = {

                            if (
                                visibleCount <
                                candles.size
                            ) {

                                visibleCount++
                            }
                        },

                        onNextFive = {

                            visibleCount =
                                minOf(
                                    candles.size,
                                    visibleCount +
                                            5
                                )
                        },

                        onRestart = {

                            visibleCount =
                                minOf(
                                    20,
                                    candles.size
                                )
                        }
                    )

                    if (
                        decisions.isNotEmpty()
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    16.dp
                                )
                        )

                        DecisionHistory(
                            decisions =
                                decisions
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    16.dp
                                )
                        )

                        ReplayGradingPanel(
                            decisions =
                                decisions,
                            allCandles =
                                candles
                        )
                    }

                    if (
                        visibleCount >=
                        candles.size
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    16.dp
                                )
                        )

                        ReplayCompleteCard(
                            trade =
                                trade,
                            decisions =
                                decisions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalEngineCard(
    snapshot:
    TechnicalSnapshot?
) {

    ReplayCard {

        Text(
            text =
                "TRADNA TECHNICAL ENGINE",
            color =
                ReplayCyan,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold,
            letterSpacing =
                1.3.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        if (snapshot == null) {

            Text(
                text =
                    "Not enough candles yet for technical analysis.",
                color =
                    ReplaySecondary,
                fontSize =
                    13.sp
            )

            return@ReplayCard
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier
                        .weight(1f)
            ) {

                SmallLabel(
                    "TECHNICAL SCORE"
                )

                Text(
                    text =
                        "${snapshot.technicalScore} / 100",
                    color =
                        replayScoreColor(
                            snapshot
                                .technicalScore
                        ),
                    fontSize =
                        27.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End
            ) {

                SmallLabel(
                    "PRICE"
                )

                Text(
                    text =
                        formatReplayPrice(
                            snapshot.price
                        ),
                    color =
                        ReplayText,
                    fontSize =
                        18.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        TechnicalStatRow(
            label =
                "VWAP",
            value =
                snapshot.vwap
                    ?.let {
                        formatReplayPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        TechnicalStatRow(
            label =
                "EMA 9",
            value =
                snapshot.ema9
                    ?.let {
                        formatReplayPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        TechnicalStatRow(
            label =
                "EMA 20",
            value =
                snapshot.ema20
                    ?.let {
                        formatReplayPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        TechnicalStatRow(
            label =
                "Relative volume",
            value =
                snapshot.volumeRatio
                    ?.let {
                        String.format(
                            Locale.US,
                            "%.2fx",
                            it
                        )
                    }
                    ?: "—"
        )

        TechnicalStatRow(
            label =
                "VWAP distance",
            value =
                snapshot
                    .distanceFromVwapPercent
                    ?.let {
                        String.format(
                            Locale.US,
                            "%+.2f%%",
                            it
                        )
                    }
                    ?: "—"
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        SmallLabel(
            "DETECTED NOW"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        if (
            snapshot.signals.isEmpty()
        ) {

            Text(
                text =
                    "No strong predefined signal detected.",
                color =
                    ReplaySecondary,
                fontSize =
                    13.sp
            )

        } else {

            snapshot.signals
                .forEach {
                        signal ->

                    SignalRow(
                        signal
                    )
                }
        }
    }
}

@Composable
private fun SetupComparisonCard(
    selectedSetup: String,
    snapshot:
    TechnicalSnapshot
) {

    val normalizedSelected =
        normalizeSetup(
            selectedSetup
        )

    val detected =
        snapshot.signals
            .map {
                normalizeSetup(
                    it
                )
            }

    val directMatch =
        detected.any {

            it.contains(
                normalizedSelected,
                ignoreCase =
                    true
            ) ||
                    normalizedSelected
                        .contains(
                            it,
                            ignoreCase =
                                true
                        )
        }

    ReplayCard {

        Text(
            text =
                "YOUR READ VS ENGINE",
            color =
                ReplayViolet,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold,
            letterSpacing =
                1.2.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        SmallLabel(
            "YOU SELECTED"
        )

        Text(
            text =
                selectedSetup,
            color =
                ReplayText,
            fontSize =
                17.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        SmallLabel(
            "ENGINE AGREEMENT"
        )

        Text(
            text =
                if (
                    directMatch
                ) {
                    "DIRECT MATCH"
                } else {
                    "NO DIRECT MATCH"
                },
            color =
                if (
                    directMatch
                ) {
                    ReplayGreen
                } else {
                    ReplayGold
                },
            fontSize =
                16.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                if (
                    directMatch
                ) {
                    "Your selected setup is also present under the current detector definitions."
                } else {
                    "The engine is not labeling the current candle the same way. This gives TraDNA a disagreement to study."
                },
            color =
                ReplaySecondary,
            fontSize =
                13.sp,
            lineHeight =
                19.sp
        )
    }
}

@Composable
private fun SignalRow(
    signal: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        4.dp
                )
    ) {

        Text(
            text =
                "●",
            color =
                ReplayCyan,
            fontSize =
                10.sp
        )

        Spacer(
            modifier =
                Modifier.padding(
                    horizontal =
                        4.dp
                )
        )

        Text(
            text =
                signal,
            color =
                ReplayText,
            fontSize =
                13.sp
        )
    }
}

@Composable
private fun TechnicalStatRow(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        5.dp
                )
    ) {

        Text(
            text =
                label,
            color =
                ReplaySecondary,
            modifier =
                Modifier
                    .weight(1f),
            fontSize =
                12.sp
        )

        Text(
            text =
                value,
            color =
                ReplayText,
            fontWeight =
                FontWeight.Medium,
            fontSize =
                12.sp
        )
    }
}

@Composable
private fun ReplayHeader(
    trade: TradeEpisode,
    onBack: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        20.dp,
                    vertical =
                        18.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                "‹",
            color =
                ReplayCyan,
            fontSize =
                38.sp,
            modifier =
                Modifier
                    .clickable {
                        onBack()
                    }
                    .padding(
                        end =
                            15.dp
                    )
        )

        Column {

            Text(
                text =
                    "${trade.symbol} REPLAY",
                color =
                    ReplayText,
                fontSize =
                    23.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    "Trade #${trade.sequenceNumber} • ${trade.openDate}",
                color =
                    ReplaySecondary,
                fontSize =
                    12.sp
            )
        }
    }
}

@Composable
private fun DecisionBuilder(
    currentCandle: Candle?,
    selectedChoice:
    ReplayChoice?,
    onChoice:
        (ReplayChoice) -> Unit,
    confidence: Int,
    onConfidence:
        (Int) -> Unit,
    selectedSetup:
    String,
    onSetup:
        (String) -> Unit,
    entryText:
    String,
    onEntryText:
        (String) -> Unit,
    stopText:
    String,
    onStopText:
        (String) -> Unit,
    targetText:
    String,
    onTargetText:
        (String) -> Unit,
    onSave:
        () -> Unit
) {

    val setups =
        listOf(
            "No setup",
            "Liquidity sweep",
            "Breakout",
            "Breakout retest",
            "VWAP reclaim",
            "VWAP rejection",
            "Support bounce",
            "Resistance rejection",
            "EMA trend",
            "Volume confirmation",
            "Failed breakout",
            "Other"
        )

    ReplayCard {

        Text(
            text =
                "TRADE DECISION",
            color =
                ReplayCyan,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold,
            letterSpacing =
                1.3.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Text(
            text =
                currentCandle
                    ?.let {
                        "Market price ${formatReplayPrice(it.close)}"
                    }
                    ?: "Current market",
            color =
                ReplayText,
            fontSize =
                19.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        SmallLabel(
            "ACTION"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            DecisionChoiceButton(
                label =
                    "BUY",
                color =
                    ReplayGreen,
                selected =
                    selectedChoice ==
                            ReplayChoice.BUY,
                modifier =
                    Modifier
                        .weight(1f),
                onClick = {
                    onChoice(
                        ReplayChoice.BUY
                    )
                }
            )

            DecisionChoiceButton(
                label =
                    "WAIT",
                color =
                    ReplayCyan,
                selected =
                    selectedChoice ==
                            ReplayChoice.WAIT,
                modifier =
                    Modifier
                        .weight(1f),
                onClick = {
                    onChoice(
                        ReplayChoice.WAIT
                    )
                }
            )

            DecisionChoiceButton(
                label =
                    "PASS",
                color =
                    ReplayRed,
                selected =
                    selectedChoice ==
                            ReplayChoice.PASS,
                modifier =
                    Modifier
                        .weight(1f),
                onClick = {
                    onChoice(
                        ReplayChoice.PASS
                    )
                }
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )

        SmallLabel(
            "CONFIDENCE • $confidence / 10"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    3.dp
                )
        ) {

            (1..10)
                .forEach {
                        score ->

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(
                                    32.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        7.dp
                                    )
                                )
                                .background(
                                    if (
                                        confidence ==
                                        score
                                    ) {
                                        ReplayViolet
                                    } else {
                                        ReplaySurface2
                                    }
                                )
                                .clickable {
                                    onConfidence(
                                        score
                                    )
                                },
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                score.toString(),
                            color =
                                if (
                                    confidence ==
                                    score
                                ) {
                                    ReplayText
                                } else {
                                    ReplaySecondary
                                },
                            fontSize =
                                10.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
        }

        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )

        SmallLabel(
            "SETUP"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        setups.chunked(2)
            .forEach {
                    pair ->

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            7.dp
                        )
                ) {

                    pair.forEach {
                            setup ->

                        SetupButton(
                            text =
                                setup,
                            selected =
                                selectedSetup ==
                                        setup,
                            modifier =
                                Modifier
                                    .weight(1f),
                            onClick = {
                                onSetup(
                                    setup
                                )
                            }
                        )
                    }

                    if (
                        pair.size ==
                        1
                    ) {

                        Spacer(
                            modifier =
                                Modifier
                                    .weight(1f)
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            7.dp
                        )
                )
            }

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        SmallLabel(
            "TRADE PLAN"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        PriceInput(
            label =
                "Entry",
            value =
                entryText,
            onValueChange =
                onEntryText
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            Box(
                modifier =
                    Modifier
                        .weight(1f)
            ) {

                PriceInput(
                    label =
                        "Stop",
                    value =
                        stopText,
                    onValueChange =
                        onStopText
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
            ) {

                PriceInput(
                    label =
                        "Target",
                    value =
                        targetText,
                    onValueChange =
                        onTargetText
                )
            }
        }

        val entry =
            entryText
                .toDoubleOrNull()

        val stop =
            stopText
                .toDoubleOrNull()

        val target =
            targetText
                .toDoubleOrNull()

        if (
            entry != null &&
            stop != null &&
            target != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            RiskRewardPreview(
                entry =
                    entry,
                stop =
                    stop,
                target =
                    target
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )

        Button(
            onClick =
                onSave,
            enabled =
                selectedChoice !=
                        null,
            modifier =
                Modifier
                    .fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    13.dp
                ),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            ReplayCyan,
                        contentColor =
                            ReplayBackground
                    )
        ) {

            Text(
                text =
                    "RECORD DECISION",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    12.sp,
                letterSpacing =
                    0.8.sp
            )
        }
    }
}

@Composable
private fun DecisionChoiceButton(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Box(
        modifier =
            modifier
                .height(
                    44.dp
                )
                .clip(
                    RoundedCornerShape(
                        11.dp
                    )
                )
                .background(
                    if (
                        selected
                    ) {
                        color.copy(
                            alpha =
                                0.25f
                        )
                    } else {
                        ReplaySurface2
                    }
                )
                .border(
                    width =
                        1.dp,
                    color =
                        if (
                            selected
                        ) {
                            color
                        } else {
                            ReplayBorder
                        },
                    shape =
                        RoundedCornerShape(
                            11.dp
                        )
                )
                .clickable {
                    onClick()
                },
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                label,
            color =
                if (
                    selected
                ) {
                    color
                } else {
                    ReplaySecondary
                },
            fontSize =
                11.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun SetupButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick:
        () -> Unit
) {

    Box(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(
                        10.dp
                    )
                )
                .background(
                    if (
                        selected
                    ) {
                        ReplayViolet
                            .copy(
                                alpha =
                                    0.18f
                            )
                    } else {
                        ReplaySurface2
                    }
                )
                .border(
                    width =
                        1.dp,
                    color =
                        if (
                            selected
                        ) {
                            ReplayViolet
                        } else {
                            ReplayBorder
                        },
                    shape =
                        RoundedCornerShape(
                            10.dp
                        )
                )
                .clickable {
                    onClick()
                }
                .padding(
                    horizontal =
                        8.dp,
                    vertical =
                        10.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text =
                text,
            color =
                if (
                    selected
                ) {
                    ReplayViolet
                } else {
                    ReplaySecondary
                },
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun PriceInput(
    label: String,
    value: String,
    onValueChange:
        (String) -> Unit
) {

    OutlinedTextField(
        value =
            value,
        onValueChange = {
                newValue ->

            if (
                newValue.isBlank() ||
                newValue.matches(
                    Regex(
                        "^\\d*\\.?\\d*$"
                    )
                )
            ) {

                onValueChange(
                    newValue
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth(),
        label = {

            Text(
                label
            )
        },
        singleLine =
            true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Decimal
            )
    )
}

@Composable
private fun RiskRewardPreview(
    entry: Double,
    stop: Double,
    target: Double
) {

    val risk =
        abs(
            entry -
                    stop
        )

    val reward =
        abs(
            target -
                    entry
        )

    val ratio =
        if (
            risk >
            0.0
        ) {
            reward /
                    risk
        } else {
            0.0
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        10.dp
                    )
                )
                .background(
                    ReplaySurface2
                )
                .padding(
                    12.dp
                )
    ) {

        Text(
            text =
                "RISK / REWARD",
            color =
                ReplaySecondary,
            fontSize =
                10.sp,
            modifier =
                Modifier
                    .weight(1f)
        )

        Text(
            text =
                String.format(
                    Locale.US,
                    "1 : %.2f",
                    ratio
                ),
            color =
                ReplayGold,
            fontSize =
                12.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun ReplayStatusCard(
    totalCandles: Int,
    visibleCount: Int
) {

    val hidden =
        (
                totalCandles -
                        visibleCount
                )
            .coerceAtLeast(
                0
            )

    ReplayCard {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier
                        .weight(1f)
            ) {

                SmallLabel(
                    "VISIBLE"
                )

                Text(
                    text =
                        "$visibleCount candles",
                    color =
                        ReplayText,
                    fontSize =
                        17.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End
            ) {

                SmallLabel(
                    "FUTURE HIDDEN"
                )

                Text(
                    text =
                        "$hidden candles",
                    color =
                        ReplayViolet,
                    fontSize =
                        17.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReplayControls(
    visibleCount: Int,
    totalCandles: Int,
    onNext:
        () -> Unit,
    onNextFive:
        () -> Unit,
    onRestart:
        () -> Unit
) {

    ReplayCard {

        SmallLabel(
            "MARKET CONTROL"
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            Button(
                onClick =
                    onRestart,
                modifier =
                    Modifier
                        .weight(1f),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                ReplaySurface2,
                            contentColor =
                                ReplaySecondary
                        )
            ) {

                Text(
                    text =
                        "RESET",
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Button(
                onClick =
                    onNext,
                enabled =
                    visibleCount <
                            totalCandles,
                modifier =
                    Modifier
                        .weight(1f),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                ReplayCyan,
                            contentColor =
                                ReplayBackground
                        )
            ) {

                Text(
                    text =
                        "+1",
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Button(
                onClick =
                    onNextFive,
                enabled =
                    visibleCount <
                            totalCandles,
                modifier =
                    Modifier
                        .weight(1f),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                ReplayViolet,
                            contentColor =
                                ReplayText
                        )
            ) {

                Text(
                    text =
                        "+5",
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DecisionHistory(
    decisions:
    List<ReplayDecision>
) {

    ReplayCard {

        Text(
            text =
                "TRADING DNA RECORD",
            color =
                ReplayCyan,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold,
            letterSpacing =
                1.2.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        decisions
            .takeLast(
                10
            )
            .reversed()
            .forEach {
                    decision ->

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical =
                                    7.dp
                            )
                ) {

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                    ) {

                        Text(
                            text =
                                decision
                                    .choice
                                    .name,
                            color =
                                decisionColor(
                                    decision.choice
                                ),
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                "${decision.setup} • ${formatReplayPrice(decision.marketPrice)}",
                            color =
                                ReplaySecondary,
                            fontSize =
                                11.sp
                        )
                    }

                    Text(
                        text =
                            "${decision.confidence}/10",
                        color =
                            ReplayViolet,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
    }
}

@Composable
private fun ReplayCompleteCard(
    trade: TradeEpisode,
    decisions:
    List<ReplayDecision>
) {

    val averageConfidence =
        if (
            decisions
                .isNotEmpty()
        ) {

            decisions
                .map {
                    it.confidence
                }
                .average()

        } else {
            0.0
        }

    ReplayCard {

        Text(
            text =
                "REPLAY COMPLETE",
            color =
                ReplayGreen,
            fontSize =
                11.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Text(
            text =
                "Compare your current process with the trade you actually made.",
            color =
                ReplayText,
            fontSize =
                20.sp,
            lineHeight =
                27.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        ReplayStatRow(
            label =
                "Historical entry",
            value =
                formatReplayPrice(
                    trade
                        .averageEntryPrice
                )
        )

        ReplayStatRow(
            label =
                "Historical exit",
            value =
                trade
                    .averageExitPrice
                    ?.let {
                        formatReplayPrice(
                            it
                        )
                    }
                    ?: "OPEN"
        )

        ReplayStatRow(
            label =
                "Historical P&L",
            value =
                String.format(
                    Locale.US,
                    "\$%,.2f",
                    trade.realizedPnl
                )
        )

        ReplayStatRow(
            label =
                "Decisions",
            value =
                decisions.size
                    .toString()
        )

        ReplayStatRow(
            label =
                "Avg. confidence",
            value =
                String.format(
                    Locale.US,
                    "%.1f / 10",
                    averageConfidence
                )
        )
    }
}

@Composable
private fun ReplayStatRow(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        6.dp
                )
    ) {

        Text(
            text =
                label,
            color =
                ReplaySecondary,
            modifier =
                Modifier
                    .weight(1f)
        )

        Text(
            text =
                value,
            color =
                ReplayText,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReplayTimeframeSelector(
    selected: String,
    onSelected:
        (String) -> Unit
) {

    val options =
        listOf(
            "5Min" to "5M",
            "15Min" to "15M",
            "1Hour" to "1H"
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {

        options
            .forEach {
                    option ->

                val active =
                    selected ==
                            option.first

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(
                                RoundedCornerShape(
                                    11.dp
                                )
                            )
                            .background(
                                if (
                                    active
                                ) {
                                    ReplayCyan
                                } else {
                                    ReplaySurface
                                }
                            )
                            .border(
                                width =
                                    1.dp,
                                color =
                                    if (
                                        active
                                    ) {
                                        ReplayCyan
                                    } else {
                                        ReplayBorder
                                    },
                                shape =
                                    RoundedCornerShape(
                                        11.dp
                                    )
                            )
                            .clickable {
                                onSelected(
                                    option.first
                                )
                            }
                            .padding(
                                vertical =
                                    10.dp
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            option.second,
                        color =
                            if (
                                active
                            ) {
                                ReplayBackground
                            } else {
                                ReplaySecondary
                            },
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
private fun ReplayLoading(
    symbol: String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    350.dp
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment
                    .CenterHorizontally
        ) {

            CircularProgressIndicator(
                color =
                    ReplayCyan
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            Text(
                text =
                    "Loading $symbol replay...",
                color =
                    ReplaySecondary
            )
        }
    }
}

@Composable
private fun SmallLabel(
    text: String
) {

    Text(
        text =
            text,
        color =
            ReplaySecondary,
        fontSize =
            9.sp,
        fontWeight =
            FontWeight.Bold,
        letterSpacing =
            1.1.sp
    )
}

@Composable
private fun ReplayCard(
    content:
    @Composable
    ColumnScope.() -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        20.dp
                    )
                )
                .background(
                    ReplaySurface
                )
                .border(
                    width =
                        1.dp,
                    color =
                        ReplayBorder,
                    shape =
                        RoundedCornerShape(
                            20.dp
                        )
                )
                .padding(
                    18.dp
                ),
        content =
            content
    )
}

private fun replayDateRange(
    trade:
    TradeEpisode
): Pair<String, String> {

    val formatter =
        DateTimeFormatter
            .ofPattern(
                "M/d/yyyy",
                Locale.US
            )

    val open =
        LocalDate.parse(
            trade.openDate,
            formatter
        )

    val close =
        trade.closeDate
            ?.let {

                LocalDate.parse(
                    it,
                    formatter
                )
            }
            ?: open

    val start =
        open
            .minusDays(
                2
            )
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    val end =
        close
            .plusDays(
                1
            )
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    return start to end
}

private fun decisionColor(
    choice:
    ReplayChoice
): Color {

    return when (
        choice
    ) {

        ReplayChoice.BUY ->
            ReplayGreen

        ReplayChoice.WAIT ->
            ReplayCyan

        ReplayChoice.PASS ->
            ReplayRed
    }
}

private fun replayScoreColor(
    score:
    Int
): Color {

    return when {

        score >=
                70 ->
            ReplayGreen

        score <=
                35 ->
            ReplayRed

        else ->
            ReplayGold
    }
}

private fun normalizeSetup(
    value:
    String
): String {

    return value
        .lowercase()
        .replace(
            "bullish ",
            ""
        )
        .replace(
            "bearish ",
            ""
        )
        .replace(
            "volume confirmation",
            "volume"
        )
        .replace(
            "volume expansion",
            "volume"
        )
        .trim()
}

private fun formatReplayPrice(
    value:
    Double
): String {

    return if (
        value <
        10.0
    ) {

        String.format(
            Locale.US,
            "\$%.3f",
            value
        )

    } else {

        String.format(
            Locale.US,
            "\$%.2f",
            value
        )
    }
}