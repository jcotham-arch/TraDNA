package com.tradna.APP

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.data.ImportMergeResult
import com.tradna.APP.data.DataIntegrityEngine
import com.tradna.APP.data.DataIntegrityReport
import com.tradna.APP.data.ImportSummary
import com.tradna.APP.data.MultiAssetTradeBridge
import com.tradna.APP.data.NormalizedImportMergeResult
import com.tradna.APP.data.NormalizedOptionActivityAdapter
import com.tradna.APP.data.NormalizedStockTradeReconstructor
import com.tradna.APP.data.NormalizedTradeActivity
import com.tradna.APP.data.OptionTradeReconstructor
import com.tradna.APP.data.FuturesTradeReconstructor
import com.tradna.APP.data.RobinhoodActivity
import com.tradna.APP.data.RobinhoodOptionActivityParser
import com.tradna.APP.data.StockExecution
import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.data.TradeReconstructor
import com.tradna.APP.data.TradeStatus
import com.tradna.APP.data.TraDnaStorage
import com.tradna.APP.data.TradingPlatformSource
import com.tradna.APP.data.UniversalAssetReconstructionEngine
import com.tradna.APP.data.UniversalAssetReconstructionOutcome
import com.tradna.APP.data.UniversalTradingDataStorage
import com.tradna.APP.data.local.LegacyNormalizedActivityMigration
import com.tradna.APP.data.local.LegacyRobinhoodActivityMigration
import com.tradna.APP.data.local.NormalizedActivityRoomRepository
import com.tradna.APP.data.local.RobinhoodActivityRoomRepository
import com.tradna.APP.data.local.TraDnaDatabase
import com.tradna.APP.coaching.CoachingSignalKind
import com.tradna.APP.coaching.TradingBehaviorReport
import com.tradna.APP.coaching.TradingBehaviorReportEngine
import com.tradna.APP.market.AlpacaMarketData
import com.tradna.APP.market.Candle
import com.tradna.APP.market.CandleChart
import com.tradna.APP.replay.HistoricalTradeReviewScreen
import com.tradna.APP.replay.MultiAssetLabHost
import com.tradna.APP.replay.MultiAssetReplayHost
import com.tradna.APP.ui.theme.TraDNATheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TraBackground = Color(0xFF07090D)
private val TraSurface = Color(0xFF0E1219)
private val TraBorder = Color(0xFF222B38)

private val TraText = Color(0xFFF4F7FB)
private val TraTextSecondary = Color(0xFF8D98A8)

private val TraCyan = Color(0xFF72E7FF)
private val TraViolet = Color(0xFF9B7CFF)
private val TraGreen = Color(0xFF39D6A0)
private val TraRed = Color(0xFFFF657A)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            TraDNATheme {
                TraDNAApp()
            }
        }
    }
}

enum class TraDestination(
    val label: String,
    val icon: ImageVector
) {
    HOME("Home", Icons.Default.Home),
    TRAIN("Train", Icons.Default.List),
    REPLAY("Replay", Icons.Default.PlayArrow),
    DNA("DNA", Icons.Default.Info),
    LAB("Lab", Icons.Default.Settings)
}

sealed class InternalScreen {

    data object Main : InternalScreen()

    data object Activity : InternalScreen()

    data class ActivityDetail(
        val activity: RobinhoodActivity
    ) : InternalScreen()

    data object Trades : InternalScreen()

    data object DataIntegrity : InternalScreen()

    data class TradeDetail(
        val trade: TradeEpisode
    ) : InternalScreen()

    data class TradeReview(
        val trade: TradeEpisode
    ) : InternalScreen()
}

