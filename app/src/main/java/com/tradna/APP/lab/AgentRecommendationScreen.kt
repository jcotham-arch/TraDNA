package com.tradna.APP.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.tradna.APP.market.AlpacaMarketData
import com.tradna.APP.market.Candle
import com.tradna.APP.market.TechnicalSignalEngine
import com.tradna.APP.market.TechnicalSnapshot
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private val AgentBackground = Color(0xFF07090D)
private val AgentSurface = Color(0xFF0E1219)
private val AgentSurface2 = Color(0xFF141A23)
private val AgentBorder = Color(0xFF222B38)

private val AgentText = Color(0xFFF4F7FB)
private val AgentSecondary = Color(0xFF8D98A8)

private val AgentCyan = Color(0xFF72E7FF)
private val AgentGreen = Color(0xFF39D6A0)
private val AgentRed = Color(0xFFFF657A)
private val AgentGold = Color(0xFFFFC857)
private val AgentViolet = Color(0xFF9B7CFF)

@Composable
fun AgentRecommendationScreen(
    onBack: () -> Unit
) {

    val context =
        LocalContext.current

    val records =
        remember {
            AgentTrainingStorage.loadRecords(
                context
            )
        }

    val patternProfile =
        remember(records) {
            PatternLearningEngine.analyze(
                records
            )
        }

    var symbol by remember {
        mutableStateOf(
            ""
        )
    }

    var stopInput by remember {
        mutableStateOf(
            ""
        )
    }

    var targetInput by remember {
        mutableStateOf(
            ""
        )
    }

    var requestedSymbol by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var loading by remember {
        mutableStateOf(
            false
        )
    }

    var error by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var result by remember {
        mutableStateOf<AgentTradeDecisionResult?>(
            null
        )
    }

    var currentSnapshot by remember {
        mutableStateOf<TechnicalSnapshot?>(
            null
        )
    }

    var predictionSaved by remember {
        mutableStateOf(
            false
        )
    }

    var savedPredictionId by remember {
        mutableStateOf<String?>(
            null
        )
    }

    LaunchedEffect(
        requestedSymbol
    ) {

        val activeSymbol =
            requestedSymbol
                ?: return@LaunchedEffect

        loading =
            true

        error =
            null

        result =
            null

        currentSnapshot =
            null

        predictionSaved =
            false

        savedPredictionId =
            null

        try {

            if (
                records.isEmpty()
            ) {

                error =
                    "The Agent Lab has no training records yet. Train the Lab from historical trades first."

                return@LaunchedEffect
            }

            val range =
                currentMarketDateRange()

            val candles =
                AlpacaMarketData.getBars(
                    symbol =
                        activeSymbol,
                    start =
                        range.first,
                    end =
                        range.second,
                    timeframe =
                        "15Min"
                )

            if (
                candles.isEmpty()
            ) {

                error =
                    "No current market candles were returned for $activeSymbol."

                return@LaunchedEffect
            }

            val analysisCandles =
                candles
                    .takeLast(
                        220
                    )

            val snapshot =
                TechnicalSignalEngine
                    .analyze(
                        analysisCandles
                    )

            if (
                snapshot ==
                null
            ) {

                error =
                    "TraDNA could not generate a technical snapshot for $activeSymbol."

                return@LaunchedEffect
            }

            currentSnapshot =
                snapshot

            val proposedStop =
                stopInput
                    .trim()
                    .toDoubleOrNull()

            val proposedTarget =
                targetInput
                    .trim()
                    .toDoubleOrNull()

            result =
                PersonalizedTradeScoringEngine
                    .score(
                        symbol =
                            activeSymbol,
                        snapshot =
                            snapshot,
                        records =
                            records,
                        patternProfile =
                            patternProfile,
                        proposedEntryPrice =
                            snapshot.price,
                        proposedStopPrice =
                            proposedStop,
                        proposedTargetPrice =
                            proposedTarget
                    )

        } catch (
            e: Exception
        ) {

            error =
                e.message
                    ?: "Unable to generate an agent recommendation."

        } finally {

            loading =
                false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    AgentBackground
                )
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    bottom =
                        32.dp
                )
    ) {

        AgentHeader(
            onBack =
                onBack
        )

        Column(
            modifier =
                Modifier.padding(
                    horizontal =
                        20.dp
                )
        ) {

            AgentIntroCard(
                trainingRecords =
                    records.size,
                profileConfidence =
                    patternProfile
                        .profileConfidencePercent
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            AgentInputCard(
                symbol =
                    symbol,
                onSymbolChange = {
                    symbol =
                        it
                            .uppercase()
                            .filter {
                                    char ->
                                char.isLetterOrDigit() ||
                                        char == '.' ||
                                        char == '-'
                            }
                            .take(
                                10
                            )
                },
                stopInput =
                    stopInput,
                onStopChange = {
                    stopInput =
                        sanitizeDecimalInput(
                            it
                        )
                },
                targetInput =
                    targetInput,
                onTargetChange = {
                    targetInput =
                        sanitizeDecimalInput(
                            it
                        )
                },
                loading =
                    loading,
                onAnalyze = {

                    val clean =
                        symbol
                            .trim()
                            .uppercase()

                    if (
                        clean.isBlank()
                    ) {

                        error =
                            "Enter a stock symbol."

                    } else {

                        /*
                         * Force a fresh request even if the user
                         * analyzes the same symbol twice.
                         */
                        requestedSymbol =
                            null

                        requestedSymbol =
                            clean
                    }
                }
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            when {

                loading -> {

                    AgentLoadingCard(
                        symbol =
                            requestedSymbol
                                ?: symbol
                    )
                }

                error != null -> {

                    AgentErrorCard(
                        message =
                            error!!
                    )
                }

                result != null &&
                        currentSnapshot != null -> {

                    AgentDecisionCard(
                        result =
                            result!!,
                        snapshot =
                            currentSnapshot!!
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    SavePredictionCard(
                        predictionSaved =
                            predictionSaved,
                        savedPredictionId =
                            savedPredictionId,
                        onSave = {

                            if (
                                !predictionSaved
                            ) {

                                val prediction =
                                    AgentPredictionJournal
                                        .createRecord(
                                            symbol =
                                                result!!.symbol,
                                            result =
                                                result!!,
                                            snapshot =
                                                currentSnapshot!!,
                                            proposedStopPrice =
                                                stopInput
                                                    .trim()
                                                    .toDoubleOrNull(),
                                            proposedTargetPrice =
                                                targetInput
                                                    .trim()
                                                    .toDoubleOrNull()
                                        )

                                AgentPredictionJournal
                                    .savePrediction(
                                        context =
                                            context,
                                        record =
                                            prediction
                                    )

                                savedPredictionId =
                                    prediction.id

                                predictionSaved =
                                    true
                            }
                        }
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    AgentScoreBreakdownCard(
                        result =
                            result!!
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    AgentEvidenceCard(
                        result =
                            result!!
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    AgentStrengthsWarningsCard(
                        result =
                            result!!
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    HistoricalMatchesCard(
                        result =
                            result!!
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    AgentReasoningCard(
                        result =
                            result!!
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentHeader(
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
                AgentCyan,
            fontSize =
                38.sp,
            modifier =
                Modifier
                    .clickable {
                        onBack()
                    }
                    .padding(
                        end =
                            14.dp
                    )
        )

        Column {

            Text(
                text =
                    "TRADNA AGENT",
                color =
                    AgentText,
                fontSize =
                    25.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    "Personalized Trade Recommendation",
                color =
                    AgentSecondary,
                fontSize =
                    12.sp
            )
        }
    }
}

@Composable
private fun AgentIntroCard(
    trainingRecords: Int,
    profileConfidence: Int
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "ADVISORY ENGINE",
            color =
                AgentViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Text(
            text =
                "Evaluate a current setup against your historical trading DNA.",
            color =
                AgentText,
            fontSize =
                21.sp,
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
                "TraDNA combines current technical conditions with your trained historical patterns and the closest matching trades in the Lab.",
            color =
                AgentSecondary,
            fontSize =
                13.sp,
            lineHeight =
                20.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    15.dp
                )
        )

        AgentValueRow(
            label =
                "Lab training records",
            value =
                trainingRecords.toString(),
            valueColor =
                AgentCyan
        )

        AgentValueRow(
            label =
                "Pattern profile confidence",
            value =
                "$profileConfidence%",
            valueColor =
                confidenceColor(
                    profileConfidence
                )
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                "Advisory only. This screen does not place orders.",
            color =
                AgentGold,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun AgentInputCard(
    symbol: String,
    onSymbolChange: (String) -> Unit,
    stopInput: String,
    onStopChange: (String) -> Unit,
    targetInput: String,
    onTargetChange: (String) -> Unit,
    loading: Boolean,
    onAnalyze: () -> Unit
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "ANALYZE CURRENT SETUP",
            color =
                AgentCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        OutlinedTextField(
            value =
                symbol,
            onValueChange =
                onSymbolChange,
            label = {
                Text(
                    "Symbol"
                )
            },
            placeholder = {
                Text(
                    "PLTR"
                )
            },
            singleLine =
                true,
            modifier =
                Modifier
                    .fillMaxWidth(),
            colors =
                agentTextFieldColors()
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

            OutlinedTextField(
                value =
                    stopInput,
                onValueChange =
                    onStopChange,
                label = {
                    Text(
                        "Stop (optional)"
                    )
                },
                placeholder = {
                    Text(
                        "178.50"
                    )
                },
                singleLine =
                    true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Decimal
                    ),
                modifier =
                    Modifier.weight(
                        1f
                    ),
                colors =
                    agentTextFieldColors()
            )

            OutlinedTextField(
                value =
                    targetInput,
                onValueChange =
                    onTargetChange,
                label = {
                    Text(
                        "Target (optional)"
                    )
                },
                placeholder = {
                    Text(
                        "190.00"
                    )
                },
                singleLine =
                    true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Decimal
                    ),
                modifier =
                    Modifier.weight(
                        1f
                    ),
                colors =
                    agentTextFieldColors()
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
                onAnalyze,
            enabled =
                !loading,
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
                            AgentCyan,
                        contentColor =
                            AgentBackground
                    )
        ) {

            Text(
                text =
                    if (
                        loading
                    ) {
                        "ANALYZING..."
                    } else {
                        "RUN PERSONALIZED ANALYSIS"
                    },
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AgentDecisionCard(
    result: AgentTradeDecisionResult,
    snapshot: TechnicalSnapshot
) {

    val decisionColor =
        decisionColor(
            result.decision
        )

    AgentCard {

        AgentSectionLabel(
            text =
                "AGENT DECISION",
            color =
                decisionColor
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                Text(
                    text =
                        result.decision
                            .name
                            .replace(
                                "_",
                                " "
                            ),
                    color =
                        decisionColor,
                    fontSize =
                        27.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        result.symbol,
                    color =
                        AgentSecondary,
                    fontSize =
                        13.sp
                )
            }

            Column(
                horizontalAlignment =
                    Alignment.End
            ) {

                Text(
                    text =
                        "${result.overallScore}",
                    color =
                        decisionColor,
                    fontSize =
                        34.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        "/ 100",
                    color =
                        AgentSecondary,
                    fontSize =
                        10.sp
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    15.dp
                )
        )

        AgentValueRow(
            label =
                "Current price",
            value =
                formatPrice(
                    snapshot.price
                )
        )

        AgentValueRow(
            label =
                "Decision confidence",
            value =
                "${result.confidencePercent}%",
            valueColor =
                confidenceColor(
                    result.confidencePercent
                )
        )

        AgentValueRow(
            label =
                "Preferred entry",
            value =
                result.preferredEntryMethod,
            valueColor =
                AgentCyan
        )

        AgentValueRow(
            label =
                "Preferred management",
            value =
                result.preferredExitMethod,
            valueColor =
                AgentViolet
        )
    }
}

@Composable
private fun SavePredictionCard(
    predictionSaved: Boolean,
    savedPredictionId: String?,
    onSave: () -> Unit
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "PREDICTION JOURNAL",
            color =
                AgentGreen
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
                    predictionSaved
                ) {
                    "This recommendation is now locked into the Agent Journal."
                } else {
                    "Save this recommendation before the trade happens so TraDNA can compare the prediction with the eventual outcome."
                },
            color =
                AgentText,
            fontSize =
                14.sp,
            lineHeight =
                20.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Button(
            onClick =
                onSave,
            enabled =
                !predictionSaved,
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
                            AgentGreen,
                        contentColor =
                            AgentBackground,
                        disabledContainerColor =
                            AgentSurface2,
                        disabledContentColor =
                            AgentGreen
                    )
        ) {

            Text(
                text =
                    if (
                        predictionSaved
                    ) {
                        "PREDICTION SAVED"
                    } else {
                        "SAVE AGENT PREDICTION"
                    },
                fontWeight =
                    FontWeight.Bold
            )
        }

        if (
            predictionSaved
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        9.dp
                    )
            )

            Text(
                text =
                    "This snapshot will remain unchanged even if you analyze the symbol again later.",
                color =
                    AgentSecondary,
                fontSize =
                    10.sp,
                lineHeight =
                    15.sp
            )

            if (
                !savedPredictionId
                    .isNullOrBlank()
            ) {

                Text(
                    text =
                        "Journal ID: ${savedPredictionId.take(8)}",
                    color =
                        AgentSecondary,
                    fontSize =
                        9.sp
                )
            }
        }
    }
}

@Composable
private fun AgentScoreBreakdownCard(
    result: AgentTradeDecisionResult
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "SCORE BREAKDOWN",
            color =
                AgentCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        ScoreRow(
            label =
                "Technical",
            score =
                result
                    .scoreBreakdown
                    .technicalScore
        )

        ScoreRow(
            label =
                "Personal history match",
            score =
                result
                    .scoreBreakdown
                    .historyMatchScore
        )

        ScoreRow(
            label =
                "Entry quality",
            score =
                result
                    .scoreBreakdown
                    .entryQualityScore
        )

        ScoreRow(
            label =
                "Risk quality",
            score =
                result
                    .scoreBreakdown
                    .riskQualityScore
        )

        ScoreRow(
            label =
                "Evidence confidence",
            score =
                result
                    .scoreBreakdown
                    .evidenceConfidenceScore
        )
    }
}

@Composable
private fun ScoreRow(
    label: String,
    score: Int
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        7.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                label,
            color =
                AgentSecondary,
            fontSize =
                12.sp,
            modifier =
                Modifier.weight(
                    1f
                )
        )

        Box(
            modifier =
                Modifier
                    .clip(
                        RoundedCornerShape(
                            8.dp
                        )
                    )
                    .background(
                        scoreColor(
                            score
                        )
                            .copy(
                                alpha =
                                    0.12f
                            )
                    )
                    .padding(
                        horizontal =
                            10.dp,
                        vertical =
                            5.dp
                    )
        ) {

            Text(
                text =
                    "$score / 100",
                color =
                    scoreColor(
                        score
                    ),
                fontSize =
                    11.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AgentEvidenceCard(
    result: AgentTradeDecisionResult
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "PERSONAL HISTORICAL EVIDENCE",
            color =
                AgentViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                result.evidenceSummary,
            color =
                AgentText,
            fontSize =
                14.sp,
            lineHeight =
                21.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        AgentValueRow(
            label =
                "Similar historical trades",
            value =
                result
                    .matchedTradeCount
                    .toString()
        )

        AgentValueRow(
            label =
                "Profitable rate",
            value =
                result
                    .historicalProfitableRatePercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                result
                    .historicalProfitableRatePercent
                    ?.let {
                        if (
                            it >=
                            50.0
                        ) {
                            AgentGreen
                        } else {
                            AgentRed
                        }
                    }
                    ?: AgentSecondary
        )

        AgentValueRow(
            label =
                "Average return",
            value =
                result
                    .historicalAverageReturnPercent
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                signedColor(
                    result
                        .historicalAverageReturnPercent
                )
        )

        AgentValueRow(
            label =
                "Average MFE",
            value =
                result
                    .historicalAverageMfePercent
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                AgentGreen
        )

        AgentValueRow(
            label =
                "Average MAE",
            value =
                result
                    .historicalAverageMaePercent
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                AgentRed
        )
    }
}

@Composable
private fun AgentStrengthsWarningsCard(
    result: AgentTradeDecisionResult
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "WHY / CAUTION",
            color =
                AgentGold
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "STRENGTHS",
            color =
                AgentGreen,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(
                    6.dp
                )
        )

        result.strengths
            .forEach {
                    item ->

                AgentBullet(
                    text =
                        item,
                    color =
                        AgentGreen
                )
            }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        Text(
            text =
                "CAUTIONS",
            color =
                AgentGold,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(
                    6.dp
                )
        )

        if (
            result.warnings
                .isEmpty()
        ) {

            AgentBullet(
                text =
                    "No major personalized warning triggered by the current rule set.",
                color =
                    AgentGreen
            )

        } else {

            result.warnings
                .forEach {
                        item ->

                    AgentBullet(
                        text =
                            item,
                        color =
                            if (
                                item.startsWith(
                                    "HIGH RISK:"
                                )
                            ) {
                                AgentRed
                            } else {
                                AgentGold
                            }
                    )
                }
        }
    }
}

@Composable
private fun HistoricalMatchesCard(
    result: AgentTradeDecisionResult
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "CLOSEST HISTORICAL MATCHES",
            color =
                AgentCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        if (
            result
                .matchedHistoricalTrades
                .isEmpty()
        ) {

            Text(
                text =
                    "No historical trade met the current similarity threshold.",
                color =
                    AgentSecondary,
                fontSize =
                    12.sp
            )

            return@AgentCard
        }

        result
            .matchedHistoricalTrades
            .take(
                8
            )
            .forEach {
                    match ->

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
                                AgentSurface2
                            )
                            .border(
                                width =
                                    1.dp,
                                color =
                                    AgentBorder,
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

                        Column(
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {

                            Text(
                                text =
                                    "${match.symbol} • ${match.openDate}",
                                color =
                                    AgentText,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                text =
                                    "${match.similarityPercent}% similarity",
                                color =
                                    AgentCyan,
                                fontSize =
                                    10.sp,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }

                        Text(
                            text =
                                match
                                    .actualReturnPercent
                                    ?.let {
                                        formatSignedPercent(
                                            it
                                        )
                                    }
                                    ?: "—",
                            color =
                                signedColor(
                                    match
                                        .actualReturnPercent
                                ),
                            fontSize =
                                15.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    if (
                        !match
                            .bestAlternativeTitle
                            .isNullOrBlank()
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        Text(
                            text =
                                "Best modeled rule: ${match.bestAlternativeTitle}",
                            color =
                                AgentSecondary,
                            fontSize =
                                10.sp
                        )
                    }
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

@Composable
private fun AgentReasoningCard(
    result: AgentTradeDecisionResult
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "AGENT REASONING",
            color =
                AgentViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        result.reasoning
            .forEachIndexed {
                    index,
                    item ->

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
                            "${index + 1}.",
                        color =
                            AgentViolet,
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.padding(
                                end =
                                    8.dp
                            )
                    )

                    Text(
                        text =
                            item,
                        color =
                            AgentText,
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
    }
}

@Composable
private fun AgentLoadingCard(
    symbol: String
) {

    AgentCard {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        190.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            CircularProgressIndicator(
                color =
                    AgentCyan
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            Text(
                text =
                    "Analyzing $symbol...",
                color =
                    AgentSecondary,
                fontSize =
                    13.sp
            )
        }
    }
}

@Composable
private fun AgentErrorCard(
    message: String
) {

    AgentCard {

        AgentSectionLabel(
            text =
                "AGENT ERROR",
            color =
                AgentRed
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
                AgentText,
            fontSize =
                13.sp,
            lineHeight =
                19.sp
        )
    }
}

@Composable
private fun AgentBullet(
    text: String,
    color: Color
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
                "●",
            color =
                color,
            fontSize =
                8.sp,
            modifier =
                Modifier.padding(
                    end =
                        8.dp,
                    top =
                        2.dp
                )
        )

        Text(
            text =
                text,
            color =
                AgentText,
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

@Composable
private fun AgentValueRow(
    label: String,
    value: String,
    valueColor: Color =
        AgentText
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
                AgentSecondary,
            fontSize =
                11.sp,
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
                11.sp,
            fontWeight =
                FontWeight.SemiBold,
            modifier =
                Modifier.weight(
                    1f
                )
        )
    }
}

@Composable
private fun AgentSectionLabel(
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
            1.2.sp
    )
}

@Composable
private fun AgentCard(
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
                    AgentSurface
                )
                .border(
                    width =
                        1.dp,
                    color =
                        AgentBorder,
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

@Composable
private fun agentTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor =
            AgentText,
        unfocusedTextColor =
            AgentText,
        focusedBorderColor =
            AgentCyan,
        unfocusedBorderColor =
            AgentBorder,
        focusedLabelColor =
            AgentCyan,
        unfocusedLabelColor =
            AgentSecondary,
        cursorColor =
            AgentCyan,
        focusedContainerColor =
            AgentSurface2,
        unfocusedContainerColor =
            AgentSurface2
    )

private fun currentMarketDateRange(): Pair<String, String> {

    val endDate =
        LocalDate
            .now(
                ZoneOffset.UTC
            )
            .plusDays(
                1
            )

    val startDate =
        endDate
            .minusDays(
                12
            )

    val start =
        startDate
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    val end =
        endDate
            .atStartOfDay(
                ZoneOffset.UTC
            )
            .toInstant()
            .toString()

    return start to end
}

private fun sanitizeDecimalInput(
    value: String
): String {

    var decimalSeen =
        false

    return buildString {

        value.forEach {
                char ->

            when {

                char.isDigit() ->
                    append(
                        char
                    )

                char == '.' &&
                        !decimalSeen -> {

                    decimalSeen =
                        true

                    append(
                        char
                    )
                }
            }
        }
    }
        .take(
            12
        )
}

private fun decisionColor(
    decision: AgentTradeDecision
): Color {

    return when (
        decision
    ) {

        AgentTradeDecision.HIGH_CONVICTION ->
            AgentGreen

        AgentTradeDecision.FAVORABLE ->
            AgentCyan

        AgentTradeDecision.WATCH ->
            AgentGold

        AgentTradeDecision.WAIT ->
            AgentViolet

        AgentTradeDecision.AVOID ->
            AgentRed
    }
}

private fun scoreColor(
    score: Int
): Color {

    return when {

        score >= 75 ->
            AgentGreen

        score >= 60 ->
            AgentCyan

        score >= 45 ->
            AgentGold

        else ->
            AgentRed
    }
}

private fun confidenceColor(
    score: Int
): Color {

    return when {

        score >= 70 ->
            AgentGreen

        score >= 50 ->
            AgentCyan

        score >= 30 ->
            AgentGold

        else ->
            AgentViolet
    }
}

private fun signedColor(
    value: Double?
): Color {

    return when {

        value == null ->
            AgentSecondary

        value > 0.0 ->
            AgentGreen

        value < 0.0 ->
            AgentRed

        else ->
            AgentText
    }
}

private fun formatPrice(
    value: Double
): String {

    return if (
        value <
        10.0
    ) {

        String.format(
            Locale.US,
            "$%.3f",
            value
        )

    } else {

        String.format(
            Locale.US,
            "$%.2f",
            value
        )
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
