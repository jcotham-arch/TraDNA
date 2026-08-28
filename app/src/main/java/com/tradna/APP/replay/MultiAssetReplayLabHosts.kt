package com.tradna.APP.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.data.MultiAssetTradeEpisode
import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.lab.AgentLabDashboard
import com.tradna.APP.lab.OptionContextTrainingEngine
import com.tradna.APP.lab.OptionContextTrainingStorage
import kotlinx.coroutines.launch

private val HostBackground =
    Color(0xFF07090D)

private val HostSurface =
    Color(0xFF0E1219)

private val HostBorder =
    Color(0xFF222B38)

private val HostText =
    Color(0xFFF4F7FB)

private val HostSecondary =
    Color(0xFF8D98A8)

private val HostCyan =
    Color(0xFF72E7FF)

private val HostViolet =
    Color(0xFF9B7CFF)

private val HostGreen =
    Color(0xFF39D6A0)

private val HostRed =
    Color(0xFFFF657A)

private val HostGold =
    Color(0xFFFFC857)

private enum class ReplayHostView {
    ALL_ASSETS,
    STOCK_REPLAY
}

private enum class LabHostView {
    MULTI_ASSET,
    AGENT_LAB
}

@Composable
fun MultiAssetReplayHost(
    stockTrades: List<TradeEpisode>,
    multiAssetTrades: List<MultiAssetTradeEpisode>
) {

    var selectedView by remember {
        mutableStateOf(
            ReplayHostView.ALL_ASSETS
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    HostBackground
                )
    ) {

        HostSelector(
            leftText =
                "ALL ASSETS",

            rightText =
                "STOCK REPLAY",

            leftSelected =
                selectedView ==
                        ReplayHostView.ALL_ASSETS,

            onLeft = {
                selectedView =
                    ReplayHostView.ALL_ASSETS
            },

            onRight = {
                selectedView =
                    ReplayHostView.STOCK_REPLAY
            },

            accent =
                HostCyan
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(
                        1f
                    )
        ) {

            when (
                selectedView
            ) {

                ReplayHostView.ALL_ASSETS -> {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    rememberScrollState()
                                )
                                .padding(
                                    start =
                                        18.dp,
                                    end =
                                        18.dp,
                                    bottom =
                                        28.dp
                                )
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        Text(
                            text =
                                "REPLAY",
                            color =
                                HostText,
                            fontSize =
                                28.sp,
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
                                "All reconstructed stocks, options, and futures",
                            color =
                                HostSecondary,
                            fontSize =
                                12.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    18.dp
                                )
                        )

                        MultiAssetReplayPanel(
                            trades =
                                multiAssetTrades
                        )
                    }
                }

                ReplayHostView.STOCK_REPLAY -> {

                    ReplayModule(
                        trades =
                            stockTrades
                    )
                }
            }
        }
    }
}

