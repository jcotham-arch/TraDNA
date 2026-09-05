package com.tradna.APP.lab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.market.AlpacaMarketData
import com.tradna.APP.replay.CounterfactualEngine
import com.tradna.APP.replay.HistoricalTradeAnalysisEngine
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LabBackground = Color(0xFF07090D)
private val LabSurface = Color(0xFF0E1219)
private val LabSurface2 = Color(0xFF141A23)
private val LabBorder = Color(0xFF222B38)

private val LabText = Color(0xFFF4F7FB)
private val LabSecondary = Color(0xFF8D98A8)

private val LabCyan = Color(0xFF72E7FF)
private val LabGreen = Color(0xFF39D6A0)
private val LabRed = Color(0xFFFF657A)
private val LabGold = Color(0xFFFFC857)
private val LabViolet = Color(0xFF9B7CFF)

private data class TrainingAttempt(
    val success: Boolean,
    val reason: String? = null,
    val timeframe: String? = null,
    val candleCount: Int = 0
)

@Composable
fun AgentLabDashboard(
    trades: List<TradeEpisode>
) {

    val context =
        LocalContext.current

    var showAgentScreen by remember {
        mutableStateOf(
            false
        )
    }

    var showJournalScreen by remember {
        mutableStateOf(
            false
        )
    }

    var showPaperSandbox by remember {
        mutableStateOf(false)
    }

    var showBackendConnection by remember {
        mutableStateOf(false)
    }

    BackHandler(
        enabled =
            showAgentScreen ||
                    showJournalScreen ||
                    showPaperSandbox ||
                    showBackendConnection
    ) {

        when {

            showBackendConnection ->
                showBackendConnection = false

            showPaperSandbox ->
                showPaperSandbox = false

            showJournalScreen ->
                showJournalScreen =
                    false

            showAgentScreen ->
                showAgentScreen =
                    false
        }
    }

    var records by remember {
        mutableStateOf(
            AgentTrainingStorage.loadRecords(
                context
            )
        )
    }

    var isTraining by remember {
        mutableStateOf(false)
    }

    var trainingRunId by remember {
        mutableIntStateOf(0)
    }

    var completedCount by remember {
        mutableIntStateOf(0)
    }

    var failedCount by remember {
        mutableIntStateOf(0)
    }

    var processedCount by remember {
        mutableIntStateOf(0)
    }

    var totalThisPass by remember {
        mutableIntStateOf(0)
    }

    var currentSymbol by remember {
        mutableStateOf("")
    }

    var lastFailure by remember {
        mutableStateOf<String?>(null)
    }

    var statusMessage by remember {
        mutableStateOf(
            if (records.isEmpty()) {
                "No historical trades have trained the agent yet."
            } else {
                "${records.size} historical trades are in the Lab dataset."
            }
        )
    }

    val eligibleTrades =
        remember(trades) {
            trades.filter {
                it.symbol.isNotBlank() &&
                        it.averageEntryPrice > 0.0
            }
        }

    LaunchedEffect(
        trainingRunId
    ) {

        if (
            trainingRunId == 0
        ) {
            return@LaunchedEffect
        }
        isTraining = true

        completedCount = 0
        failedCount = 0
        processedCount = 0
        totalThisPass = 0
        currentSymbol = ""
        lastFailure = null

        val existingTradeIds =
            AgentTrainingStorage
                .loadRecords(
                    context
                )
                .map {
                    it.tradeId
                }
                .toSet()

        val tradesToAnalyze =
            eligibleTrades
                .filter {
                    it.id !in
                            existingTradeIds
                }
                .sortedBy {
                    safeTradeDate(
                        it.openDate
                    )
                }

        totalThisPass =
            tradesToAnalyze.size

        if (
            tradesToAnalyze
                .isEmpty()
        ) {

            records =
                AgentTrainingStorage
                    .loadRecords(
                        context
                    )

            statusMessage =
                "Every eligible historical trade is already in the Lab dataset."

            isTraining = false
            return@LaunchedEffect
        }

        /*
         * IMPORTANT:
         *
         * The earlier version performed the market-data work
         * directly inside the Compose effect. If an API call
         * was slow, the Lab could appear frozen after the first
         * failure.
         *
         * Each trade now runs on Dispatchers.IO and has its own
         * timeout. A bad trade cannot stop the rest of the pass.
         */
        for (trade in tradesToAnalyze) {

            currentSymbol =
                trade.symbol

            statusMessage =
                "Analyzing ${trade.symbol} • ${processedCount + 1} of $totalThisPass"

            val attempt =
                analyzeAndStoreTrade(
                    context = context,
                    trade = trade
                )

            if (
                attempt.success
            ) {

                completedCount++

                records =
                    AgentTrainingStorage
                        .loadRecords(
                            context
                        )

            } else {

                failedCount++

                lastFailure =
                    attempt.reason
                        ?: "${trade.symbol}: analysis failed."
            }

            processedCount++

            statusMessage =
                buildString {
                    append(
                        "Processed $processedCount of $totalThisPass • trained $completedCount • skipped $failedCount"
                    )

                    if (
                        attempt.timeframe != null &&
                        attempt.candleCount > 0
                    ) {
                        append(
                            " • ${attempt.timeframe} • ${attempt.candleCount} candles"
                        )
                    }
                }

            /*
             * Yield briefly so Compose can repaint the progress
             * after every trade.
             */
            delay(
                120L
            )
        }

        currentSymbol =
            ""

        records =
            AgentTrainingStorage
                .loadRecords(
                    context
                )

        statusMessage =
            buildString {

                append(
                    "Training pass complete. "
                )

                append(
                    "${records.size} records are now in the Lab dataset."
                )

                if (
                    failedCount >
                    0
                ) {

                    append(
                        " $failedCount trade"
                    )

                    if (
                        failedCount !=
                        1
                    ) {
                        append(
                            "s"
                        )
                    }

                    append(
                        " were skipped instead of stopping the run."
                    )
                }
            }

        isTraining =
            false
    }

    val metrics =
        remember(records) {
            AgentLabMetrics.from(
                records
            )
        }

    val patternProfile =
        remember(records) {
            PatternLearningEngine.analyze(
                records
            )
        }

    val predictionCount =
        AgentPredictionJournal.predictionCount(
            context
        )

    val linkedOutcomeCount =
        AgentPredictionJournal.linkedOutcomeCount(
            context
        )

    if (
        showPaperSandbox
    ) {
        PaperSandboxScreen(onBack = { showPaperSandbox = false })
        return
    }

    if (showBackendConnection) {
        BackendConnectionScreen(onBack = { showBackendConnection = false })
        return
    }

    if (
        showJournalScreen
    ) {

        AgentPredictionJournalScreen(
            trades =
                trades,
            onBack = {
                showJournalScreen =
                    false
            }
        )

        return
    }

    if (
        showAgentScreen
    ) {

        AgentRecommendationScreen(
            onBack = {
                showAgentScreen =
                    false
            }
        )

        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    LabBackground
                )
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal =
                        20.dp,
                    vertical =
                        20.dp
                )
    ) {

        LabHeader(
            records =
                records.size,
            readiness =
                metrics.readinessScore
        )

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        DatasetCard(
            records =
                records.size,
            eligibleTrades =
                eligibleTrades.size,
            readiness =
                metrics.readinessScore,
            statusMessage =
                statusMessage
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        LabCard {
            LabSectionLabel(text = "LIVE CONNECTION", color = LabCyan)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Connect this phone to the secure TraDNA backend and inspect Robinhood synchronization health.",
                color = LabSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showBackendConnection = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LabCyan,
                    contentColor = LabBackground
                )
            ) { Text("BACKEND CONNECTION", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(14.dp))

        LabCard {
            LabSectionLabel(text = "PAPER SANDBOX", color = LabGreen)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$5,000 simulated account • 10% maximum per entry",
                color = LabText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Test frozen Agent recommendations without sending anything to Robinhood.",
                color = LabSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { showPaperSandbox = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LabGreen,
                    contentColor = LabBackground
                )
            ) {
                Text("OPEN PAPER SANDBOX", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(14.dp))

        if (
            isTraining
        ) {

            TrainingProgressCard(
                currentSymbol =
                    currentSymbol,
                processed =
                    processedCount,
                completed =
                    completedCount,
                failed =
                    failedCount,
                total =
                    totalThisPass,
                lastFailure =
                    lastFailure
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )
        } else if (
            lastFailure !=
            null
        ) {

            LastFailureCard(
                message =
                    lastFailure!!
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )
        }

        LabCard {

            LabSectionLabel(
                text =
                    "TRADNA AGENT",
                color =
                    LabViolet
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            Text(
                text =
                    "Use what the Lab has learned on a current setup.",
                color =
                    LabText,
                fontSize =
                    19.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        7.dp
                    )
            )

            Text(
                text =
                    if (
                        records.isEmpty()
                    ) {
                        "Train the Lab first. Once training records exist, the Agent can compare a current stock setup with your historical patterns."
                    } else {
                        "Analyze a stock symbol against ${records.size} trained historical records, your pattern profile, and the closest historical matches."
                    },
                color =
                    LabSecondary,
                fontSize =
                    12.sp,
                lineHeight =
                    18.sp
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            Button(
                onClick = {
                    showAgentScreen =
                        true
                },
                enabled =
                    records.isNotEmpty() &&
                            !isTraining,
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        14.dp
                    ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            LabViolet,
                        contentColor =
                            LabText,
                        disabledContainerColor =
                            LabSurface2,
                        disabledContentColor =
                            LabSecondary
                    )
            ) {

                Text(
                    text =
                        "OPEN TRADNA AGENT",
                    fontWeight =
                        FontWeight.Bold,
                    letterSpacing =
                        0.5.sp
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        LabCard {

            LabSectionLabel(
                text =
                    "AGENT PREDICTION JOURNAL",
                color =
                    LabGreen
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            Text(
                text =
                    "Measure what the Agent said before the outcome was known.",
                color =
                    LabText,
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

            LabValueRow(
                label =
                    "Predictions saved",
                value =
                    predictionCount
                        .toString(),
                valueColor =
                    LabCyan
            )

            LabValueRow(
                label =
                    "Outcomes linked",
                value =
                    linkedOutcomeCount
                        .toString(),
                valueColor =
                    LabGreen
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            Text(
                text =
                    if (
                        predictionCount ==
                        0
                    ) {
                        "Save an Agent recommendation first. The Journal will preserve the original prediction and later compare it with your imported trade outcome."
                    } else {
                        "Open the Journal to review saved predictions and link new Robinhood trade outcomes."
                    },
                color =
                    LabSecondary,
                fontSize =
                    12.sp,
                lineHeight =
                    18.sp
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            Button(
                onClick = {
                    showJournalScreen =
                        true
                },
                enabled =
                    predictionCount >
                            0 &&
                            !isTraining,
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(
                        14.dp
                    ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            LabGreen,
                        contentColor =
                            LabBackground,
                        disabledContainerColor =
                            LabSurface2,
                        disabledContentColor =
                            LabSecondary
                    )
            ) {

                Text(
                    text =
                        "OPEN AGENT JOURNAL",
                    fontWeight =
                        FontWeight.Bold,
                    letterSpacing =
                        0.5.sp
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        Button(
            onClick = {
                trainingRunId++
            },
            enabled =
                !isTraining &&
                        eligibleTrades
                            .isNotEmpty(),
            modifier =
                Modifier
                    .fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    14.dp
                ),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            LabCyan,
                        contentColor =
                            LabBackground
                    )
        ) {

            Text(
                text =
                    if (
                        records.isEmpty()
                    ) {
                        "TRAIN FROM HISTORICAL TRADES"
                    } else {
                        "UPDATE AGENT TRAINING DATA"
                    },
                fontWeight =
                    FontWeight.Bold,
                letterSpacing =
                    0.5.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        TradingDnaCard(
            profile =
                patternProfile
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        CoachingPrioritiesCard(
            profile =
                patternProfile
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        GuardrailCard()

        Spacer(
            modifier =
                Modifier.height(
                    28.dp
                )
        )
    }
}

private suspend fun analyzeAndStoreTrade(
    context: android.content.Context,
    trade: TradeEpisode
): TrainingAttempt {

    return try {

        val range =
            labDateRange(
                trade
            )

        /*
         * ADAPTIVE TIMEFRAME
         *
         * Counterfactual analysis repeatedly evaluates technical
         * indicators across the available history. Feeding thousands
         * of 15-minute candles into that process can become very slow.
         *
         * Batch Lab training uses hourly bars for trades up to 45 days.
         * Longer trades use daily bars. Detailed 15-minute analysis remains
         * available in the individual Historical Trade Review.
         */
        val timeframe =
            trainingTimeframe(
                trade
            )

        val loadedCandles =
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

        if (
            loadedCandles.isEmpty()
        ) {

            TrainingAttempt(
                success = false,
                reason =
                    "${trade.symbol}: no historical candles were returned for ${trade.openDate}.",
                timeframe =
                    timeframe,
                candleCount =
                    0
            )

        } else {

            /*
             * HARD SAFETY CAP
             *
             * Adaptive timeframes should normally keep the dataset
             * small already. This additional cap prevents an unusual
             * market-data response from sending thousands of candles
             * into the O(n²)-style counterfactual calculations.
             *
             * We preserve the beginning and end of the history and
             * evenly sample the middle so entry context and post-exit
             * context both remain represented.
             */
            val candles =
                limitTrainingCandles(
                    loadedCandles,
                    maxCandles =
                        180
                )

            val analysis =
                HistoricalTradeAnalysisEngine
                    .analyze(
                        trade =
                            trade,
                        candles =
                            candles
                    )

            val counterfactual =
                CounterfactualEngine
                    .analyze(
                        trade =
                            trade,
                        candles =
                            candles,
                        historicalAnalysis =
                            analysis
                    )

            val record =
                AgentTrainingEngine
                    .buildRecord(
                        trade =
                            trade,
                        analysis =
                            analysis,
                        counterfactualReport =
                            counterfactual
                    )

            AgentTrainingStorage
                .saveRecord(
                    context =
                        context,
                    record =
                        record
                )

            TrainingAttempt(
                success =
                    true,
                timeframe =
                    timeframe,
                candleCount =
                    candles.size
            )
        }

    } catch (
        e: Exception
    ) {

        TrainingAttempt(
            success = false,
            reason =
                "${trade.symbol}: ${e.message ?: e::class.java.simpleName}"
        )
    }
}

/*
 * Choose the coarsest timeframe needed to preserve useful structure
 * without making the training calculation unnecessarily expensive.
 */
private fun trainingTimeframe(
    trade: TradeEpisode
): String {

    val open =
        safeTradeDate(
            trade.openDate
        )

    val close =
        trade.closeDate
            ?.let {
                safeTradeDate(
                    it
                )
            }
            ?: open

    val holdingDays =
        if (
            open == LocalDate.MAX ||
            close == LocalDate.MAX
        ) {
            0L
        } else {
            kotlin.math.abs(
                java.time.temporal.ChronoUnit
                    .DAYS
                    .between(
                        open,
                        close
                    )
            )
        }

    return when {

        holdingDays <= 45L ->
            "1Hour"

        else ->
            "1Day"
    }
}

/*
 * Preserve the full shape of an unexpectedly large series without
 * handing every bar to the expensive teaching/counterfactual engines.
 */
private fun limitTrainingCandles(
    candles: List<com.tradna.APP.market.Candle>,
    maxCandles: Int
): List<com.tradna.APP.market.Candle> {

    if (
        candles.size <= maxCandles
    ) {
        return candles
    }

    if (
        maxCandles < 3
    ) {
        return candles.take(
            maxCandles
        )
    }

    val result =
        ArrayList<com.tradna.APP.market.Candle>(
            maxCandles
        )

    result.add(
        candles.first()
    )

    val interiorSlots =
        maxCandles - 2

    val step =
        (
                candles.size - 2
                )
            .toDouble() /
                interiorSlots
                    .toDouble()

    for (
    slot in 0 until interiorSlots
    ) {

        val rawIndex =
            1.0 +
                    (
                            slot *
                                    step
                            )

        val index =
            rawIndex
                .toInt()
                .coerceIn(
                    1,
                    candles.lastIndex - 1
                )

        val candle =
            candles[index]

        if (
            result.lastOrNull()
                ?.timestamp !=
            candle.timestamp
        ) {

            result.add(
                candle
            )
        }
    }

    if (
        result.lastOrNull()
            ?.timestamp !=
        candles.last()
            .timestamp
    ) {

        result.add(
            candles.last()
        )
    }

    return result
        .take(
            maxCandles
        )
}

private data class AgentLabMetrics(
    val readinessScore: Int,
    val profitableRate: Double?,
    val strongEntryCount: Int,
    val efficientEntryCount: Int,
    val efficientExitCount: Int,
    val earlyExitCount: Int,
    val highAdverseCount: Int,
    val alternativeBeatCount: Int,
    val averageTechnicalScore: Double?,
    val averageEntryEfficiency: Double?,
    val averageExitEfficiency: Double?,
    val averageMissedUpside: Double?,
    val averageAlternativeImprovement: Double?
) {

    companion object {

        fun from(
            records:
            List<AgentTrainingRecord>
        ): AgentLabMetrics {

            val count =
                records.size

            val profitable =
                records.count {
                    it.profitableTrade
                }

            val technicalScores =
                records.mapNotNull {
                    it.entryTechnicalScore
                        ?.toDouble()
                }

            val entryEfficiencies =
                records.mapNotNull {
                    it.entryEfficiencyScore
                        ?.toDouble()
                }

            val exitEfficiencies =
                records.mapNotNull {
                    it.exitEfficiencyScore
                        ?.toDouble()
                }

            val missedUpside =
                records.mapNotNull {
                    it.missedUpsidePercent
                }

            val improvements =
                records.mapNotNull {
                    it.improvementVsActualPercent
                }

            val readiness =
                when {

                    count >= 50 ->
                        90

                    count >= 30 ->
                        78

                    count >= 20 ->
                        65

                    count >= 10 ->
                        50

                    count >= 5 ->
                        35

                    count > 0 ->
                        20

                    else ->
                        0
                }

            return AgentLabMetrics(
                readinessScore =
                    readiness,

                profitableRate =
                    if (
                        count >
                        0
                    ) {

                        profitable
                            .toDouble() /
                                count *
                                100.0

                    } else {
                        null
                    },

                strongEntryCount =
                    records.count {
                        it.strongTechnicalEntry
                    },

                efficientEntryCount =
                    records.count {
                        it.efficientEntry
                    },

                efficientExitCount =
                    records.count {
                        it.efficientExit
                    },

                earlyExitCount =
                    records.count {
                        it.earlyExitCandidate
                    },

                highAdverseCount =
                    records.count {
                        it.highAdverseExcursion
                    },

                alternativeBeatCount =
                    records.count {
                        it.alternativeOutperformedActual
                    },

                averageTechnicalScore =
                    technicalScores
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.average(),

                averageEntryEfficiency =
                    entryEfficiencies
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.average(),

                averageExitEfficiency =
                    exitEfficiencies
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.average(),

                averageMissedUpside =
                    missedUpside
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.average(),

                averageAlternativeImprovement =
                    improvements
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.average()
            )
        }
    }
}

@Composable
private fun LabHeader(
    records: Int,
    readiness: Int
) {

    Column {

        Text(
            text =
                "AGENT LAB",
            color =
                LabText,
            fontSize =
                30.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(
                    4.dp
                )
        )

        Text(
            text =
                "Historical evidence → personalized trading intelligence",
            color =
                LabSecondary,
            fontSize =
                13.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                LabSmallLabel(
                    "TRAINING RECORDS"
                )

                Text(
                    text =
                        records.toString(),
                    color =
                        LabCyan,
                    fontSize =
                        26.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End
            ) {

                LabSmallLabel(
                    "READINESS"
                )

                Text(
                    text =
                        "$readiness%",
                    color =
                        readinessColor(
                            readiness
                        ),
                    fontSize =
                        26.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DatasetCard(
    records: Int,
    eligibleTrades: Int,
    readiness: Int,
    statusMessage: String
) {

    LabCard {

        LabSectionLabel(
            text =
                "TRAINING DATASET",
            color =
                LabCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                if (
                    records ==
                    0
                ) {
                    "Build the agent's first evidence base."
                } else {
                    "The agent is learning from your historical behavior."
                },
            color =
                LabText,
            fontSize =
                20.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        LabValueRow(
            label =
                "Historical trades available",
            value =
                eligibleTrades
                    .toString()
        )

        LabValueRow(
            label =
                "Training records stored",
            value =
                records
                    .toString(),
            valueColor =
                LabCyan
        )

        LabValueRow(
            label =
                "Agent readiness",
            value =
                "$readiness%",
            valueColor =
                readinessColor(
                    readiness
                )
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        LinearProgressIndicator(
            progress = {
                (
                        readiness /
                                100f
                        )
                    .coerceIn(
                        0f,
                        1f
                    )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        7.dp
                    ),
            color =
                readinessColor(
                    readiness
                ),
            trackColor =
                LabSurface2
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                statusMessage,
            color =
                LabSecondary,
            fontSize =
                12.sp,
            lineHeight =
                18.sp
        )
    }
}

@Composable
private fun TrainingProgressCard(
    currentSymbol: String,
    processed: Int,
    completed: Int,
    failed: Int,
    total: Int,
    lastFailure: String?
) {

    LabCard {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            CircularProgressIndicator(
                modifier =
                    Modifier.height(
                        27.dp
                    ),
                color =
                    LabViolet,
                strokeWidth =
                    3.dp
            )

            Spacer(
                modifier =
                    Modifier.padding(
                        horizontal =
                            7.dp
                    )
            )

            Column {

                Text(
                    text =
                        "TRAINING AGENT",
                    color =
                        LabViolet,
                    fontSize =
                        10.sp,
                    fontWeight =
                        FontWeight.Bold,
                    letterSpacing =
                        1.1.sp
                )

                Text(
                    text =
                        if (
                            currentSymbol
                                .isBlank()
                        ) {
                            "Preparing..."
                        } else {
                            "Analyzing $currentSymbol"
                        },
                    color =
                        LabText,
                    fontSize =
                        16.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        LabValueRow(
            label =
                "Progress",
            value =
                "$processed / $total"
        )

        LabValueRow(
            label =
                "Trained",
            value =
                completed.toString(),
            valueColor =
                LabGreen
        )

        LabValueRow(
            label =
                "Skipped",
            value =
                failed.toString(),
            valueColor =
                if (
                    failed >
                    0
                ) {
                    LabGold
                } else {
                    LabSecondary
                }
        )

        if (
            lastFailure !=
            null
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            Text(
                text =
                    "Last skip: $lastFailure",
                color =
                    LabGold,
                fontSize =
                    11.sp,
                lineHeight =
                    17.sp
            )
        }
    }
}

@Composable
private fun LastFailureCard(
    message: String
) {

    LabCard {

        LabSectionLabel(
            text =
                "LAST SKIPPED TRADE",
            color =
                LabGold
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                message,
            color =
                LabSecondary,
            fontSize =
                12.sp,
            lineHeight =
                18.sp
        )
    }
}

@Composable
private fun TradingDnaCard(
    profile: AgentPatternProfile
) {
    LabCard {
        LabSectionLabel(
            text = "YOUR TRADING DNA",
            color = LabViolet
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (profile.totalRecords == 0) {
                "Train historical trades to measure your preferred symbols and VWAP-relative entry behavior."
            } else {
                "What has worked in your own history—not a generic market rule."
            },
            color = LabSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        if (profile.totalRecords == 0) return@LabCard

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                LabSmallLabel("TRAINED TRADES")
                Text(
                    text = profile.totalRecords.toString(),
                    color = LabText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                LabSmallLabel("CONFIDENCE")
                Text(
                    text = "${profile.profileConfidencePercent}%",
                    color = readinessColor(profile.profileConfidencePercent),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (profile.symbolPatterns.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            LabSmallLabel("SYMBOL EXPERIENCE")
            Spacer(Modifier.height(6.dp))

            profile.symbolPatterns.take(5).forEach { symbol ->
                LabValueRow(
                    label = "${symbol.symbol} • ${symbol.trades} trade${if (symbol.trades == 1) "" else "s"}",
                    value = buildString {
                        symbol.profitableRatePercent?.let { append(formatPercent(it)) }
                        symbol.averageReturnPercent?.let {
                            if (isNotEmpty()) append(" • ")
                            append(formatSignedPercent(it))
                        }
                    }.ifBlank { "—" },
                    valueColor = if (symbol.realizedPnl >= 0.0) LabGreen else LabRed
                )
            }
        }

        if (profile.vwapPatterns.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            LabSmallLabel("ENTRY VS VWAP")
            Spacer(Modifier.height(6.dp))

            profile.vwapPatterns.forEach { pattern ->
                LabValueRow(
                    label = "${pattern.title} • ${pattern.sampleSize}",
                    value = buildString {
                        pattern.profitableRatePercent?.let { append(formatPercent(it)) }
                        pattern.averageReturnPercent?.let {
                            if (isNotEmpty()) append(" • ")
                            append(formatSignedPercent(it))
                        }
                    }.ifBlank { "—" },
                    valueColor = readinessColor(pattern.confidencePercent)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Rates show historical outcomes with sample size. They do not authorize or trigger trades.",
            color = LabSecondary,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun PatternOverviewCard(
    metrics:
    AgentLabMetrics
) {

    LabCard {

        LabSectionLabel(
            text =
                "BEHAVIORAL PATTERNS",
            color =
                LabViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        if (
            metrics
                .profitableRate ==
            null
        ) {

            Text(
                text =
                    "Train the Lab from historical trades to reveal recurring behavior.",
                color =
                    LabSecondary,
                fontSize =
                    13.sp,
                lineHeight =
                    19.sp
            )

            return@LabCard
        }

        LabValueRow(
            label =
                "Profitable historical trades",
            value =
                formatPercent(
                    metrics
                        .profitableRate
                ),
            valueColor =
                LabGreen
        )

        LabValueRow(
            label =
                "Strong technical entries",
            value =
                metrics
                    .strongEntryCount
                    .toString()
        )

        LabValueRow(
            label =
                "Efficient entries",
            value =
                metrics
                    .efficientEntryCount
                    .toString()
        )

        LabValueRow(
            label =
                "Efficient exits",
            value =
                metrics
                    .efficientExitCount
                    .toString()
        )

        LabValueRow(
            label =
                "Early-exit candidates",
            value =
                metrics
                    .earlyExitCount
                    .toString(),
            valueColor =
                if (
                    metrics
                        .earlyExitCount >
                    0
                ) {
                    LabGold
                } else {
                    LabGreen
                }
        )

        LabValueRow(
            label =
                "High adverse excursions",
            value =
                metrics
                    .highAdverseCount
                    .toString(),
            valueColor =
                if (
                    metrics
                        .highAdverseCount >
                    0
                ) {
                    LabRed
                } else {
                    LabGreen
                }
        )

        LabValueRow(
            label =
                "Alternatives beat actual",
            value =
                metrics
                    .alternativeBeatCount
                    .toString(),
            valueColor =
                LabCyan
        )
    }
}

@Composable
private fun AgentLearningCard(
    metrics:
    AgentLabMetrics
) {

    LabCard {

        LabSectionLabel(
            text =
                "WHAT THE AGENT IS LEARNING",
            color =
                LabCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        LearningMetric(
            label =
                "Average technical score at entry",
            value =
                metrics
                    .averageTechnicalScore
                    ?.let {
                        String.format(
                            Locale.US,
                            "%.1f / 100",
                            it
                        )
                    }
                    ?: "—"
        )

        LearningMetric(
            label =
                "Average entry efficiency",
            value =
                metrics
                    .averageEntryEfficiency
                    ?.let {
                        String.format(
                            Locale.US,
                            "%.1f / 100",
                            it
                        )
                    }
                    ?: "—"
        )

        LearningMetric(
            label =
                "Average exit efficiency",
            value =
                metrics
                    .averageExitEfficiency
                    ?.let {
                        String.format(
                            Locale.US,
                            "%.1f / 100",
                            it
                        )
                    }
                    ?: "—"
        )

        LearningMetric(
            label =
                "Average missed upside",
            value =
                metrics
                    .averageMissedUpside
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—"
        )

        LearningMetric(
            label =
                "Average best-alternative edge",
            value =
                metrics
                    .averageAlternativeImprovement
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—"
        )
    }
}

@Composable
private fun StrategyEvidenceCard(
    records:
    List<AgentTrainingRecord>
) {

    val strategies =
        records
            .mapNotNull {
                it.bestAlternativeTitle
            }
            .groupingBy {
                it
            }
            .eachCount()
            .entries
            .sortedByDescending {
                it.value
            }
            .take(
                5
            )

    LabCard {

        LabSectionLabel(
            text =
                "STRATEGY EVIDENCE",
            color =
                LabGold
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        if (
            strategies
                .isEmpty()
        ) {

            Text(
                text =
                    "No counterfactual strategy has accumulated enough evidence yet.",
                color =
                    LabSecondary,
                fontSize =
                    13.sp,
                lineHeight =
                    19.sp
            )

        } else {

            Text(
                text =
                    "Most frequent best-performing alternatives",
                color =
                    LabText,
                fontSize =
                    16.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            strategies
                .forEachIndexed {
                        index,
                        entry ->

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
                                "${index + 1}",
                            color =
                                LabGold,
                            fontWeight =
                                FontWeight.Bold,
                            modifier =
                                Modifier.padding(
                                    end =
                                        10.dp
                                )
                        )

                        Text(
                            text =
                                entry.key,
                            color =
                                LabText,
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )

                        Text(
                            text =
                                "${entry.value}x",
                            color =
                                LabCyan,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
        }
    }
}


@Composable
private fun PersonalPatternProfileCard(
    profile: AgentPatternProfile
) {

    LabCard {

        LabSectionLabel(
            text =
                "PERSONAL PATTERN PROFILE",
            color =
                LabViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                LabSmallLabel(
                    "PROFILE CONFIDENCE"
                )

                Text(
                    text =
                        "${profile.profileConfidencePercent}%",
                    color =
                        readinessColor(
                            profile.profileConfidencePercent
                        ),
                    fontSize =
                        24.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End
            ) {

                LabSmallLabel(
                    "EVIDENCE LEVEL"
                )

                Text(
                    text =
                        profile.profileStrength
                            .name
                            .replace(
                                "_",
                                " "
                            ),
                    color =
                        patternStrengthColor(
                            profile.profileStrength
                        ),
                    fontSize =
                        15.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        LabValueRow(
            label =
                "Overall profitable rate",
            value =
                profile
                    .overallProfitableRatePercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                LabGreen
        )

        LabValueRow(
            label =
                "Average historical return",
            value =
                profile
                    .overallAverageReturnPercent
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—"
        )

        LabValueRow(
            label =
                "Early-exit rate",
            value =
                profile
                    .earlyExitRatePercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                LabGold
        )

        LabValueRow(
            label =
                "Alternative outperformed rate",
            value =
                profile
                    .alternativeOutperformedRatePercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                LabCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        LabSmallLabel(
            "BEST OBSERVED ENVIRONMENT"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        PatternEvidenceBlock(
            evidence =
                profile.bestEnvironment,
            positive =
                true
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        LabSmallLabel(
            "WEAKEST OBSERVED ENVIRONMENT"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        PatternEvidenceBlock(
            evidence =
                profile.weakestEnvironment,
            positive =
                false
        )

        if (
            profile.signalPatterns
                .isNotEmpty()
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )

            LabSmallLabel(
                "TOP SIGNAL EVIDENCE"
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            profile.signalPatterns
                .take(
                    5
                )
                .forEach {
                        signal ->

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical =
                                        6.dp
                                )
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    signal.signal,
                                color =
                                    LabText,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.SemiBold,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )

                            Text(
                                text =
                                    "${signal.confidencePercent}%",
                                color =
                                    readinessColor(
                                        signal.confidencePercent
                                    ),
                                fontSize =
                                    11.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Text(
                            text =
                                buildString {

                                    append(
                                        "${signal.occurrences} trades"
                                    )

                                    signal.profitableRatePercent
                                        ?.let {
                                            append(
                                                " • ${formatPercent(it)} profitable"
                                            )
                                        }

                                    signal.averageReturnPercent
                                        ?.let {
                                            append(
                                                " • ${formatSignedPercent(it)} avg return"
                                            )
                                        }
                                },
                            color =
                                LabSecondary,
                            fontSize =
                                11.sp
                        )
                    }
                }
        }

        if (
            profile.strategyPatterns
                .isNotEmpty()
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )

            LabSmallLabel(
                "BEST MODELED MANAGEMENT RULES"
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            profile.strategyPatterns
                .take(
                    5
                )
                .forEach {
                        strategy ->

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical =
                                        6.dp
                                )
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    strategy.strategyTitle,
                                color =
                                    LabText,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.SemiBold,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )

                            Text(
                                text =
                                    "${strategy.timesBest}x",
                                color =
                                    LabGold,
                                fontSize =
                                    12.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Text(
                            text =
                                buildString {

                                    strategy.averageImprovementPercent
                                        ?.let {
                                            append(
                                                "${formatSignedPercent(it)} avg modeled edge"
                                            )
                                        }

                                    strategy.averageImprovementDollars
                                        ?.let {

                                            if (
                                                isNotEmpty()
                                            ) {
                                                append(
                                                    " • "
                                                )
                                            }

                                            append(
                                                String.format(
                                                    Locale.US,
                                                    "$%+,.2f avg P&L difference",
                                                    it
                                                )
                                            )
                                        }
                                }
                                    .ifBlank {
                                        "More evidence required."
                                    },
                            color =
                                LabSecondary,
                            fontSize =
                                11.sp
                        )
                    }
                }
        }

        if (
            profile.behavioralPatterns
                .isNotEmpty()
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )

            LabSmallLabel(
                "BEHAVIORAL SIGNALS"
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            profile.behavioralPatterns
                .take(
                    5
                )
                .forEach {
                        pattern ->

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        12.dp
                                    )
                                )
                                .background(
                                    LabSurface2
                                )
                                .border(
                                    width =
                                        1.dp,
                                    color =
                                        patternStrengthColor(
                                            pattern.strength
                                        )
                                            .copy(
                                                alpha =
                                                    0.30f
                                            ),
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
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    pattern.title,
                                color =
                                    LabText,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )

                            Text(
                                text =
                                    "${pattern.confidencePercent}%",
                                color =
                                    patternStrengthColor(
                                        pattern.strength
                                    ),
                                fontSize =
                                    11.sp,
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
                                pattern.summary,
                            color =
                                LabSecondary,
                            fontSize =
                                11.sp,
                            lineHeight =
                                16.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    5.dp
                                )
                        )

                        Text(
                            text =
                                "${pattern.sampleSize} matching trades" +
                                        (
                                                pattern.averageReturnPercent
                                                    ?.let {
                                                        " • ${formatSignedPercent(it)} avg return"
                                                    }
                                                    ?: ""
                                                ),
                            color =
                                LabCyan,
                            fontSize =
                                10.sp,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )
                }
        }
    }
}

@Composable
private fun PatternEvidenceBlock(
    evidence: PatternEvidence?,
    positive: Boolean
) {

    if (
        evidence == null
    ) {

        Text(
            text =
                "Not enough evidence yet.",
            color =
                LabSecondary,
            fontSize =
                12.sp
        )

        return
    }

    val accent =
        if (
            positive
        ) {
            LabGreen
        } else {
            LabRed
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .background(
                    LabSurface2
                )
                .border(
                    width =
                        1.dp,
                    color =
                        accent.copy(
                            alpha =
                                0.35f
                        ),
                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                )
                .padding(
                    14.dp
                )
    ) {

        Text(
            text =
                evidence.title,
            color =
                accent,
            fontSize =
                15.sp,
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
                evidence.summary,
            color =
                LabSecondary,
            fontSize =
                11.sp,
            lineHeight =
                16.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        LabValueRow(
            label =
                "Matching trades",
            value =
                evidence.sampleSize
                    .toString()
        )

        LabValueRow(
            label =
                "Profitable rate",
            value =
                evidence.profitableRatePercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—"
        )

        LabValueRow(
            label =
                "Average return",
            value =
                evidence.averageReturnPercent
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—"
        )

        LabValueRow(
            label =
                "Pattern confidence",
            value =
                "${evidence.confidencePercent}%",
            valueColor =
                patternStrengthColor(
                    evidence.strength
                )
        )
    }
}

@Composable
private fun CoachingPrioritiesCard(
    profile: AgentPatternProfile
) {

    LabCard {

        LabSectionLabel(
            text =
                "PERSONALIZED COACHING PRIORITIES",
            color =
                LabCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "What TraDNA currently wants you to practice next.",
            color =
                LabText,
            fontSize =
                18.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        profile.coachingPriorities
            .forEachIndexed {
                    index,
                    priority ->

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
                                .clip(
                                    RoundedCornerShape(
                                        8.dp
                                    )
                                )
                                .background(
                                    LabCyan.copy(
                                        alpha =
                                            0.12f
                                    )
                                )
                                .padding(
                                    horizontal =
                                        8.dp,
                                    vertical =
                                        5.dp
                                )
                    ) {

                        Text(
                            text =
                                "${index + 1}",
                            color =
                                LabCyan,
                            fontSize =
                                10.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    6.dp
                            )
                    )

                    Text(
                        text =
                            priority,
                        color =
                            LabText,
                        fontSize =
                            12.sp,
                        lineHeight =
                            18.sp,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    )
                }
            }

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Text(
            text =
                "These are evidence-based coaching hypotheses, not guarantees. Confidence should rise only as more independent trades support the same pattern.",
            color =
                LabSecondary,
            fontSize =
                10.sp,
            lineHeight =
                15.sp
        )
    }
}

private fun patternStrengthColor(
    strength: PatternStrength
): Color {

    return when (
        strength
    ) {

        PatternStrength.STRONG ->
            LabGreen

        PatternStrength.MODERATE ->
            LabCyan

        PatternStrength.EARLY ->
            LabGold

        PatternStrength.INSUFFICIENT ->
            LabSecondary
    }
}

@Composable
private fun GuardrailCard() {

    LabCard {

        LabSectionLabel(
            text =
                "AGENT STATUS",
            color =
                LabGreen
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "ADVISORY / SIMULATION",
            color =
                LabGreen,
            fontSize =
                20.sp,
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
                "The Lab is building a personalized decision model from historical evidence. It is not authorized to place live trades. Validate the model against unseen historical and paper-trading data before considering any future execution capability.",
            color =
                LabSecondary,
            fontSize =
                13.sp,
            lineHeight =
                20.sp
        )
    }
}

@Composable
private fun LearningMetric(
    label: String,
    value: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        7.dp
                )
    ) {

        Text(
            text =
                label,
            color =
                LabSecondary,
            fontSize =
                12.sp,
            modifier =
                Modifier.weight(
                    1f
                )
        )

        Text(
            text =
                value,
            color =
                LabText,
            fontSize =
                12.sp,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun LabValueRow(
    label: String,
    value: String,
    valueColor:
    Color =
        LabText
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
                LabSecondary,
            fontSize =
                12.sp,
            modifier =
                Modifier.weight(
                    1f
                )
        )

        Text(
            text =
                value,
            color =
                valueColor,
            fontSize =
                12.sp,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun LabSectionLabel(
    text: String,
    color: Color
) {

    Text(
        text =
            text,
        color =
            color,
        fontSize =
            10.sp,
        fontWeight =
            FontWeight.Bold,
        letterSpacing =
            1.3.sp
    )
}

@Composable
private fun LabSmallLabel(
    text: String
) {

    Text(
        text =
            text,
        color =
            LabSecondary,
        fontSize =
            9.sp,
        fontWeight =
            FontWeight.Bold,
        letterSpacing =
            1.1.sp
    )
}

@Composable
private fun LabCard(
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
                    LabSurface
                )
                .border(
                    width =
                        1.dp,
                    color =
                        LabBorder,
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

private fun readinessColor(
    score: Int
): Color {

    return when {

        score >=
                75 ->
            LabGreen

        score >=
                50 ->
            LabCyan

        score >=
                25 ->
            LabGold

        else ->
            LabViolet
    }
}

private fun formatPercent(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%.1f%%",
        value
    )
}

private fun formatSignedPercent(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%+.2f%%",
        value
    )
}

private fun safeTradeDate(
    value: String
): LocalDate {

    return try {

        LocalDate.parse(
            value,
            DateTimeFormatter
                .ofPattern(
                    "M/d/yyyy",
                    Locale.US
                )
        )

    } catch (
        _: Exception
    ) {

        LocalDate.MAX
    }
}

private fun labDateRange(
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
                5
            )
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    val end =
        close
            .plusDays(
                5
            )
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    return start to end
}