@Composable
fun TraDNAApp() {

    val context = LocalContext.current

    val roomDatabase = remember(context) {
        TraDnaDatabase.getInstance(context)
    }

    val normalizedRoomRepository = remember(roomDatabase) {
        NormalizedActivityRoomRepository(roomDatabase)
    }

    val robinhoodRoomRepository = remember(roomDatabase) {
        RobinhoodActivityRoomRepository(roomDatabase)
    }

    val persistenceScope = rememberCoroutineScope()

    var normalizedStorageError by remember {
        mutableStateOf<String?>(null)
    }

    var selectedDestination by remember {
        mutableStateOf(TraDestination.HOME)
    }

    var internalScreen by remember {
        mutableStateOf<InternalScreen>(
            InternalScreen.Main
        )
    }

    var activities by remember {
        mutableStateOf(
            TraDnaStorage.loadActivities(context)
        )
    }

    var importedFileName by remember {
        mutableStateOf(
            TraDnaStorage.loadFileName(context)
        )
    }

    var normalizedActivities by remember {
        mutableStateOf(
            UniversalTradingDataStorage.loadActivities(
                context
            )
        )
    }

    LaunchedEffect(
        normalizedRoomRepository,
        robinhoodRoomRepository
    ) {
        try {
            val canonicalHistory = withContext(Dispatchers.IO) {
                LegacyRobinhoodActivityMigration(
                    context = context,
                    database = roomDatabase
                ).migrateIfNeeded()

                LegacyNormalizedActivityMigration(
                    context = context,
                    database = roomDatabase
                ).migrateIfNeeded()

                Pair(
                    robinhoodRoomRepository.loadActivities(),
                    normalizedRoomRepository.loadActivities()
                )
            }

            activities = canonicalHistory.first
            normalizedActivities = canonicalHistory.second
            normalizedStorageError = null
        } catch (error: Exception) {
            normalizedStorageError =
                "TraDNA could not verify its local database. " +
                        "Legacy trading history remains available. " +
                        (error.message ?: "Unknown storage error.")
        }
    }

    val trades = remember(activities) {
        TradeReconstructor.reconstruct(
            activities
        )
    }

    val robinhoodOptionTrades =
        remember(activities) {
            RobinhoodOptionActivityParser
                .parseAndReconstruct(
                    activities
                )
                .optionTrades
        }

    val normalizedStockTrades =
        remember(normalizedActivities) {
            NormalizedStockTradeReconstructor
                .reconstruct(
                    normalizedActivities
                )
        }

    val normalizedOptionTrades =
        remember(normalizedActivities) {

            val converted =
                NormalizedOptionActivityAdapter
                    .convert(
                        normalizedActivities
                    )

            OptionTradeReconstructor
                .reconstruct(
                    converted.activities
                )
        }

    val normalizedFuturesTrades =
        remember(normalizedActivities) {
            FuturesTradeReconstructor
                .reconstruct(
                    normalizedActivities
                )
        }

    val robinhoodOptionTradeCount =
        robinhoodOptionTrades.size

    val normalizedStockTradeCount =
        normalizedStockTrades.size

    val normalizedOptionTradeCount =
        normalizedOptionTrades.size

    val normalizedFuturesTradeCount =
        normalizedFuturesTrades.size

    val multiAssetTrades =
        remember(
            trades,
            robinhoodOptionTrades,
            normalizedStockTrades,
            normalizedOptionTrades,
            normalizedFuturesTrades
        ) {
            MultiAssetTradeBridge.combine(
                legacyStockTrades =
                    trades,
                normalizedStockTrades =
                    normalizedStockTrades,
                optionTrades =
                    robinhoodOptionTrades +
                            normalizedOptionTrades,
                futuresTrades =
                    normalizedFuturesTrades
            )
        }

    BackHandler(
        enabled =
            internalScreen !is InternalScreen.Main
    ) {

        val currentScreen =
            internalScreen

        internalScreen =
            when (currentScreen) {

                is InternalScreen.ActivityDetail ->
                    InternalScreen.Activity

                is InternalScreen.TradeDetail ->
                    InternalScreen.Trades

                is InternalScreen.TradeReview ->
                    InternalScreen.TradeDetail(
                        currentScreen.trade
                    )

                InternalScreen.DataIntegrity ->
                    InternalScreen.Main

                else ->
                    InternalScreen.Main
            }
    }

    Scaffold(
        containerColor = TraBackground,

        bottomBar = {

            if (
                internalScreen is InternalScreen.Main
            ) {

                TraBottomNavigation(
                    selected = selectedDestination,

                    onSelected = {
                        selectedDestination = it
                    }
                )
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TraBackground)
                .padding(padding)
        ) {

            when (
                val screen = internalScreen
            ) {

                InternalScreen.Main -> {

                    when (selectedDestination) {

                        TraDestination.HOME -> {

                            HomeScreen(
                                activities = activities,
                                normalizedActivities =
                                    normalizedActivities,
                                trades = trades,
                                fileName = importedFileName,
                                robinhoodOptionTradeCount =
                                    robinhoodOptionTradeCount,
                                normalizedStockTradeCount =
                                    normalizedStockTradeCount,
                                normalizedOptionTradeCount =
                                    normalizedOptionTradeCount,
                                normalizedFuturesTradeCount =
                                    normalizedFuturesTradeCount,
                                storageError =
                                    normalizedStorageError,

                                onImportComplete = {
                                        mergedActivities,
                                        fileName ->

                                    activities =
                                        mergedActivities

                                    importedFileName =
                                        fileName

                                    persistenceScope.launch {
                                        try {
                                            val canonicalActivities =
                                                withContext(Dispatchers.IO) {
                                                    robinhoodRoomRepository
                                                        .mergeActivities(
                                                            incomingActivities =
                                                                mergedActivities,
                                                            fileName = fileName
                                                        )
                                                        .mergedActivities
                                                }

                                            activities =
                                                canonicalActivities

                                            normalizedStorageError = null
                                        } catch (error: Exception) {
                                            normalizedStorageError =
                                                "The Robinhood import remains in legacy storage, " +
                                                        "but Room synchronization failed. " +
                                                        (error.message
                                                            ?: "Unknown storage error.")
                                        }
                                    }
                                },

                                onNormalizedImportComplete = {
                                        mergedActivities,
                                        fileName,
                                        source ->

                                    normalizedActivities =
                                        mergedActivities

                                    persistenceScope.launch {
                                        try {
                                            val canonicalActivities =
                                                withContext(Dispatchers.IO) {
                                                    normalizedRoomRepository
                                                        .mergeActivities(
                                                            incomingActivities =
                                                                mergedActivities,
                                                            source = source,
                                                            fileName = fileName
                                                        )
                                                        .mergedActivities
                                                }

                                            normalizedActivities =
                                                canonicalActivities

                                            normalizedStorageError = null
                                        } catch (error: Exception) {
                                            normalizedStorageError =
                                                "The import remains in legacy storage, " +
                                                        "but Room synchronization failed. " +
                                                        (error.message
                                                            ?: "Unknown storage error.")
                                        }
                                    }
                                },

                                onViewActivity = {
                                    internalScreen =
                                        InternalScreen.Activity
                                },

                                onViewTrades = {
                                    internalScreen =
                                        InternalScreen.Trades
                                },

                                onViewDataIntegrity = {
                                    internalScreen =
                                        InternalScreen.DataIntegrity
                                }
                            )
                        }

                        TraDestination.TRAIN -> {

                            PlaceholderScreen(
                                title = "TRAIN",
                                subtitle =
                                    "Technical training protocols are next."
                            )
                        }

                        TraDestination.REPLAY -> {

                            MultiAssetReplayHost(
                                stockTrades =
                                    trades,
                                multiAssetTrades =
                                    multiAssetTrades
                            )
                        }

                        TraDestination.DNA -> {

                            DnaScreen(
                                trades = trades
                            )
                        }

                        TraDestination.LAB -> {

                            MultiAssetLabHost(
                                stockTrades =
                                    trades,
                                multiAssetTrades =
                                    multiAssetTrades
                            )
                        }
                    }
                }

                InternalScreen.Activity -> {

                    ActivityScreen(
                        activities = activities,

                        onBack = {
                            internalScreen =
                                InternalScreen.Main
                        },

                        onActivitySelected = {

                            internalScreen =
                                InternalScreen
                                    .ActivityDetail(it)
                        }
                    )
                }

                is InternalScreen.ActivityDetail -> {

                    ActivityDetailScreen(
                        activity =
                            screen.activity,

                        onBack = {
                            internalScreen =
                                InternalScreen.Activity
                        }
                    )
                }

                InternalScreen.DataIntegrity -> {

                    DataIntegrityScreen(
                        report =
                            DataIntegrityEngine.analyze(
                                activities = activities,
                                trades = trades
                            ),
                        onBack = {
                            internalScreen =
                                InternalScreen.Main
                        }
                    )
                }

                InternalScreen.Trades -> {

                    TradesScreen(
                        trades = trades,

                        onBack = {
                            internalScreen =
                                InternalScreen.Main
                        },

                        onTradeSelected = {

                            internalScreen =
                                InternalScreen
                                    .TradeDetail(it)
                        }
                    )
                }

                is InternalScreen.TradeDetail -> {

                    TradeDetailScreen(
                        trade =
                            screen.trade,

                        onBack = {
                            internalScreen =
                                InternalScreen.Trades
                        },

                        onOpenReview = {
                            internalScreen =
                                InternalScreen
                                    .TradeReview(
                                        screen.trade
                                    )
                        }
                    )
                }

                is InternalScreen.TradeReview -> {

                    HistoricalTradeReviewScreen(
                        trade =
                            screen.trade,

                        onBack = {
                            internalScreen =
                                InternalScreen
                                    .TradeDetail(
                                        screen.trade
                                    )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    activities: List<RobinhoodActivity>,
    normalizedActivities: List<NormalizedTradeActivity>,
    trades: List<TradeEpisode>,
    fileName: String,
    robinhoodOptionTradeCount: Int,
    normalizedStockTradeCount: Int,
    normalizedOptionTradeCount: Int,
    normalizedFuturesTradeCount: Int,
    storageError: String?,

    onImportComplete:
        (
        List<RobinhoodActivity>,
        String
    ) -> Unit,

    onNormalizedImportComplete:
        (
        List<NormalizedTradeActivity>,
        String,
        TradingPlatformSource
    ) -> Unit,

    onViewActivity: () -> Unit,
    onViewTrades: () -> Unit,
    onViewDataIntegrity: () -> Unit
) {

    val context = LocalContext.current

    var importError by remember {
        mutableStateOf<String?>(null)
    }

    var lastMergeResult by remember {
        mutableStateOf<ImportMergeResult?>(
            null
        )
    }

    var lastNormalizedMergeResult by remember {
        mutableStateOf<NormalizedImportMergeResult?>(
            null
        )
    }

    val filePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    importError = null

                    val fileNameValue =
                        uri.lastPathSegment
                            ?.substringAfterLast("/")
                            ?: "Trading history CSV"

                    val csv =
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }

                    if (csv.isNullOrBlank()) {

                        importError =
                            "Unable to read this file."

                        return@rememberLauncherForActivityResult
                    }

                    when (
                        val outcome =
                            UniversalAssetReconstructionEngine
                                .importAndReconstruct(
                                    csvText =
                                        csv,
                                    fileName =
                                        fileNameValue
                                )
                    ) {

                        is UniversalAssetReconstructionOutcome.Failure -> {

                            importError =
                                outcome.reason
                        }

                        is UniversalAssetReconstructionOutcome.Success -> {

                            val reconstructed =
                                outcome.result

                            when (
                                reconstructed.source
                            ) {

                                TradingPlatformSource.ROBINHOOD -> {

                                    val mergeResult =
                                        TraDnaStorage.mergeActivities(
                                            context =
                                                context,
                                            incomingActivities =
                                                reconstructed
                                                    .robinhoodActivities,
                                            fileName =
                                                fileNameValue
                                        )

                                    lastMergeResult =
                                        mergeResult

                                    lastNormalizedMergeResult =
                                        null

                                    onImportComplete(
                                        mergeResult
                                            .mergedActivities,
                                        fileNameValue
                                    )
                                }

                                TradingPlatformSource.TRADINGVIEW,
                                TradingPlatformSource.WEALTHCHARTS,
                                TradingPlatformSource.GENERIC_CSV -> {

                                    val mergeResult =
                                        UniversalTradingDataStorage
                                            .mergeActivities(
                                                context =
                                                    context,
                                                incomingActivities =
                                                    reconstructed
                                                        .normalizedActivities,
                                                source =
                                                    reconstructed
                                                        .source,
                                                fileName =
                                                    fileNameValue
                                            )

                                    lastNormalizedMergeResult =
                                        mergeResult

                                    lastMergeResult =
                                        null

                                    onNormalizedImportComplete(
                                        mergeResult
                                            .mergedActivities,
                                        fileNameValue,
                                        reconstructed.source
                                    )
                                }
                            }
                        }
                    }

                } catch (e: Exception) {

                    importError =
                        e.message
                            ?: "Import failed."
                }
            }
        }

    val hasAnyHistory =
        activities.isNotEmpty() ||
                normalizedActivities.isNotEmpty()

    val totalStockTradeCount =
        trades.size +
                normalizedStockTradeCount

    val totalOptionTradeCount =
        robinhoodOptionTradeCount +
                normalizedOptionTradeCount

    val totalTradeCount =
        totalStockTradeCount +
                totalOptionTradeCount +
                normalizedFuturesTradeCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(horizontal = 22.dp)
            .padding(
                top = 18.dp,
                bottom = 28.dp
            )
    ) {

        BrandHeader()

        Spacer(
            Modifier.height(30.dp)
        )

        if (!hasAnyHistory) {

            EmptyImportCard(
                onImport = {

                    filePicker.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "application/vnd.ms-excel",
                            "text/plain"
                        )
                    )
                }
            )

        } else {

            Text(
                text = "TRADING HISTORY",
                color = TraCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                text = totalTradeCount.toString(),
                color = TraText,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light
            )

            Text(
                text =
                    "RECONSTRUCTED TRADES",
                color = TraTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(
                Modifier.height(24.dp)
            )

            if (lastMergeResult != null) {

                ImportReceiptCard(
                    result =
                        lastMergeResult!!,

                    reconstructedTradeCount =
                        trades.size
                )

                Spacer(
                    Modifier.height(18.dp)
                )
            }

            if (
                lastNormalizedMergeResult !=
                null
            ) {

                NormalizedImportReceiptCard(
                    result =
                        lastNormalizedMergeResult!!,
                    stockTradeCount =
                        normalizedStockTradeCount,
                    optionTradeCount =
                        normalizedOptionTradeCount,
                    futuresTradeCount =
                        normalizedFuturesTradeCount
                )

                Spacer(
                    Modifier.height(18.dp)
                )
            }

            UniversalHistoryOverviewCard(
                totalRecords =
                    activities.size +
                            normalizedActivities.size,
                stockTradeCount =
                    totalStockTradeCount,
                optionTradeCount =
                    totalOptionTradeCount,
                futuresTradeCount =
                    normalizedFuturesTradeCount
            )

            Spacer(
                Modifier.height(18.dp)
            )

            ActionCard(
                eyebrow = "TRADING DATA",
                title =
                    "Import historical trading data",
                subtitle =
                    "Import or update supported trading-history files. Overlapping records are detected automatically and only new activity is added.",
                actionText =
                    "IMPORT TRADING DATA",
                accent =
                    TraCyan,
                onClick = {

                    filePicker.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "application/vnd.ms-excel",
                            "text/plain"
                        )
                    )
                }
            )

            if (
                activities.isNotEmpty()
            ) {

                Spacer(
                    Modifier.height(18.dp)
                )

                ActionCard(
                    eyebrow = "TRADES",
                    title =
                        "${trades.size} trade episodes reconstructed",
                    subtitle =
                        "View entries, exits, remaining shares and realized P&L.",
                    actionText =
                        "VIEW TRADES",
                    accent =
                        TraGreen,
                    onClick =
                        onViewTrades
                )

                Spacer(
                    Modifier.height(18.dp)
                )

                TradingBehaviorReportCard(
                    report =
                        TradingBehaviorReportEngine
                            .analyze(trades)
                )

                Spacer(
                    Modifier.height(18.dp)
                )

                ActionCard(
                    eyebrow = "RAW DATA",
                    title =
                        "${activities.size} brokerage records",
                    subtitle =
                        "Inspect every stored brokerage transaction.",
                    actionText =
                        "VIEW ACTIVITY",
                    accent =
                        TraCyan,
                    onClick =
                        onViewActivity
                )

                Spacer(
                    Modifier.height(18.dp)
                )

                val integrityReport =
                    DataIntegrityEngine.analyze(
                        activities = activities,
                        trades = trades
                    )

                ActionCard(
                    eyebrow = "DATA INTEGRITY",
                    title =
                        "${integrityReport.healthScore} / 100 health score",
                    subtitle =
                        if (integrityReport.warningCount == 0) {
                            "Imported trading history passed the current integrity checks."
                        } else {
                            "${integrityReport.warningCount} data warning${if (integrityReport.warningCount == 1) "" else "s"} detected."
                        },
                    actionText =
                        "VIEW DATA AUDIT",
                    accent =
                        if (integrityReport.healthScore >= 90) {
                            TraGreen
                        } else if (integrityReport.healthScore >= 70) {
                            TraCyan
                        } else {
                            TraRed
                        },
                    onClick =
                        onViewDataIntegrity
                )

                Spacer(
                    Modifier.height(18.dp)
                )

                ModelProgressCard(
                    trades = trades
                )
            }

            if (
                normalizedActivities.isNotEmpty()
            ) {

                Spacer(
                    Modifier.height(18.dp)
                )

                NormalizedHistoryCard(
                    activities =
                        normalizedActivities,
                    stockTradeCount =
                        normalizedStockTradeCount,
                    optionTradeCount =
                        normalizedOptionTradeCount,
                    futuresTradeCount =
                        normalizedFuturesTradeCount
                )
            }
        }

        val visibleError =
            importError
                ?: storageError

        if (visibleError != null) {

            Spacer(
                Modifier.height(18.dp)
            )

            Text(
                text = visibleError,
                color = TraRed
            )
        }
    }
}

