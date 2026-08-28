package com.tradna.APP.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.market.Candle
import java.util.Locale

private val GradeSurface = Color(0xFF0E1219)
private val GradeSurface2 = Color(0xFF141A23)
private val GradeBorder = Color(0xFF222B38)

private val GradeText = Color(0xFFF4F7FB)
private val GradeSecondary = Color(0xFF8D98A8)

private val GradeCyan = Color(0xFF72E7FF)
private val GradeGreen = Color(0xFF39D6A0)
private val GradeRed = Color(0xFFFF657A)
private val GradeViolet = Color(0xFF9B7CFF)
private val GradeGold = Color(0xFFFFC857)

@Composable
fun ReplayGradingPanel(
    decisions: List<ReplayDecision>,
    allCandles: List<Candle>
) {

    val buyPlans =
        decisions.filter {
            it.choice == ReplayChoice.BUY &&
                    it.plannedEntry != null &&
                    it.plannedStop != null &&
                    it.plannedTarget != null
        }

    val grades =
        buyPlans.map { decision ->

            decision to
                    ReplayScoringEngine.grade(
                        decision = decision,
                        allCandles = allCandles
                    )
        }

    GradeCard {

        Text(
            text = "REPLAY PERFORMANCE",
            color = GradeCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text =
                if (grades.isEmpty()) {
                    "No graded trade plans yet."
                } else {
                    "${grades.size} trade plans graded"
                },
            color = GradeText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        Text(
            text =
                "TraDNA evaluates what happened after each saved BUY plan without changing the decision you made during blind replay.",
            color = GradeSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        if (grades.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            ReplayGradeSummary(
                grades = grades.map {
                    it.second
                }
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            HorizontalDivider(
                color = GradeBorder
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            grades
                .takeLast(10)
                .reversed()
                .forEach { pair ->

                    ReplayGradeItem(
                        decision = pair.first,
                        grade = pair.second
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )
                }
        }
    }
}

@Composable
private fun ReplayGradeSummary(
    grades: List<ReplayGrade>
) {

    val targetHits =
        grades.count {
            it.outcome ==
                    ReplayOutcome.TARGET_HIT
        }

    val stopHits =
        grades.count {
            it.outcome ==
                    ReplayOutcome.STOP_HIT
        }

    val notTriggered =
        grades.count {
            it.outcome ==
                    ReplayOutcome.ENTRY_NOT_TRIGGERED
        }

    val ambiguous =
        grades.count {
            it.outcome ==
                    ReplayOutcome.BOTH_HIT_SAME_CANDLE
        }

    val open =
        grades.count {
            it.outcome ==
                    ReplayOutcome.OPEN_AT_END
        }

    val realizedRs =
        grades.mapNotNull {
            it.realizedRMultiple
        }

    val averageR =
        if (realizedRs.isNotEmpty()) {
            realizedRs.average()
        } else {
            null
        }

    val averageScore =
        if (grades.isNotEmpty()) {
            grades
                .map {
                    it.score
                }
                .average()
        } else {
            0.0
        }

    Column {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            SummaryMetric(
                label = "TARGET",
                value = targetHits.toString(),
                valueColor = GradeGreen,
                modifier = Modifier.weight(1f)
            )

            SummaryMetric(
                label = "STOP",
                value = stopHits.toString(),
                valueColor = GradeRed,
                modifier = Modifier.weight(1f)
            )

            SummaryMetric(
                label = "NO ENTRY",
                value = notTriggered.toString(),
                valueColor = GradeGold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            SummaryMetric(
                label = "AVG R",
                value =
                    averageR?.let {
                        formatR(it)
                    } ?: "—",
                valueColor =
                    rColor(averageR),
                modifier = Modifier.weight(1f)
            )

            SummaryMetric(
                label = "AVG SCORE",
                value =
                    String.format(
                        Locale.US,
                        "%.0f",
                        averageScore
                    ),
                valueColor =
                    scoreColor(
                        averageScore.toInt()
                    ),
                modifier = Modifier.weight(1f)
            )

            SummaryMetric(
                label = "AMBIG / OPEN",
                value =
                    "${ambiguous + open}",
                valueColor = GradeViolet,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(12.dp)
            )
            .background(GradeSurface2)
            .border(
                width = 1.dp,
                color = GradeBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(11.dp)
    ) {

        Text(
            text = label,
            color = GradeSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ReplayGradeItem(
    decision: ReplayDecision,
    grade: ReplayGrade
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(14.dp)
            )
            .background(GradeSurface2)
            .border(
                width = 1.dp,
                color = GradeBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = decision.setup,
                    color = GradeText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text =
                        "Confidence ${decision.confidence}/10",
                    color = GradeSecondary,
                    fontSize = 10.sp
                )
            }

            Column {

                Text(
                    text =
                        outcomeLabel(
                            grade.outcome
                        ),
                    color =
                        outcomeColor(
                            grade.outcome
                        ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "${grade.score}/100",
                    color =
                        scoreColor(
                            grade.score
                        ),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        GradeValueRow(
            label = "Entry",
            value =
                decision.plannedEntry
                    ?.let {
                        formatPrice(it)
                    }
                    ?: "—"
        )

        GradeValueRow(
            label = "Stop",
            value =
                decision.plannedStop
                    ?.let {
                        formatPrice(it)
                    }
                    ?: "—"
        )

        GradeValueRow(
            label = "Target",
            value =
                decision.plannedTarget
                    ?.let {
                        formatPrice(it)
                    }
                    ?: "—"
        )

        GradeValueRow(
            label = "Planned R:R",
            value =
                grade.plannedRiskReward
                    ?.let {
                        String.format(
                            Locale.US,
                            "1 : %.2f",
                            it
                        )
                    }
                    ?: "—"
        )

        GradeValueRow(
            label = "Result",
            value =
                grade.realizedRMultiple
                    ?.let {
                        formatR(it)
                    }
                    ?: "—",
            valueColor =
                rColor(
                    grade.realizedRMultiple
                )
        )

        GradeValueRow(
            label = "MFE",
            value =
                grade
                    .maxFavorableExcursionPercent
                    ?.let {
                        formatPercent(it)
                    }
                    ?: "—",
            valueColor = GradeGreen
        )

        GradeValueRow(
            label = "MAE",
            value =
                grade
                    .maxAdverseExcursionPercent
                    ?.let {
                        formatPercent(it)
                    }
                    ?: "—",
            valueColor = GradeRed
        )

        if (
            grade.candlesUntilEntry != null
        ) {

            GradeValueRow(
                label = "Candles to entry",
                value =
                    grade
                        .candlesUntilEntry
                        .toString()
            )
        }

        if (
            grade.candlesInTrade != null
        ) {

            GradeValueRow(
                label = "Candles in trade",
                value =
                    grade
                        .candlesInTrade
                        .toString()
            )
        }

        if (grade.notes.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            grade.notes.forEach { note ->

                Text(
                    text = "• $note",
                    color = GradeSecondary,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun GradeValueRow(
    label: String,
    value: String,
    valueColor: Color = GradeText
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 3.dp
            )
    ) {

        Text(
            text = label,
            color = GradeSecondary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GradeCard(
    content:
    @Composable
    ColumnScope.() -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(20.dp)
            )
            .background(GradeSurface)
            .border(
                width = 1.dp,
                color = GradeBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp),
        content = content
    )
}

private fun outcomeLabel(
    outcome: ReplayOutcome
): String {

    return when (outcome) {

        ReplayOutcome.TARGET_HIT ->
            "TARGET HIT"

        ReplayOutcome.STOP_HIT ->
            "STOP HIT"

        ReplayOutcome.BOTH_HIT_SAME_CANDLE ->
            "AMBIGUOUS"

        ReplayOutcome.ENTRY_NOT_TRIGGERED ->
            "NO ENTRY"

        ReplayOutcome.OPEN_AT_END ->
            "OPEN"

        ReplayOutcome.NO_TRADE_PLAN ->
            "NO PLAN"
    }
}

private fun outcomeColor(
    outcome: ReplayOutcome
): Color {

    return when (outcome) {

        ReplayOutcome.TARGET_HIT ->
            GradeGreen

        ReplayOutcome.STOP_HIT ->
            GradeRed

        ReplayOutcome.BOTH_HIT_SAME_CANDLE ->
            GradeViolet

        ReplayOutcome.ENTRY_NOT_TRIGGERED ->
            GradeGold

        ReplayOutcome.OPEN_AT_END ->
            GradeCyan

        ReplayOutcome.NO_TRADE_PLAN ->
            GradeSecondary
    }
}

private fun scoreColor(
    score: Int
): Color {

    return when {

        score >= 75 ->
            GradeGreen

        score >= 50 ->
            GradeGold

        else ->
            GradeRed
    }
}

private fun rColor(
    value: Double?
): Color {

    return when {

        value == null ->
            GradeSecondary

        value > 0.0 ->
            GradeGreen

        value < 0.0 ->
            GradeRed

        else ->
            GradeText
    }
}

private fun formatPrice(
    value: Double
): String {

    return if (value < 10.0) {

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

private fun formatR(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%+.2fR",
        value
    )
}

private fun formatPercent(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%+.2f%%",
        value
    )
}