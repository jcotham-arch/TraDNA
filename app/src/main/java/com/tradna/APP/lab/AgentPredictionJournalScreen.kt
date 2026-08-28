package com.tradna.APP.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.data.TradeEpisode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val JournalBackground = Color(0xFF07090D)
private val JournalSurface = Color(0xFF0E1219)
private val JournalSurface2 = Color(0xFF141A23)
private val JournalBorder = Color(0xFF222B38)

private val JournalText = Color(0xFFF4F7FB)
private val JournalSecondary = Color(0xFF8D98A8)

private val JournalCyan = Color(0xFF72E7FF)
private val JournalGreen = Color(0xFF39D6A0)
private val JournalRed = Color(0xFFFF657A)
private val JournalGold = Color(0xFFFFC857)
private val JournalViolet = Color(0xFF9B7CFF)

@Composable
fun AgentPredictionJournalScreen(
    trades: List<TradeEpisode>,
    onBack: () -> Unit
) {

    val context =
        LocalContext.current

    var refreshVersion by remember {
        mutableIntStateOf(
            0
        )
    }

    val predictions =
        remember(
            refreshVersion
        ) {
            AgentPredictionJournal.loadPredictions(
                context
            )
        }

    val performance =
        remember(
            predictions
        ) {
            AgentPerformanceEngine.analyze(
                predictions
            )
        }

    val calibration =
        remember(
            predictions
        ) {
            AgentCalibrationEngine.analyze(
                predictions
            )
        }

    val directionalPredictions =
        predictions.filter {
            it.predictionDirectionCorrect !=
                    null
        }

    val correctDirectional =
        directionalPredictions.count {
            it.predictionDirectionCorrect ==
                    true
        }

    val directionalAccuracy =
        if (
            directionalPredictions.isNotEmpty()
        ) {

            correctDirectional.toDouble() /
                    directionalPredictions.size *
                    100.0

        } else {

            null
        }

    val linked =
        predictions.filter {
            it.outcomeLinked
        }

    val averageLinkedReturn =
        linked
            .mapNotNull {
                it.actualReturnPercent
            }
            .takeIf {
                it.isNotEmpty()
            }
            ?.average()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    JournalBackground
                )
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal =
                        20.dp,
                    vertical =
                        18.dp
                )
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    "‹",
                color =
                    JournalCyan,
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
                        "AGENT JOURNAL",
                    color =
                        JournalText,
                    fontSize =
                        27.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        "Prediction → outcome validation",
                    color =
                        JournalSecondary,
                    fontSize =
                        12.sp
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        JournalCard {

            JournalLabel(
                text =
                    "AGENT PERFORMANCE",
                color =
                    JournalViolet
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            JournalValueRow(
                label =
                    "Predictions saved",
                value =
                    performance.totalPredictions
                        .toString(),
                valueColor =
                    JournalCyan
            )

            JournalValueRow(
                label =
                    "Outcomes linked",
                value =
                    performance.linkedOutcomes
                        .toString(),
                valueColor =
                    JournalGreen
            )

            JournalValueRow(
                label =
                    "Unresolved predictions",
                value =
                    performance.unresolvedPredictions
                        .toString(),
                valueColor =
                    JournalGold
            )

            JournalValueRow(
                label =
                    "Directional calls scored",
                value =
                    performance.scoredDirectionalCalls
                        .toString()
            )

            JournalValueRow(
                label =
                    "Directional accuracy",
                value =
                    performance.directionalAccuracyPercent
                        ?.let {
                            formatPercent(
                                it
                            )
                        }
                        ?: "—",
                valueColor =
                    accuracyColor(
                        performance.directionalAccuracyPercent
                    )
            )

            JournalValueRow(
                label =
                    "Profitable linked outcomes",
                value =
                    performance.profitableLinkedOutcomeRatePercent
                        ?.let {
                            formatPercent(
                                it
                            )
                        }
                        ?: "—",
                valueColor =
                    accuracyColor(
                        performance.profitableLinkedOutcomeRatePercent
                    )
            )

            JournalValueRow(
                label =
                    "Average actual return",
                value =
                    performance.averageActualReturnPercent
                        ?.let {
                            formatSignedPercent(
                                it
                            )
                        }
                        ?: "—",
                valueColor =
                    signedColor(
                        performance.averageActualReturnPercent
                    )
            )

            JournalValueRow(
                label =
                    "Median actual return",
                value =
                    performance.medianActualReturnPercent
                        ?.let {
                            formatSignedPercent(
                                it
                            )
                        }
                        ?: "—",
                valueColor =
                    signedColor(
                        performance.medianActualReturnPercent
                    )
            )

            JournalValueRow(
                label =
                    "Total realized P&L",
                value =
                    performance.totalActualRealizedPnl
                        ?.let {
                            formatMoneySigned(
                                it
                            )
                        }
                        ?: "—",
                valueColor =
                    signedColor(
                        performance.totalActualRealizedPnl
                    )
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            Text(
                text =
                    performance.calibrationSummary,
                color =
                    JournalText,
                fontSize =
                    12.sp,
                lineHeight =
                    18.sp
            )

            performance.evidenceWarning
                ?.let {
                        warning ->

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    Text(
                        text =
                            warning,
                        color =
                            JournalGold,
                        fontSize =
                            10.sp,
                        lineHeight =
                            15.sp,
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

        JournalCard {

            JournalLabel(
                text =
                    "DECISION CALIBRATION",
                color =
                    JournalCyan
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            Text(
                text =
                    "How each Agent decision category is performing prospectively.",
                color =
                    JournalText,
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            if (
                performance.decisionPerformance
                    .isEmpty()
            ) {

                Text(
                    text =
                        "No Agent predictions have been saved yet.",
                    color =
                        JournalSecondary,
                    fontSize =
                        12.sp
                )

            } else {

                performance.decisionPerformance
                    .forEach {
                            item ->

                        DecisionPerformanceRow(
                            item =
                                item
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )
                    }
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        JournalCard {

            JournalLabel(
                text =
                    "AGENT CALIBRATION",
                color =
                    JournalViolet
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
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

                    Text(
                        text =
                            if (
                                calibration.calibrationReady
                            ) {
                                "CALIBRATION READY"
                            } else {
                                "COLLECTING EVIDENCE"
                            },
                        color =
                            if (
                                calibration.calibrationReady
                            ) {
                                JournalGreen
                            } else {
                                JournalGold
                            },
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            "${calibration.linkedOutcomes} linked outcomes",
                        color =
                            JournalSecondary,
                        fontSize =
                            10.sp
                    )
                }

                Column {

                    Text(
                        text =
                            "${calibration.calibrationConfidencePercent}%",
                        color =
                            accuracyColor(
                                calibration.calibrationConfidencePercent
                                    .toDouble()
                            ),
                        fontSize =
                            22.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            "confidence",
                        color =
                            JournalSecondary,
                        fontSize =
                            9.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Text(
                text =
                    calibration.summary,
                color =
                    JournalText,
                fontSize =
                    12.sp,
                lineHeight =
                    18.sp
            )

            calibration.caution
                ?.let {
                        caution ->

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    Text(
                        text =
                            caution,
                        color =
                            JournalGold,
                        fontSize =
                            10.sp,
                        lineHeight =
                            15.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )

            JournalLabel(
                text =
                    "SCORE BUCKETS",
                color =
                    JournalCyan
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            calibration.scoreBuckets
                .forEach {
                        bucket ->

                    ScoreBucketRow(
                        bucket =
                            bucket
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )
                }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            JournalLabel(
                text =
                    "RECOMMENDATIONS",
                color =
                    JournalGreen
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            calibration.recommendations
                .forEachIndexed {
                        index,
                        recommendation ->

                    CalibrationRecommendationCard(
                        number =
                            index + 1,
                        recommendation =
                            recommendation
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )
                }

            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )

            Text(
                text =
                    "TraDNA is recommendation-only at this stage. These observations do not automatically change Agent thresholds or weights.",
                color =
                    JournalSecondary,
                fontSize =
                    10.sp,
                lineHeight =
                    15.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    14.dp
                )
        )

        Button(
            onClick = {

                AgentPredictionOutcomeEngine
                    .linkOutcomes(
                        context =
                            context,
                        trades =
                            trades
                    )

                refreshVersion++
            },
            enabled =
                predictions.isNotEmpty() &&
                        trades.isNotEmpty(),
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    14.dp
                ),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        JournalCyan,
                    contentColor =
                        JournalBackground
                )
        ) {

            Text(
                text =
                    "LINK NEW TRADE OUTCOMES",
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        if (
            predictions.isEmpty()
        ) {

            JournalCard {

                JournalLabel(
                    text =
                        "NO SAVED PREDICTIONS",
                    color =
                        JournalGold
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Text(
                    text =
                        "Open the TraDNA Agent, run a personalized analysis, and save the recommendation before the trade occurs.",
                    color =
                        JournalSecondary,
                    fontSize =
                        13.sp,
                    lineHeight =
                        19.sp
                )
            }

        } else {

            JournalLabel(
                text =
                    "PREDICTION HISTORY",
                color =
                    JournalCyan
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            predictions.forEach {
                    prediction ->

                PredictionCard(
                    prediction =
                        prediction
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp
                        )
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    24.dp
                )
        )
    }
}

@Composable
private fun ScoreBucketRow(
    bucket: ScoreBucketCalibration
) {

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
                    JournalSurface2
                )
                .border(
                    width =
                        1.dp,
                    color =
                        JournalBorder,
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
                    "SCORE ${bucket.label}",
                color =
                    JournalCyan,
                fontSize =
                    11.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            Text(
                text =
                    "${bucket.linkedOutcomes}/${bucket.predictions} linked",
                color =
                    JournalSecondary,
                fontSize =
                    10.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    5.dp
                )
        )

        JournalValueRow(
            label =
                "Profitable rate",
            value =
                bucket.profitableRatePercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                accuracyColor(
                    bucket.profitableRatePercent
                )
        )

        JournalValueRow(
            label =
                "Average return",
            value =
                bucket.averageReturnPercent
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                signedColor(
                    bucket.averageReturnPercent
                )
        )

        JournalValueRow(
            label =
                "Directional accuracy",
            value =
                bucket.directionalAccuracyPercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                accuracyColor(
                    bucket.directionalAccuracyPercent
                )
        )
    }
}

@Composable
private fun CalibrationRecommendationCard(
    number: Int,
    recommendation: CalibrationRecommendation
) {

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
                    JournalSurface2
                )
                .border(
                    width =
                        1.dp,
                    color =
                        JournalGreen.copy(
                            alpha =
                                0.25f
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
                    "$number. ${recommendation.title}",
                color =
                    JournalText,
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            Text(
                text =
                    "${recommendation.confidencePercent}%",
                color =
                    accuracyColor(
                        recommendation.confidencePercent
                            .toDouble()
                    ),
                fontSize =
                    10.sp,
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
                recommendation.category,
            color =
                JournalViolet,
            fontSize =
                9.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(
                    6.dp
                )
        )

        JournalValueRow(
            label =
                "Current",
            value =
                recommendation.currentValue
        )

        JournalValueRow(
            label =
                "Recommended",
            value =
                recommendation.recommendedValue,
            valueColor =
                JournalGreen
        )

        Spacer(
            modifier =
                Modifier.height(
                    4.dp
                )
        )

        Text(
            text =
                recommendation.reason,
            color =
                JournalSecondary,
            fontSize =
                10.sp,
            lineHeight =
                15.sp
        )
    }
}

@Composable
private fun DecisionPerformanceRow(
    item: AgentDecisionPerformance
) {

    val label =
        item.decision
            .replace(
                "_",
                " "
            )

    val accent =
        when (
            item.decision
        ) {

            AgentTradeDecision.HIGH_CONVICTION.name ->
                JournalGreen

            AgentTradeDecision.FAVORABLE.name ->
                JournalCyan

            AgentTradeDecision.WATCH.name ->
                JournalGold

            AgentTradeDecision.WAIT.name ->
                JournalViolet

            else ->
                JournalRed
        }

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
                    JournalSurface2
                )
                .border(
                    width =
                        1.dp,
                    color =
                        accent.copy(
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
                    label,
                color =
                    accent,
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            Text(
                text =
                    "${item.totalPredictions} saved",
                color =
                    JournalSecondary,
                fontSize =
                    10.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    5.dp
                )
        )

        JournalValueRow(
            label =
                "Linked outcomes",
            value =
                item.linkedOutcomes
                    .toString()
        )

        JournalValueRow(
            label =
                "Directional accuracy",
            value =
                item.directionalAccuracyPercent
                    ?.let {
                        formatPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                accuracyColor(
                    item.directionalAccuracyPercent
                )
        )

        JournalValueRow(
            label =
                "Average actual return",
            value =
                item.averageActualReturnPercent
                    ?.let {
                        formatSignedPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                signedColor(
                    item.averageActualReturnPercent
                )
        )

        JournalValueRow(
            label =
                "Total linked P&L",
            value =
                item.totalActualRealizedPnl
                    ?.let {
                        formatMoneySigned(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                signedColor(
                    item.totalActualRealizedPnl
                )
        )
    }
}

@Composable
private fun PredictionCard(
    prediction: AgentPredictionRecord
) {

    val decisionColor =
        when (
            prediction.decision
        ) {

            AgentTradeDecision.HIGH_CONVICTION.name ->
                JournalGreen

            AgentTradeDecision.FAVORABLE.name ->
                JournalCyan

            AgentTradeDecision.WATCH.name ->
                JournalGold

            AgentTradeDecision.WAIT.name ->
                JournalViolet

            else ->
                JournalRed
        }

    JournalCard {

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
                        prediction.symbol,
                    color =
                        JournalText,
                    fontSize =
                        19.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        formatDateTime(
                            prediction.createdAtEpochMillis
                        ),
                    color =
                        JournalSecondary,
                    fontSize =
                        10.sp
                )
            }

            Column {

                Text(
                    text =
                        prediction.decision
                            .replace(
                                "_",
                                " "
                            ),
                    color =
                        decisionColor,
                    fontSize =
                        11.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        "${prediction.overallScore}/100",
                    color =
                        decisionColor,
                    fontSize =
                        18.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        JournalValueRow(
            label =
                "Market price at prediction",
            value =
                formatMoney(
                    prediction.marketPrice
                )
        )

        JournalValueRow(
            label =
                "Agent confidence",
            value =
                "${prediction.confidencePercent}%"
        )

        JournalValueRow(
            label =
                "Historical matches",
            value =
                prediction.matchedTradeCount
                    .toString()
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        if (
            prediction.outcomeLinked
        ) {

            JournalLabel(
                text =
                    "OUTCOME LINKED",
                color =
                    JournalGreen
            )

            Spacer(
                modifier =
                    Modifier.height(
                        7.dp
                    )
            )

            JournalValueRow(
                label =
                    "Actual entry",
                value =
                    prediction.actualEntryPrice
                        ?.let {
                            formatMoney(
                                it
                            )
                        }
                        ?: "—"
            )

            JournalValueRow(
                label =
                    "Actual exit",
                value =
                    prediction.actualExitPrice
                        ?.let {
                            formatMoney(
                                it
                            )
                        }
                        ?: "—"
            )

            JournalValueRow(
                label =
                    "Actual return",
                value =
                    prediction.actualReturnPercent
                        ?.let {
                            formatSignedPercent(
                                it
                            )
                        }
                        ?: "—",
                valueColor =
                    signedColor(
                        prediction.actualReturnPercent
                    )
            )

            JournalValueRow(
                label =
                    "Actual realized P&L",
                value =
                    prediction.actualRealizedPnl
                        ?.let {
                            formatMoneySigned(
                                it
                            )
                        }
                        ?: "—",
                valueColor =
                    signedColor(
                        prediction.actualRealizedPnl
                    )
            )

            val correct =
                prediction.predictionDirectionCorrect

            JournalValueRow(
                label =
                    "Directional call",
                value =
                    when (
                        correct
                    ) {

                        true ->
                            "CORRECT"

                        false ->
                            "INCORRECT"

                        null ->
                            "NOT SCORED"
                    },
                valueColor =
                    when (
                        correct
                    ) {

                        true ->
                            JournalGreen

                        false ->
                            JournalRed

                        null ->
                            JournalSecondary
                    }
            )

        } else {

            JournalLabel(
                text =
                    "AWAITING OUTCOME",
                color =
                    JournalGold
            )

            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )

            Text(
                text =
                    "Import the later Robinhood trade report, then run Link New Trade Outcomes.",
                color =
                    JournalSecondary,
                fontSize =
                    11.sp,
                lineHeight =
                    16.sp
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
                "Preferred entry: ${prediction.preferredEntryMethod}",
            color =
                JournalSecondary,
            fontSize =
                10.sp,
            lineHeight =
                15.sp
        )

        Text(
            text =
                "Preferred management: ${prediction.preferredExitMethod}",
            color =
                JournalSecondary,
            fontSize =
                10.sp,
            lineHeight =
                15.sp
        )
    }
}

@Composable
private fun JournalValueRow(
    label: String,
    value: String,
    valueColor: Color =
        JournalText
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
                JournalSecondary,
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
                FontWeight.SemiBold
        )
    }
}

@Composable
private fun JournalLabel(
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
private fun JournalCard(
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
                        18.dp
                    )
                )
                .background(
                    JournalSurface
                )
                .border(
                    width =
                        1.dp,
                    color =
                        JournalBorder,
                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                )
                .padding(
                    16.dp
                ),
        content =
            content
    )
}

private fun formatDateTime(
    epochMillis: Long
): String {

    return SimpleDateFormat(
        "MMM d, yyyy • h:mm a",
        Locale.US
    )
        .format(
            Date(
                epochMillis
            )
        )
}

private fun formatMoney(
    value: Double
): String {

    return String.format(
        Locale.US,
        "$%,.2f",
        value
    )
}

private fun formatMoneySigned(
    value: Double
): String {

    return String.format(
        Locale.US,
        "$%+,.2f",
        value
    )
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

private fun signedColor(
    value: Double?
): Color {

    return when {

        value == null ->
            JournalSecondary

        value > 0.0 ->
            JournalGreen

        value < 0.0 ->
            JournalRed

        else ->
            JournalText
    }
}

private fun accuracyColor(
    value: Double?
): Color {

    return when {

        value == null ->
            JournalSecondary

        value >= 70.0 ->
            JournalGreen

        value >= 55.0 ->
            JournalCyan

        value >= 45.0 ->
            JournalGold

        else ->
            JournalRed
    }
}