@Composable
fun TradingBehaviorReportCard(
    report: TradingBehaviorReport
) {
    TraCard {
        SectionLabel("BEHAVIOR BASELINE")

        Spacer(Modifier.height(16.dp))

        if (report.completedTrades == 0) {
            Text(
                text = "Complete a stock trade before behavior metrics can be calculated.",
                color = TraTextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        } else {
            StatRow("Completed trades", report.completedTrades.toString())
            StatRow(
                "Win rate",
                report.winRatePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
            )
            StatRow("Realized P&L", money(report.realizedPnl))
            StatRow(
                "Avg winner / loser",
                report.payoffRatio?.let { String.format(Locale.US, "%.2f×", it) } ?: "Not enough data"
            )
            StatRow(
                "Average hold",
                report.averageHoldingDays?.let { String.format(Locale.US, "%.1f days", it) } ?: "Unknown"
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = TraBorder)
            Spacer(Modifier.height(14.dp))

            report.signals.take(3).forEachIndexed { index, signal ->
                Text(
                    text = signal.title.uppercase(Locale.US),
                    color = when (signal.kind) {
                        CoachingSignalKind.STRENGTH -> TraGreen
                        CoachingSignalKind.WATCH -> TraRed
                        CoachingSignalKind.CONTEXT -> TraCyan
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = signal.evidence,
                    color = TraTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                if (index != report.signals.take(3).lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = report.evidenceNote,
                color = TraTextSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun UniversalHistoryOverviewCard(
    totalRecords: Int,
    stockTradeCount: Int,
    optionTradeCount: Int,
    futuresTradeCount: Int
) {

    TraCard {

        SectionLabel(
            "UNIVERSAL HISTORY"
        )

        Spacer(
            Modifier.height(14.dp)
        )

        DetailRow(
            label =
                "Stored records",
            value =
                totalRecords.toString()
        )

        DetailRow(
            label =
                "Stock trades",
            value =
                stockTradeCount.toString()
        )

        DetailRow(
            label =
                "Option trades",
            value =
                optionTradeCount.toString()
        )

        DetailRow(
            label =
                "Futures trades",
            value =
                futuresTradeCount.toString()
        )
    }
}

@Composable
fun NormalizedImportReceiptCard(
    result: NormalizedImportMergeResult,
    stockTradeCount: Int,
    optionTradeCount: Int,
    futuresTradeCount: Int
) {

    TraCard {

        Text(
            text =
                "${result.source.name.replace("_", " ")} IMPORT COMPLETE",
            color =
                if (
                    result.newRecordCount >
                    0
                ) {
                    TraGreen
                } else {
                    TraCyan
                },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            text =
                if (
                    result.newRecordCount >
                    0
                ) {
                    "${result.newRecordCount} new records added"
                } else {
                    "No new activity found"
                },
            color = TraText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            Modifier.height(18.dp)
        )

        DetailRow(
            label =
                "Report scanned",
            value =
                result.incomingRecordCount
                    .toString()
        )

        DetailRow(
            label =
                "Duplicates skipped",
            value =
                result.duplicateRecordCount
                    .toString()
        )

        DetailRow(
            label =
                "Total stored",
            value =
                result.totalStoredCount
                    .toString()
        )

        DetailRow(
            label =
                "Stock trades",
            value =
                stockTradeCount
                    .toString()
        )

        DetailRow(
            label =
                "Option trades",
            value =
                optionTradeCount
                    .toString()
        )

        DetailRow(
            label =
                "Futures trades",
            value =
                futuresTradeCount
                    .toString()
        )
    }
}

@Composable
fun NormalizedHistoryCard(
    activities: List<NormalizedTradeActivity>,
    stockTradeCount: Int,
    optionTradeCount: Int,
    futuresTradeCount: Int
) {

    TraCard {

        SectionLabel(
            "MULTI-ASSET DATA"
        )

        Spacer(
            Modifier.height(12.dp)
        )

        Text(
            text =
                "${activities.size} WealthCharts / TradingView normalized records stored",
            color = TraText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            Modifier.height(10.dp)
        )

        Text(
            text =
                "$stockTradeCount stock • $optionTradeCount option • $futuresTradeCount futures trade episodes reconstructed",
            color =
                TraTextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )

        Spacer(
            Modifier.height(10.dp)
        )

        Text(
            text =
                "The established Replay and Agent Lab remain on the existing stock TradeEpisode pipeline while the multi-asset analysis screens are connected next.",
            color =
                TraTextSecondary,
            fontSize = 12.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
fun ImportReceiptCard(
    result: ImportMergeResult,
    reconstructedTradeCount: Int
) {

    TraCard {

        Text(
            text =
                "TRADING DATA IMPORT COMPLETE",
            color =
                if (
                    result.newRecordCount > 0
                ) {
                    TraGreen
                } else {
                    TraCyan
                },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            text =
                if (
                    result.newRecordCount > 0
                ) {
                    "${result.newRecordCount} new records added"
                } else {
                    "No new activity found"
                },
            color = TraText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            Modifier.height(18.dp)
        )

        DetailRow(
            label =
                "Report scanned",
            value =
                result.reportRecordCount
                    .toString()
        )

        DetailRow(
            label =
                "New activity",
            value =
                result.newRecordCount
                    .toString()
        )

        DetailRow(
            label =
                "Duplicates skipped",
            value =
                result.duplicateRecordCount
                    .toString()
        )

        DetailRow(
            label =
                "Total stored",
            value =
                result.totalStoredCount
                    .toString()
        )

        DetailRow(
            label =
                "Trades reconstructed",
            value =
                reconstructedTradeCount
                    .toString()
        )
    }
}


@Composable
fun DataIntegrityScreen(
    report: DataIntegrityReport,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TraBackground)
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                bottom = 30.dp
            )
    ) {

        ScreenHeader(
            title = "DATA INTEGRITY",
            subtitle = "Trading data import audit",
            onBack = onBack
        )

        Column(
            modifier = Modifier.padding(
                horizontal = 20.dp
            )
        ) {

            TraCard {

                Text(
                    text = "DATA HEALTH",
                    color = TraCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp
                )

                Spacer(
                    Modifier.height(10.dp)
                )

                Text(
                    text = "${report.healthScore} / 100",
                    color =
                        integrityHealthColor(
                            report.healthScore
                        ),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.height(6.dp)
                )

                Text(
                    text =
                        when {
                            report.healthScore >= 95 ->
                                "Excellent data quality"

                            report.healthScore >= 85 ->
                                "Good data quality"

                            report.healthScore >= 70 ->
                                "Review recommended"

                            else ->
                                "Data requires attention"
                        },
                    color = TraText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    text =
                        if (report.warningCount == 0) {
                            "No integrity warnings were found by the current audit rules."
                        } else {
                            "${report.warningCount} warning${if (report.warningCount == 1) "" else "s"} found. Review the details below before relying on this history for deeper DNA analysis."
                        },
                    color = TraTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }

            Spacer(
                Modifier.height(18.dp)
            )

            TraCard {

                SectionLabel(
                    "BROKERAGE RECORDS"
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                DetailRow(
                    "Total records",
                    report.totalRecords.toString()
                )

                DetailRow(
                    "Buy records",
                    report.buyRecords.toString()
                )

                DetailRow(
                    "Sell records",
                    report.sellRecords.toString()
                )

                DetailRow(
                    "Option records",
                    report.optionRecords.toString()
                )

                DetailRow(
                    "Other records",
                    report.otherRecords.toString()
                )

                DetailRow(
                    "Unique instruments",
                    report.uniqueInstruments.toString()
                )
            }

            Spacer(
                Modifier.height(18.dp)
            )

            TraCard {

                SectionLabel(
                    "TRADE RECONSTRUCTION"
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                DetailRow(
                    "Trade episodes",
                    report.reconstructedTrades.toString()
                )

                DetailRow(
                    "Closed",
                    report.closedTrades.toString()
                )

                DetailRow(
                    "Open",
                    report.openTrades.toString()
                )

                DetailRow(
                    "Partial",
                    report.partialTrades.toString()
                )

                DetailRow(
                    "Unmatched sells",
                    report.unmatchedSellRecords.toString()
                )
            }

            Spacer(
                Modifier.height(18.dp)
            )

            TraCard {

                SectionLabel(
                    "PARSING CHECKS"
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                IntegrityCheckRow(
                    label = "Missing instruments",
                    count = report.blankInstrumentRecords
                )

                IntegrityCheckRow(
                    label = "Missing activity dates",
                    count = report.blankDateRecords
                )

                IntegrityCheckRow(
                    label = "Missing transaction codes",
                    count = report.blankTransactionCodeRecords
                )

                IntegrityCheckRow(
                    label = "Unreadable quantities",
                    count = report.invalidQuantityRecords
                )

                IntegrityCheckRow(
                    label = "Unreadable prices",
                    count = report.invalidPriceRecords
                )

                IntegrityCheckRow(
                    label = "Unreadable amounts",
                    count = report.invalidAmountRecords
                )

                IntegrityCheckRow(
                    label = "Unmatched sells",
                    count = report.unmatchedSellRecords
                )
            }

            Spacer(
                Modifier.height(18.dp)
            )

            TraCard {

                SectionLabel(
                    "WARNINGS"
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                if (report.warnings.isEmpty()) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(TraGreen)
                        )

                        Spacer(
                            Modifier.width(10.dp)
                        )

                        Text(
                            text = "No current data-integrity warnings.",
                            color = TraText,
                            fontSize = 14.sp
                        )
                    }

                } else {

                    report.warnings.forEachIndexed {
                            index,
                            warning ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 7.dp
                                ),
                            verticalAlignment = Alignment.Top
                        ) {

                            Text(
                                text = "${index + 1}.",
                                color = TraRed,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp)
                            )

                            Text(
                                text = warning,
                                color = TraText,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            Text(
                text =
                    "This audit validates the imported structure and trade reconstruction under TraDNA's current rules. A high score does not guarantee that a brokerage export is economically complete; it means the stored records passed the checks currently implemented in the app.",
                color = TraTextSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun IntegrityCheckRow(
    label: String,
    count: Int
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 7.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = label,
            color = TraTextSecondary,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        10.dp
                    )
                )
                .background(
                    if (count == 0) {
                        TraGreen.copy(
                            alpha = 0.14f
                        )
                    } else {
                        TraRed.copy(
                            alpha = 0.14f
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    color =
                        if (count == 0) {
                            TraGreen.copy(
                                alpha = 0.35f
                            )
                        } else {
                            TraRed.copy(
                                alpha = 0.45f
                            )
                        },
                    shape =
                        RoundedCornerShape(
                            10.dp
                        )
                )
                .padding(
                    horizontal = 10.dp,
                    vertical = 5.dp
                )
        ) {

            Text(
                text =
                    if (count == 0) {
                        "PASS"
                    } else {
                        count.toString()
                    },
                color =
                    if (count == 0) {
                        TraGreen
                    } else {
                        TraRed
                    },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun integrityHealthColor(
    score: Int
): Color {

    return when {
        score >= 90 ->
            TraGreen

        score >= 70 ->
            TraCyan

        else ->
            TraRed
    }
}

@Composable
fun TradeOverviewCard(
    trades: List<TradeEpisode>
) {

    val closed =
        trades.count {
            it.status ==
                    TradeStatus.CLOSED
        }

    val open =
        trades.count {
            it.status ==
                    TradeStatus.OPEN ||
                    it.status ==
                    TradeStatus.PARTIAL
        }

    val winning =
        trades.count {
            it.realizedPnl > 0.0
        }

    val losing =
        trades.count {
            it.realizedPnl < 0.0
        }

    val realized =
        trades.sumOf {
            it.realizedPnl
        }

    TraCard {

        SectionLabel(
            "RECONSTRUCTION"
        )

        Spacer(
            Modifier.height(20.dp)
        )

        StatRow(
            "Completed",
            closed.toString()
        )

        StatRow(
            "Open / partial",
            open.toString()
        )

        StatRow(
            "Profitable",
            winning.toString()
        )

        StatRow(
            "Losing",
            losing.toString()
        )

        Spacer(
            Modifier.height(12.dp)
        )

        HorizontalDivider(
            color = TraBorder
        )

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            text =
                "REALIZED P&L IN IMPORTED HISTORY",
            color = TraTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )

        Spacer(
            Modifier.height(6.dp)
        )

        Text(
            text = money(realized),

            color =
                if (realized >= 0.0) {
                    TraGreen
                } else {
                    TraRed
                },

            fontSize = 28.sp,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
fun TradesScreen(
    trades: List<TradeEpisode>,
    onBack: () -> Unit,
    onTradeSelected:
        (TradeEpisode) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TraBackground)
    ) {

        ScreenHeader(
            title = "TRADES",
            subtitle =
                "${trades.size} reconstructed episodes",
            onBack = onBack
        )

        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    bottom = 30.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            items(
                trades,
                key = {
                    it.id
                }
            ) { trade ->

                TradeRow(
                    trade = trade,
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
fun TradeRow(
    trade: TradeEpisode,
    onClick: () -> Unit
) {

    val statusColor =
        when (trade.status) {

            TradeStatus.CLOSED ->
                TraTextSecondary

            TradeStatus.OPEN ->
                TraCyan

            TradeStatus.PARTIAL ->
                TraViolet
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    18.dp
                )
            )
            .background(TraSurface)
            .border(
                1.dp,
                TraBorder,
                RoundedCornerShape(
                    18.dp
                )
            )
            .clickable {
                onClick()
            }
            .padding(16.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = trade.symbol,
                    color = TraText,
                    fontSize = 19.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.width(9.dp)
                )

                Text(
                    text =
                        trade.status.name,
                    color =
                        statusColor,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(5.dp)
            )

            Text(
                text =
                    if (
                        trade.closeDate != null
                    ) {

                        "${trade.openDate} → ${trade.closeDate}"

                    } else {

                        "${trade.openDate} → OPEN"
                    },

                color =
                    TraTextSecondary,

                fontSize =
                    12.sp
            )
        }

        Column(
            horizontalAlignment =
                Alignment.End
        ) {

            Text(
                text =
                    money(
                        trade.realizedPnl
                    ),

                color =
                    when {

                        trade.realizedPnl >
                                0.0 -> TraGreen

                        trade.realizedPnl <
                                0.0 -> TraRed

                        else -> TraText
                    },

                fontSize = 16.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text =
                    "${formatShares(trade.totalSharesBought)} sh",
                color =
                    TraTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun TradeDetailScreen(
    trade: TradeEpisode,
    onBack: () -> Unit,
    onOpenReview: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                bottom = 30.dp
            )
    ) {

        ScreenHeader(
            title = trade.symbol,
            subtitle =
                "TRADE #${trade.sequenceNumber}",
            onBack = onBack
        )

        Column(
            modifier =
                Modifier.padding(
                    horizontal = 20.dp
                )
        ) {

            ActionCard(
                eyebrow = "TRADNA TEACHER",
                title = "Analyze this historical trade",
                subtitle =
                    "Open the full historical review with technical analysis, strengths, weaknesses, lessons, alternative scenarios, and modeled returns.",
                actionText =
                    "OPEN TRADE REVIEW",
                accent =
                    TraViolet,
                onClick =
                    onOpenReview
            )

            Spacer(
                Modifier.height(18.dp)
            )

            TraCard {

                SectionLabel(
                    "POSITION"
                )

                Spacer(
                    Modifier.height(18.dp)
                )

                DetailRow(
                    "Status",
                    trade.status.name
                )

                DetailRow(
                    "Opened",
                    trade.openDate
                )

                DetailRow(
                    "Closed",
                    trade.closeDate
                        ?: "Open"
                )

                DetailRow(
                    "Shares bought",
                    formatShares(
                        trade.totalSharesBought
                    )
                )

                DetailRow(
                    "Shares sold",
                    formatShares(
                        trade.totalSharesSold
                    )
                )

                DetailRow(
                    "Remaining",
                    formatShares(
                        trade.remainingShares
                    )
                )

                DetailRow(
                    "Average entry",
                    money(
                        trade.averageEntryPrice
                    )
                )

                DetailRow(
                    "Average exit",
                    trade.averageExitPrice
                        ?.let {
                            money(it)
                        }
                        ?: "—"
                )

                DetailRow(
                    "Realized P&L",
                    money(
                        trade.realizedPnl
                    )
                )

                DetailRow(
                    "Executions",
                    trade.executions
                        .size
                        .toString()
                )
            }

            Spacer(
                Modifier.height(18.dp)
            )

            MarketReconstructionCard(
                trade = trade
            )

            Spacer(
                Modifier.height(18.dp)
            )

            SectionLabel(
                "EXECUTIONS"
            )

            Spacer(
                Modifier.height(10.dp)
            )

            trade.executions
                .forEach { execution ->

                    ExecutionCard(
                        execution =
                            execution
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )
                }
        }
    }
}

@Composable
fun MarketReconstructionCard(
    trade: TradeEpisode
) {

    var requested by remember(
        trade.id
    ) {
        mutableStateOf(false)
    }

    var loading by remember(
        trade.id
    ) {
        mutableStateOf(false)
    }

    var candles by remember(
        trade.id
    ) {
        mutableStateOf<List<Candle>>(
            emptyList()
        )
    }

    var error by remember(
        trade.id
    ) {
        mutableStateOf<String?>(null)
    }

    var timeframe by remember(
        trade.id
    ) {
        mutableStateOf("15Min")
    }

    LaunchedEffect(
        requested,
        timeframe,
        trade.id
    ) {

        if (!requested) {
            return@LaunchedEffect
        }

        try {

            loading = true
            error = null
            candles = emptyList()

            val range =
                marketDateRange(
                    trade
                )

            candles =
                AlpacaMarketData.getBars(
                    symbol =
                        trade.symbol,
                    start =
                        range.first,
                    end =
                        range.second,
                    timeframe =
                        timeframe
                )

            if (candles.isEmpty()) {

                error =
                    "No historical candles were returned for this period."
            }

        } catch (e: Exception) {

            error =
                e.message
                    ?: "Unable to load market data."

        } finally {

            loading = false
        }
    }

    TraCard {

        Text(
            text =
                "MARKET RECONSTRUCTION",
            color = TraCyan,
            fontSize = 11.sp,
            fontWeight =
                FontWeight.Bold,
            letterSpacing = 1.4.sp
        )

        Spacer(
            Modifier.height(10.dp)
        )

        Text(
            text =
                "See what the market looked like around this trade.",
            color = TraText,
            fontSize = 20.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            Modifier.height(8.dp)
        )

        Text(
            text =
                "${trade.symbol} • ${trade.openDate}" +
                        if (
                            trade.closeDate != null
                        ) {
                            " → ${trade.closeDate}"
                        } else {
                            " → OPEN"
                        },

            color =
                TraTextSecondary,

            fontSize = 13.sp
        )

        Spacer(
            Modifier.height(18.dp)
        )

        if (!requested) {

            Button(
                onClick = {
                    requested = true
                },

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(
                        14.dp
                    ),

                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                TraCyan,

                            contentColor =
                                Color(
                                    0xFF061015
                                )
                        )
            ) {

                Text(
                    text =
                        "LOAD HISTORICAL CHART",
                    fontWeight =
                        FontWeight.Bold
                )
            }

        } else {

            TimeframeSelector(
                selected = timeframe,

                onSelected = {
                    timeframe = it
                }
            )

            Spacer(
                Modifier.height(16.dp)
            )

            if (loading) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),

                    contentAlignment =
                        Alignment.Center
                ) {

                    CircularProgressIndicator(
                        color = TraCyan
                    )
                }

            } else if (error != null) {

                Text(
                    text = error!!,
                    color = TraRed
                )

            } else {

                CandleChart(
                    candles = candles,
                    entryPrice =
                        trade.averageEntryPrice,
                    exitPrice =
                        trade.averageExitPrice
                )
            }
        }
    }
}

@Composable
fun TimeframeSelector(
    selected: String,
    onSelected: (String) -> Unit
) {

    val options =
        listOf(
            "5Min" to "5M",
            "15Min" to "15M",
            "1Hour" to "1H",
            "1Day" to "1D"
        )

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        options.forEach { option ->

            val active =
                selected == option.first

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(
                        RoundedCornerShape(
                            10.dp
                        )
                    )
                    .background(
                        if (active) {
                            TraCyan
                        } else {
                            TraBackground
                        }
                    )
                    .border(
                        1.dp,
                        if (active) {
                            TraCyan
                        } else {
                            TraBorder
                        },
                        RoundedCornerShape(
                            10.dp
                        )
                    )
                    .clickable {
                        onSelected(
                            option.first
                        )
                    }
                    .padding(
                        vertical = 10.dp
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = option.second,
                    color =
                        if (active) {
                            Color(
                                0xFF061015
                            )
                        } else {
                            TraTextSecondary
                        },
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ExecutionCard(
    execution: StockExecution
) {

    val color =
        if (
            execution.side == "BUY"
        ) {
            TraGreen
        } else {
            TraRed
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    16.dp
                )
            )
            .background(TraSurface)
            .border(
                1.dp,
                TraBorder,
                RoundedCornerShape(
                    16.dp
                )
            )
            .padding(14.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = execution.side,
            color = color,
            fontSize = 11.sp,
            fontWeight =
                FontWeight.Bold,
            modifier =
                Modifier.width(48.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text =
                    "${formatShares(execution.quantity)} shares",
                color = TraText,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text =
                    execution.activityDate,
                color =
                    TraTextSecondary,
                fontSize = 11.sp
            )
        }

        Text(
            text =
                money(
                    execution.statedPrice
                ),
            color = TraText,
            fontWeight =
                FontWeight.Medium
        )
    }
}

@Composable
fun DnaScreen(
    trades: List<TradeEpisode>
) {

    val closed =
        trades.filter {
            it.status ==
                    TradeStatus.CLOSED
        }

    val wins =
        closed.count {
            it.realizedPnl > 0.0
        }

    val winRate =
        if (closed.isNotEmpty()) {

            wins.toDouble() /
                    closed.size *
                    100.0

        } else {
            0.0
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(22.dp)
    ) {

        BrandHeader()

        Spacer(
            Modifier.height(30.dp)
        )

        Text(
            text = "TRADING DNA",
            color = TraText,
            fontSize = 28.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(24.dp)
        )

        TraCard {

            SectionLabel(
                "BASELINE"
            )

            Spacer(
                Modifier.height(18.dp)
            )

            StatRow(
                "Trade episodes",
                trades.size.toString()
            )

            StatRow(
                "Closed",
                closed.size.toString()
            )

            StatRow(
                "Winning",
                wins.toString()
            )

            StatRow(
                "Win rate",
                String.format(
                    Locale.US,
                    "%.1f%%",
                    winRate
                )
            )
        }
    }
}

@Composable
fun LabScreen(
    trades: List<TradeEpisode>
) {

    PlaceholderScreen(
        title = "AGENT LAB",
        subtitle =
            "${trades.size} trade episodes are reconstructed.\n\nMarket reconstruction, replay, and incremental trading-data imports are available."
    )
}

@Composable
fun ActionCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    actionText: String,
    accent: Color,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    24.dp
                )
            )
            .background(TraSurface)
            .border(
                1.dp,
                TraBorder,
                RoundedCornerShape(
                    24.dp
                )
            )
            .clickable {
                onClick()
            }
            .padding(22.dp)
    ) {

        Column {

            Text(
                text = eyebrow,
                color = accent,
                fontSize = 11.sp,
                fontWeight =
                    FontWeight.Bold,
                letterSpacing = 1.4.sp
            )

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                text = title,
                color = TraText,
                fontSize = 20.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                Modifier.height(7.dp)
            )

            Text(
                text = subtitle,
                color =
                    TraTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(
                Modifier.height(17.dp)
            )

            Text(
                text =
                    "$actionText  →",
                color = accent,
                fontSize = 12.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
fun ModelProgressCard(
    trades: List<TradeEpisode>
) {

    TraCard {

        SectionLabel(
            "MODEL STATE"
        )

        Spacer(
            Modifier.height(15.dp)
        )

        Text(
            text = "MAPPING",
            color = TraText,
            fontSize = 22.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            Modifier.height(10.dp)
        )

        Text(
            text =
                "${trades.size} stock trade episodes reconstructed. New trading activity can be merged without duplicating history.",
            color =
                TraTextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
fun EmptyImportCard(
    onImport: () -> Unit
) {

    TraCard {

        Text(
            text =
                "YOUR TRADING DNA",
            color =
                TraTextSecondary,
            fontSize = 11.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(18.dp)
        )

        Text(
            text =
                "Import your historical trading data to begin.",
            color = TraText,
            fontSize = 26.sp,
            fontWeight =
                FontWeight.Medium
        )

        Spacer(
            Modifier.height(22.dp)
        )

        Button(
            onClick = onImport,
            modifier =
                Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            TraCyan,
                        contentColor =
                            Color(
                                0xFF061015
                            )
                    )
        ) {

            Text(
                text =
                    "IMPORT TRADING HISTORY",
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
fun ActivityScreen(
    activities:
    List<RobinhoodActivity>,
    onBack: () -> Unit,
    onActivitySelected:
        (RobinhoodActivity) -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxSize()
    ) {

        ScreenHeader(
            title = "ACTIVITY",
            subtitle =
                "${activities.size} imported records",
            onBack = onBack
        )

        LazyColumn(
            contentPadding =
                PaddingValues(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            itemsIndexed(
                activities
            ) {
                    index,
                    activity ->

                ActivityRow(
                    number = index + 1,
                    activity = activity,
                    onClick = {
                        onActivitySelected(
                            activity
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ActivityRow(
    number: Int,
    activity: RobinhoodActivity,
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
            .background(TraSurface)
            .border(
                1.dp,
                TraBorder,
                RoundedCornerShape(
                    18.dp
                )
            )
            .clickable {
                onClick()
            }
            .padding(16.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text =
                    activity.instrument
                        .ifBlank {
                            "ACCOUNT"
                        },
                color = TraText,
                fontSize = 17.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text =
                    "${activity.transCode} • ${activity.activityDate}",
                color =
                    TraTextSecondary,
                fontSize = 12.sp
            )
        }

        Text(
            text =
                activity.amount
                    .ifBlank {
                        "#$number"
                    },
            color = TraText
        )
    }
}

@Composable
fun ActivityDetailScreen(
    activity: RobinhoodActivity,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                bottom = 30.dp
            )
    ) {

        ScreenHeader(
            title =
                activity.instrument
                    .ifBlank {
                        "ACTIVITY"
                    },
            subtitle =
                activity.transCode,
            onBack = onBack
        )

        Column(
            modifier =
                Modifier.padding(
                    horizontal = 20.dp
                )
        ) {

            TraCard {

                DetailRow(
                    "Activity date",
                    activity.activityDate
                )

                DetailRow(
                    "Process date",
                    activity.processDate
                )

                DetailRow(
                    "Settle date",
                    activity.settleDate
                )

                DetailRow(
                    "Instrument",
                    activity.instrument
                )

                DetailRow(
                    "Transaction",
                    activity.transCode
                )

                DetailRow(
                    "Quantity",
                    activity.quantity
                )

                DetailRow(
                    "Price",
                    activity.price
                )

                DetailRow(
                    "Amount",
                    activity.amount
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                Text(
                    text =
                        activity.description,
                    color = TraText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 18.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = "‹",
            color = TraCyan,
            fontSize = 38.sp,

            modifier =
                Modifier
                    .clickable {
                        onBack()
                    }
                    .padding(
                        end = 16.dp
                    )
        )

        Column {

            Text(
                text = title,
                color = TraText,
                fontSize = 24.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = subtitle,
                color =
                    TraTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 8.dp
            )
    ) {

        Text(
            text = label,
            color =
                TraTextSecondary,
            modifier =
                Modifier.weight(1f)
        )

        Text(
            text =
                value.ifBlank {
                    "—"
                },
            color = TraText,
            textAlign =
                TextAlign.End,
            fontWeight =
                FontWeight.Medium,
            modifier =
                Modifier.weight(1f)
        )
    }
}

@Composable
fun SectionLabel(
    text: String
) {

    Text(
        text = text,
        color =
            TraTextSecondary,
        fontSize = 11.sp,
        fontWeight =
            FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}

@Composable
fun StatRow(
    label: String,
    value: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 7.dp
            )
    ) {

        Text(
            text = label,
            color =
                TraTextSecondary,
            modifier =
                Modifier.weight(1f)
        )

        Text(
            text = value,
            color = TraText,
            fontSize = 18.sp,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}

@Composable
fun BrandHeader() {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Row {

                Text(
                    text = "Tra",
                    color = TraText,
                    fontSize = 28.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = "DNA",
                    color = TraCyan,
                    fontSize = 28.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Text(
                text =
                    "TRADING INTELLIGENCE",
                color =
                    TraTextSecondary,
                fontSize = 9.sp,
                letterSpacing = 2.5.sp
            )
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(TraSurface)
                .border(
                    1.dp,
                    TraBorder,
                    CircleShape
                ),

            contentAlignment =
                Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        TraGreen
                    )
            )
        }
    }
}

@Composable
fun TraCard(
    content:
    @Composable
    ColumnScope.() -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    24.dp
                )
            )
            .background(TraSurface)
            .border(
                1.dp,
                TraBorder,
                RoundedCornerShape(
                    24.dp
                )
            )
            .padding(22.dp),
        content = content
    )
}

@Composable
fun TraBottomNavigation(
    selected: TraDestination,
    onSelected:
        (TraDestination) -> Unit
) {

    NavigationBar(
        containerColor = TraSurface,
        tonalElevation = 0.dp
    ) {

        TraDestination.entries
            .forEach {
                    destination ->

                NavigationBarItem(
                    selected =
                        selected ==
                                destination,

                    onClick = {
                        onSelected(
                            destination
                        )
                    },

                    icon = {

                        Icon(
                            imageVector =
                                destination.icon,
                            contentDescription =
                                destination.label
                        )
                    },

                    label = {

                        Text(
                            text =
                                destination.label,
                            fontSize = 10.sp
                        )
                    },

                    colors =
                        NavigationBarItemDefaults
                            .colors(
                                selectedIconColor =
                                    TraCyan,
                                selectedTextColor =
                                    TraCyan,
                                unselectedIconColor =
                                    TraTextSecondary,
                                unselectedTextColor =
                                    TraTextSecondary,
                                indicatorColor =
                                    Color.Transparent
                            )
                )
            }
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = title,
                color = TraText,
                fontSize = 28.sp,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center
            )

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                text = subtitle,
                color =
                    TraTextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign =
                    TextAlign.Center
            )
        }
    }
}

fun buildSummary(
    activities:
    List<RobinhoodActivity>,
    fileName: String
): ImportSummary {

    val optionCodes =
        setOf(
            "STO",
            "BTC",
            "BTO",
            "STC",
            "OEXP",
            "OASGN"
        )

    val dates =
        activities
            .mapNotNull {

                try {

                    SimpleDateFormat(
                        "M/d/yyyy",
                        Locale.US
                    ).parse(
                        it.activityDate
                    )

                } catch (
                    _: Exception
                ) {
                    null
                }
            }

    val formatter =
        SimpleDateFormat(
            "MMM d, yyyy",
            Locale.US
        )

    return ImportSummary(
        activities = activities,
        activityCount =
            activities.size,

        buyCount =
            activities.count {
                it.transCode
                    .equals(
                        "Buy",
                        true
                    )
            },

        sellCount =
            activities.count {
                it.transCode
                    .equals(
                        "Sell",
                        true
                    )
            },

        optionCount =
            activities.count {
                it.transCode
                    .uppercase() in
                        optionCodes
            },

        instrumentCount =
            activities
                .map {
                    it.instrument
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()
                .size,

        startDate =
            dates.minOrNull()
                ?.let {
                    formatter.format(it)
                }
                ?: "Unknown",

        endDate =
            dates.maxOrNull()
                ?.let {
                    formatter.format(it)
                }
                ?: "Unknown",

        fileName = fileName
    )
}

fun money(
    value: Double
): String {

    return NumberFormat
        .getCurrencyInstance(
            Locale.US
        )
        .format(value)
}

fun formatShares(
    value: Double
): String {

    return if (
        kotlin.math.abs(
            value -
                    value.toLong()
        ) < 0.000001
    ) {

        value.toLong()
            .toString()

    } else {

        String.format(
            Locale.US,
            "%.4f",
            value
        )
            .trimEnd('0')
            .trimEnd('.')
    }
}

fun marketDateRange(
    trade: TradeEpisode
): Pair<String, String> {

    val formatter =
        DateTimeFormatter.ofPattern(
            "M/d/yyyy",
            Locale.US
        )

    val openDate =
        LocalDate.parse(
            trade.openDate,
            formatter
        )

    val closeDate =
        trade.closeDate
            ?.let {
                LocalDate.parse(
                    it,
                    formatter
                )
            }
            ?: openDate

    val start =
        openDate
            .minusDays(1)
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    val end =
        closeDate
            .plusDays(1)
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    return start to end
}

fun timeframeLabel(
    timeframe: String
): String {

    return when (timeframe) {
        "5Min" -> "5 MIN"
        "15Min" -> "15 MIN"
        "1Hour" -> "1 HOUR"
        "1Day" -> "DAILY"
        else -> timeframe
    }
}