@Composable
fun MultiAssetLabHost(
    stockTrades: List<TradeEpisode>,
    multiAssetTrades: List<MultiAssetTradeEpisode>
) {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    var selectedView by remember {
        mutableStateOf(
            LabHostView.MULTI_ASSET
        )
    }

    var trainedOptionTradeIds by remember(
        context
    ) {
        mutableStateOf(
            OptionContextTrainingStorage
                .loadRecords(
                    context
                )
                .map {
                    it.tradeId
                }
                .toSet()
        )
    }

    var optionTrainingRunning by remember {
        mutableStateOf(
            false
        )
    }

    var optionTrainingStatus by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var optionTrainingProgress by remember {
        mutableStateOf<String?>(
            null
        )
    }

    val optionTrades =
        multiAssetTrades.filter {
            it.assetType.name ==
                    "OPTION"
        }

    val untrainedOptionTrades =
        optionTrades.filter {
            it.id !in
                    trainedOptionTradeIds
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    HostBackground
                )
    ) {

        HostSelector(
            leftText =
                "MULTI-ASSET",

            rightText =
                "AGENT LAB",

            leftSelected =
                selectedView ==
                        LabHostView.MULTI_ASSET,

            onLeft = {
                selectedView =
                    LabHostView.MULTI_ASSET
            },

            onRight = {
                selectedView =
                    LabHostView.AGENT_LAB
            },

            accent =
                HostViolet
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(
                        1f
                    )
        ) {

            when (
                selectedView
            ) {

                LabHostView.MULTI_ASSET -> {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    rememberScrollState()
                                )
                                .padding(
                                    start =
                                        18.dp,
                                    end =
                                        18.dp,
                                    bottom =
                                        28.dp
                                )
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        Text(
                            text =
                                "AGENT LAB",
                            color =
                                HostText,
                            fontSize =
                                28.sp,
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
                                "Multi-asset training readiness and derivative queue",
                            color =
                                HostSecondary,
                            fontSize =
                                12.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    18.dp
                                )
                        )

                        OptionContextTrainingCard(
                            totalOptionTrades =
                                optionTrades.size,
                            trainedOptionTrades =
                                optionTrades.count {
                                    it.id in
                                            trainedOptionTradeIds
                                },
                            remainingOptionTrades =
                                untrainedOptionTrades.size,
                            isRunning =
                                optionTrainingRunning,
                            progressText =
                                optionTrainingProgress,
                            statusText =
                                optionTrainingStatus,
                            onTrain = {

                                if (
                                    optionTrainingRunning ||
                                    untrainedOptionTrades.isEmpty()
                                ) {
                                    return@OptionContextTrainingCard
                                }

                                coroutineScope.launch {

                                    optionTrainingRunning =
                                        true

                                    optionTrainingStatus =
                                        null

                                    var trainedThisRun =
                                        0

                                    val failures =
                                        mutableListOf<String>()

                                    try {

                                        val queue =
                                            OptionContextTrainingEngine
                                                .untrainedOptionTrades(
                                                    context =
                                                        context,
                                                    trades =
                                                        multiAssetTrades
                                                )

                                        queue.forEachIndexed {
                                                index,
                                                trade ->

                                            val contextSymbol =
                                                trade.underlyingSymbol
                                                    ?.takeIf {
                                                        it.isNotBlank()
                                                    }
                                                    ?: trade.analysisSymbol
                                                        .ifBlank {
                                                            trade.symbol
                                                        }

                                            optionTrainingProgress =
                                                "Analyzing ${index + 1} of ${queue.size} • $contextSymbol"

                                            val attempt =
                                                OptionContextTrainingEngine
                                                    .analyzeAndStore(
                                                        context =
                                                            context,
                                                        trade =
                                                            trade
                                                    )

                                            if (
                                                attempt.success
                                            ) {
                                                trainedThisRun++
                                            } else {
                                                failures.add(
                                                    attempt.reason
                                                        ?: "${trade.symbol}: training failed."
                                                )
                                            }
                                        }

                                        trainedOptionTradeIds =
                                            OptionContextTrainingStorage
                                                .loadRecords(
                                                    context
                                                )
                                                .map {
                                                    it.tradeId
                                                }
                                                .toSet()

                                        optionTrainingStatus =
                                            when {

                                                queue.isEmpty() ->
                                                    "Option context is already current."

                                                failures.isEmpty() ->
                                                    "$trainedThisRun option context record${if (trainedThisRun == 1) "" else "s"} trained successfully."

                                                trainedThisRun >
                                                        0 ->
                                                    "$trainedThisRun trained • ${failures.size} could not be trained. ${failures.first()}"

                                                else ->
                                                    "No option context records were trained. ${failures.firstOrNull() ?: "No eligible option trades were available."}"
                                            }

                                    } finally {

                                        optionTrainingProgress =
                                            null

                                        optionTrainingRunning =
                                            false
                                    }
                                }
                            }
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    14.dp
                                )
                        )

                        MultiAssetLabPanel(
                            trades =
                                multiAssetTrades,
                            trainedOptionTradeIds =
                                trainedOptionTradeIds
                        )
                    }
                }

                LabHostView.AGENT_LAB -> {

                    AgentLabDashboard(
                        trades =
                            stockTrades
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionContextTrainingCard(
    totalOptionTrades: Int,
    trainedOptionTrades: Int,
    remainingOptionTrades: Int,
    isRunning: Boolean,
    progressText: String?,
    statusText: String?,
    onTrain: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color =
                        HostSurface,
                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                )
                .border(
                    width =
                        1.dp,
                    color =
                        HostBorder,
                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                )
                .padding(
                    18.dp
                )
    ) {

        Text(
            text =
                "OPTION CONTEXT TRAINING",
            color =
                HostGold,
            fontSize =
                10.sp,
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
                "$trainedOptionTrades of $totalOptionTrades option trades trained",
            color =
                HostText,
            fontSize =
                18.sp,
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
                "TraDNA reconstructs the underlying stock market around each option entry and stores that evidence separately from stock Agent training.",
            color =
                HostSecondary,
            fontSize =
                11.sp
        )

        if (
            progressText !=
            null
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .height(
                                18.dp
                            )
                            .fillMaxWidth(
                                0.05f
                            ),
                    color =
                        HostGold,
                    strokeWidth =
                        2.dp
                )

                Text(
                    text =
                        progressText,
                    color =
                        HostSecondary,
                    fontSize =
                        11.sp,
                    modifier =
                        Modifier.padding(
                            start =
                                10.dp
                        )
                )
            }
        }

        if (
            statusText !=
            null
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Text(
                text =
                    statusText,
                color =
                    if (
                        statusText.contains(
                            "success",
                            ignoreCase =
                                true
                        ) ||
                        statusText.contains(
                            "current",
                            ignoreCase =
                                true
                        )
                    ) {
                        HostGreen
                    } else {
                        HostSecondary
                    },
                fontSize =
                    11.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        Button(
            onClick =
                onTrain,
            enabled =
                !isRunning &&
                        remainingOptionTrades >
                        0,
            modifier =
                Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            HostGold,
                        contentColor =
                            Color(
                                0xFF111008
                            ),
                        disabledContainerColor =
                            HostBorder,
                        disabledContentColor =
                            HostSecondary
                    )
        ) {

            Text(
                text =
                    when {

                        isRunning ->
                            "TRAINING OPTION CONTEXT..."

                        remainingOptionTrades <=
                                0 &&
                                totalOptionTrades >
                                0 ->
                            "OPTION CONTEXT CURRENT"

                        totalOptionTrades ==
                                0 ->
                            "NO OPTION TRADES TO TRAIN"

                        trainedOptionTrades >
                                0 ->
                            "UPDATE OPTION CONTEXT"

                        else ->
                            "TRAIN OPTION CONTEXT"
                    },
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    11.sp
            )
        }
    }
}

@Composable
private fun HostSelector(
    leftText: String,
    rightText: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    accent: Color
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        18.dp,
                    vertical =
                        12.dp
                )
                .background(
                    color =
                        HostSurface,
                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                )
                .border(
                    width =
                        1.dp,
                    color =
                        HostBorder,
                    shape =
                        RoundedCornerShape(
                            14.dp
                        )
                )
                .padding(
                    4.dp
                )
    ) {

        HostSelectorItem(
            text =
                leftText,
            selected =
                leftSelected,
            onClick =
                onLeft,
            accent =
                accent,
            modifier =
                Modifier.weight(
                    1f
                )
        )

        HostSelectorItem(
            text =
                rightText,
            selected =
                !leftSelected,
            onClick =
                onRight,
            accent =
                accent,
            modifier =
                Modifier.weight(
                    1f
                )
        )
    }
}

@Composable
private fun HostSelectorItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {

    Box(
        modifier =
            modifier
                .background(
                    color =
                        if (
                            selected
                        ) {
                            accent
                        } else {
                            Color.Transparent
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
                    Color(
                        0xFF061015
                    )
                } else {
                    HostSecondary
                },
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}
