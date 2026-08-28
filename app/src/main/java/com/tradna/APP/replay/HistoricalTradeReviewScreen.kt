package com.tradna.APP.replay

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradna.APP.data.TradeEpisode
import com.tradna.APP.market.AlpacaMarketData
import com.tradna.APP.market.Candle
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ReviewBackground =
    Color(0xFF07090D)

private val ReviewSurface =
    Color(0xFF0E1219)

private val ReviewSurface2 =
    Color(0xFF141A23)

private val ReviewBorder =
    Color(0xFF222B38)

private val ReviewText =
    Color(0xFFF4F7FB)

private val ReviewSecondary =
    Color(0xFF8D98A8)

private val ReviewCyan =
    Color(0xFF72E7FF)

private val ReviewGreen =
    Color(0xFF39D6A0)

private val ReviewRed =
    Color(0xFFFF657A)

private val ReviewGold =
    Color(0xFFFFC857)

private val ReviewViolet =
    Color(0xFF9B7CFF)

@Composable
fun HistoricalTradeReviewScreen(
    trade: TradeEpisode,
    onBack: () -> Unit
) {

    var candles by remember(
        trade.id
    ) {
        mutableStateOf<List<Candle>>(
            emptyList()
        )
    }

    var analysis by remember(
        trade.id
    ) {
        mutableStateOf<HistoricalTradeAnalysis?>(
            null
        )
    }

    var counterfactualReport by remember(
        trade.id
    ) {
        mutableStateOf<CounterfactualReport?>(
            null
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
            "15Min"
        )
    }

    LaunchedEffect(
        trade.id,
        timeframe
    ) {

        loading = true
        error = null
        candles =
            emptyList()
        analysis =
            null
        counterfactualReport =
            null

        try {

            val range =
                historicalReviewDateRange(
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

            if (
                loaded.isEmpty()
            ) {

                error =
                    "No historical market data was returned for this trade."

            } else {

                val historicalAnalysis =
                    HistoricalTradeAnalysisEngine
                        .analyze(
                            trade =
                                trade,
                            candles =
                                loaded
                        )

                analysis =
                    historicalAnalysis

                counterfactualReport =
                    CounterfactualEngine
                        .analyze(
                            trade =
                                trade,
                            candles =
                                loaded,
                            historicalAnalysis =
                                historicalAnalysis
                        )
            }

        } catch (
            e: Exception
        ) {

            error =
                e.message
                    ?: "Unable to generate the historical trade review."

        } finally {

            loading =
                false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                ReviewBackground
            )
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                bottom =
                    32.dp
            )
    ) {

        ReviewHeader(
            trade =
                trade,
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

            ReviewIntroCard(
                trade =
                    trade
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            ReviewTimeframeSelector(
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
                        14.dp
                    )
            )

            when {

                loading -> {

                    ReviewLoading(
                        symbol =
                            trade.symbol
                    )
                }

                error != null -> {

                    ReviewCard {

                        Text(
                            text =
                                "ANALYSIS ERROR",
                            color =
                                ReviewRed,
                            fontSize =
                                11.sp,
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
                                error
                                    ?: "Unknown error",
                            color =
                                ReviewText,
                            fontSize =
                                14.sp
                        )
                    }
                }

                analysis != null -> {

                    val result =
                        analysis
                            ?: return@Column

                    ActualTradeCard(
                        analysis =
                            result
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    TechnicalReviewCard(
                        analysis =
                            result
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    EfficiencyCard(
                        analysis =
                            result
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    StrengthsCard(
                        strengths =
                            result.strengths
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    WeaknessesCard(
                        weaknesses =
                            result.weaknesses
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    RecommendationsCard(
                        recommendations =
                            result.recommendations
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                14.dp
                            )
                    )

                    LessonCard(
                        analysis =
                            result
                    )

                    if (
                        counterfactualReport != null
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    14.dp
                                )
                        )

                        CounterfactualSummaryCard(
                            report =
                                counterfactualReport!!
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    14.dp
                                )
                        )

                        CounterfactualScenariosCard(
                            report =
                                counterfactualReport!!
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewHeader(
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
                ReviewCyan,
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
                    "${trade.symbol} REVIEW",
                color =
                    ReviewText,
                fontSize =
                    23.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text =
                    "Historical Trade Analysis",
                color =
                    ReviewSecondary,
                fontSize =
                    12.sp
            )
        }
    }
}

@Composable
private fun ReviewIntroCard(
    trade: TradeEpisode
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "TRADNA TEACHER",
            color =
                ReviewViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    9.dp
                )
        )

        Text(
            text =
                "Learn from the trade you already made.",
            color =
                ReviewText,
            fontSize =
                22.sp,
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
                "TraDNA reconstructs your historical trade, studies the technical environment, measures entry and exit quality, and compares your actual result with repeatable alternatives.",
            color =
                ReviewSecondary,
            fontSize =
                14.sp,
            lineHeight =
                21.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        ReviewValueRow(
            label =
                "Opened",
            value =
                trade.openDate
        )

        ReviewValueRow(
            label =
                "Closed",
            value =
                trade.closeDate
                    ?: "Open position"
        )
    }
}

@Composable
private fun ActualTradeCard(
    analysis:
    HistoricalTradeAnalysis
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "WHAT YOU ACTUALLY DID",
            color =
                ReviewCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        ReviewValueRow(
            label =
                "Average entry",
            value =
                reviewPrice(
                    analysis
                        .actualEntryPrice
                )
        )

        ReviewValueRow(
            label =
                "Average exit",
            value =
                analysis
                    .actualExitPrice
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "OPEN"
        )

        ReviewValueRow(
            label =
                "Realized P&L",
            value =
                reviewMoney(
                    analysis
                        .actualRealizedPnl
                ),
            valueColor =
                when {

                    analysis
                        .actualRealizedPnl >
                            0.0 ->
                        ReviewGreen

                    analysis
                        .actualRealizedPnl <
                            0.0 ->
                        ReviewRed

                    else ->
                        ReviewText
                }
        )

        ReviewValueRow(
            label =
                "Actual return",
            value =
                analysis
                    .actualReturnPercent
                    ?.let {
                        reviewPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                percentColor(
                    analysis
                        .actualReturnPercent
                )
        )

        ReviewValueRow(
            label =
                "Maximum favorable move",
            value =
                analysis
                    .maximumFavorableExcursionPercent
                    ?.let {
                        reviewPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                ReviewGreen
        )

        ReviewValueRow(
            label =
                "Maximum adverse move",
            value =
                analysis
                    .maximumAdverseExcursionPercent
                    ?.let {
                        reviewPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                ReviewRed
        )
    }
}

@Composable
private fun TechnicalReviewCard(
    analysis:
    HistoricalTradeAnalysis
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "WHAT THE MARKET SHOWED",
            color =
                ReviewCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        ReviewSmallLabel(
            "AT ENTRY"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        ReviewValueRow(
            label =
                "Technical score",
            value =
                analysis
                    .entryTechnicalScore
                    ?.let {
                        "$it / 100"
                    }
                    ?: "—",
            valueColor =
                technicalScoreColor(
                    analysis
                        .entryTechnicalScore
                )
        )

        ReviewValueRow(
            label =
                "VWAP",
            value =
                analysis
                    .entryVwap
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "EMA 9",
            value =
                analysis
                    .entryEma9
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "EMA 20",
            value =
                analysis
                    .entryEma20
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "Relative volume",
            value =
                analysis
                    .entryRelativeVolume
                    ?.let {
                        String.format(
                            Locale.US,
                            "%.2fx",
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "VWAP distance",
            value =
                analysis
                    .entryDistanceFromVwapPercent
                    ?.let {
                        reviewPercent(
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

        ReviewSmallLabel(
            "SIGNALS AT ENTRY"
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        if (
            analysis
                .entrySignals
                .isEmpty()
        ) {

            Text(
                text =
                    "No predefined technical setup was strongly detected at the reconstructed entry.",
                color =
                    ReviewSecondary,
                fontSize =
                    13.sp,
                lineHeight =
                    19.sp
            )

        } else {

            analysis
                .entrySignals
                .forEach {
                        signal ->

                    ReviewBullet(
                        text =
                            signal,
                        color =
                            ReviewCyan
                    )
                }
        }

        if (
            analysis
                .actualExitPrice !=
            null
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )

            ReviewSmallLabel(
                "AT EXIT"
            )

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            ReviewValueRow(
                label =
                    "Technical score",
                value =
                    analysis
                        .exitTechnicalScore
                        ?.let {
                            "$it / 100"
                        }
                        ?: "—",
                valueColor =
                    technicalScoreColor(
                        analysis
                            .exitTechnicalScore
                    )
            )

            ReviewValueRow(
                label =
                    "VWAP",
                value =
                    analysis
                        .exitVwap
                        ?.let {
                            reviewPrice(
                                it
                            )
                        }
                        ?: "—"
            )

            ReviewValueRow(
                label =
                    "EMA 9",
                value =
                    analysis
                        .exitEma9
                        ?.let {
                            reviewPrice(
                                it
                            )
                        }
                        ?: "—"
            )

            ReviewValueRow(
                label =
                    "EMA 20",
                value =
                    analysis
                        .exitEma20
                        ?.let {
                            reviewPrice(
                                it
                            )
                        }
                        ?: "—"
            )

            if (
                analysis
                    .exitSignals
                    .isNotEmpty()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                analysis
                    .exitSignals
                    .forEach {
                            signal ->

                        ReviewBullet(
                            text =
                                signal,
                            color =
                                ReviewViolet
                        )
                    }
            }
        }
    }
}

@Composable
private fun EfficiencyCard(
    analysis:
    HistoricalTradeAnalysis
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "TRADE EFFICIENCY",
            color =
                ReviewGold
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
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

            EfficiencyMetric(
                label =
                    "ENTRY",
                score =
                    analysis
                        .entryEfficiencyScore,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            EfficiencyMetric(
                label =
                    "EXIT",
                score =
                    analysis
                        .exitEfficiencyScore,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )

            EfficiencyMetric(
                label =
                    "TOTAL",
                score =
                    analysis
                        .tradeEfficiencyScore,
                modifier =
                    Modifier.weight(
                        1f
                    )
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        ReviewValueRow(
            label =
                "Highest after entry",
            value =
                analysis
                    .highestPriceAfterEntry
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "Lowest after entry",
            value =
                analysis
                    .lowestPriceAfterEntry
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "Highest after exit",
            value =
                analysis
                    .highestPriceAfterExit
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "Additional upside after exit",
            value =
                analysis
                    .missedUpsidePercent
                    ?.let {
                        reviewPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                ReviewGold
        )

        ReviewValueRow(
            label =
                "Additional $ / share",
            value =
                analysis
                    .missedUpsidePerShare
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                ReviewGold
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Text(
            text =
                "Efficiency uses limited local market windows rather than assuming you could perfectly buy the low and sell the high.",
            color =
                ReviewSecondary,
            fontSize =
                11.sp,
            lineHeight =
                17.sp
        )
    }
}

@Composable
private fun EfficiencyMetric(
    label: String,
    score: Int?,
    modifier:
    Modifier
) {

    Column(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(
                        12.dp
                    )
                )
                .background(
                    ReviewSurface2
                )
                .border(
                    width =
                        1.dp,
                    color =
                        ReviewBorder,
                    shape =
                        RoundedCornerShape(
                            12.dp
                        )
                )
                .padding(
                    12.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text =
                label,
            color =
                ReviewSecondary,
            fontSize =
                8.sp,
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
                score
                    ?.toString()
                    ?: "—",
            color =
                technicalScoreColor(
                    score
                ),
            fontSize =
                22.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun StrengthsCard(
    strengths:
    List<String>
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "WHAT YOU DID WELL",
            color =
                ReviewGreen
        )

        Spacer(
            modifier =
                Modifier.height(
                    13.dp
                )
        )

        strengths.forEach {
                item ->

            ReviewBullet(
                text =
                    item,
                color =
                    ReviewGreen
            )
        }
    }
}

@Composable
private fun WeaknessesCard(
    weaknesses:
    List<String>
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "WHAT COULD IMPROVE",
            color =
                ReviewGold
        )

        Spacer(
            modifier =
                Modifier.height(
                    13.dp
                )
        )

        weaknesses.forEach {
                item ->

            ReviewBullet(
                text =
                    item,
                color =
                    ReviewGold
            )
        }
    }
}

@Composable
private fun RecommendationsCard(
    recommendations:
    List<String>
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "TRADNA RECOMMENDS",
            color =
                ReviewCyan
        )

        Spacer(
            modifier =
                Modifier.height(
                    13.dp
                )
        )

        recommendations
            .forEachIndexed {
                    index,
                    recommendation ->

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical =
                                    6.dp
                            )
                ) {

                    Box(
                        modifier =
                            Modifier
                                .clip(
                                    RoundedCornerShape(
                                        7.dp
                                    )
                                )
                                .background(
                                    ReviewCyan
                                        .copy(
                                            alpha =
                                                0.15f
                                        )
                                )
                                .padding(
                                    horizontal =
                                        8.dp,
                                    vertical =
                                        4.dp
                                )
                    ) {

                        Text(
                            text =
                                "${index + 1}",
                            color =
                                ReviewCyan,
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
                            recommendation,
                        color =
                            ReviewText,
                        fontSize =
                            13.sp,
                        lineHeight =
                            19.sp,
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
private fun LessonCard(
    analysis:
    HistoricalTradeAnalysis
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "YOUR LESSON",
            color =
                ReviewViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                analysis
                    .lessonTitle,
            color =
                ReviewText,
            fontSize =
                22.sp,
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
                analysis
                    .lessonSummary,
            color =
                ReviewSecondary,
            fontSize =
                14.sp,
            lineHeight =
                21.sp
        )
    }
}

@Composable
private fun CounterfactualSummaryCard(
    report:
    CounterfactualReport
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "ALTERNATIVE ANALYSIS",
            color =
                ReviewGold
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "What if you traded it differently?",
            color =
                ReviewText,
            fontSize =
                22.sp,
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
                report.summary,
            color =
                ReviewSecondary,
            fontSize =
                14.sp,
            lineHeight =
                21.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        ReviewValueRow(
            label =
                "Actual return",
            value =
                report
                    .actualReturnPercent
                    ?.let {
                        reviewPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                percentColor(
                    report
                        .actualReturnPercent
                )
        )

        ReviewValueRow(
            label =
                "Actual realized P&L",
            value =
                reviewMoney(
                    report
                        .actualRealizedPnl
                ),
            valueColor =
                if (
                    report
                        .actualRealizedPnl >=
                    0.0
                ) {
                    ReviewGreen
                } else {
                    ReviewRed
                }
        )

        ReviewValueRow(
            label =
                "Reference stop",
            value =
                report
                    .referenceStopPrice
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        ReviewValueRow(
            label =
                "Reference risk / share",
            value =
                report
                    .referenceRiskPerShare
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        if (
            report.bestScenario !=
            null
        ) {

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        )
                        .background(
                            ReviewGreen
                                .copy(
                                    alpha =
                                        0.10f
                                )
                        )
                        .border(
                            width =
                                1.dp,
                            color =
                                ReviewGreen
                                    .copy(
                                        alpha =
                                            0.45f
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

                Column {

                    ReviewSmallLabel(
                        "BEST MODELED RESULT"
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Text(
                        text =
                            report
                                .bestScenario
                                ?.title
                                ?: "—",
                        color =
                            ReviewGreen,
                        fontSize =
                            18.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    ReviewValueRow(
                        label =
                            "Modeled return",
                        value =
                            report
                                .bestScenario
                                ?.returnPercent
                                ?.let {
                                    reviewPercent(
                                        it
                                    )
                                }
                                ?: "—",
                        valueColor =
                            ReviewGreen
                    )

                    ReviewValueRow(
                        label =
                            "Estimated P&L",
                        value =
                            report
                                .bestScenario
                                ?.estimatedPnl
                                ?.let {
                                    reviewMoney(
                                        it
                                    )
                                }
                                ?: "—",
                        valueColor =
                            ReviewGreen
                    )

                    ReviewValueRow(
                        label =
                            "Return improvement",
                        value =
                            report
                                .bestImprovementPercent
                                ?.let {
                                    reviewPercent(
                                        it
                                    )
                                }
                                ?: "—",
                        valueColor =
                            improvementColor(
                                report
                                    .bestImprovementPercent
                            )
                    )

                    ReviewValueRow(
                        label =
                            "P&L difference",
                        value =
                            report
                                .bestImprovementDollars
                                ?.let {
                                    reviewMoney(
                                        it
                                    )
                                }
                                ?: "—",
                        valueColor =
                            improvementColor(
                                report
                                    .bestImprovementDollars
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterfactualScenariosCard(
    report:
    CounterfactualReport
) {

    ReviewCard {

        ReviewSectionLabel(
            text =
                "SCENARIO COMPARISON",
            color =
                ReviewViolet
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "Repeatable rules tested against the same historical market.",
            color =
                ReviewText,
            fontSize =
                18.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        report.scenarios
            .forEach {
                    scenario ->

                CounterfactualScenarioItem(
                    scenario =
                        scenario
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            10.dp
                        )
                )
            }
    }
}

@Composable
private fun CounterfactualScenarioItem(
    scenario:
    CounterfactualScenario
) {

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
                    ReviewSurface2
                )
                .border(
                    width =
                        1.dp,
                    color =
                        scenarioBorderColor(
                            scenario.outcome
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

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                Text(
                    text =
                        scenario.title,
                    color =
                        ReviewText,
                    fontSize =
                        16.sp,
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
                        scenarioOutcomeLabel(
                            scenario.outcome
                        ),
                    color =
                        scenarioOutcomeColor(
                            scenario.outcome
                        ),
                    fontSize =
                        10.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Text(
                text =
                    scenario
                        .returnPercent
                        ?.let {
                            reviewPercent(
                                it
                            )
                        }
                        ?: "—",
                color =
                    percentColor(
                        scenario
                            .returnPercent
                    ),
                fontSize =
                    18.sp,
                fontWeight =
                    FontWeight.Bold
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
                scenario.description,
            color =
                ReviewSecondary,
            fontSize =
                12.sp,
            lineHeight =
                18.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        ReviewValueRow(
            label =
                "Entry",
            value =
                scenario
                    .entryPrice
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "No entry"
        )

        ReviewValueRow(
            label =
                "Exit",
            value =
                scenario
                    .exitPrice
                    ?.let {
                        reviewPrice(
                            it
                        )
                    }
                    ?: "—"
        )

        if (
            scenario.stopPrice !=
            null
        ) {

            ReviewValueRow(
                label =
                    "Stop",
                value =
                    reviewPrice(
                        scenario
                            .stopPrice
                    )
            )
        }

        if (
            scenario.targetPrice !=
            null
        ) {

            ReviewValueRow(
                label =
                    "Target",
                value =
                    reviewPrice(
                        scenario
                            .targetPrice
                    )
            )
        }

        ReviewValueRow(
            label =
                "Estimated P&L",
            value =
                scenario
                    .estimatedPnl
                    ?.let {
                        reviewMoney(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                scenario
                    .estimatedPnl
                    ?.let {
                        if (
                            it >
                            0.0
                        ) {
                            ReviewGreen
                        } else if (
                            it <
                            0.0
                        ) {
                            ReviewRed
                        } else {
                            ReviewText
                        }
                    }
                    ?: ReviewSecondary
        )

        ReviewValueRow(
            label =
                "Vs actual return",
            value =
                scenario
                    .improvementVsActualPercent
                    ?.let {
                        reviewPercent(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                improvementColor(
                    scenario
                        .improvementVsActualPercent
                )
        )

        ReviewValueRow(
            label =
                "Vs actual P&L",
            value =
                scenario
                    .improvementVsActualDollars
                    ?.let {
                        reviewMoney(
                            it
                        )
                    }
                    ?: "—",
            valueColor =
                improvementColor(
                    scenario
                        .improvementVsActualDollars
                )
        )

        if (
            scenario.realizedR !=
            null
        ) {

            ReviewValueRow(
                label =
                    "Result in R",
                value =
                    String.format(
                        Locale.US,
                        "%+.2fR",
                        scenario.realizedR
                    ),
                valueColor =
                    improvementColor(
                        scenario
                            .realizedR
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
                scenario.lesson,
            color =
                ReviewSecondary,
            fontSize =
                11.sp,
            lineHeight =
                17.sp
        )
    }
}

@Composable
private fun ReviewTimeframeSelector(
    selected:
    String,
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

        options.forEach {
                option ->

            val active =
                selected ==
                        option.first

            Box(
                modifier =
                    Modifier
                        .weight(
                            1f
                        )
                        .clip(
                            RoundedCornerShape(
                                11.dp
                            )
                        )
                        .background(
                            if (
                                active
                            ) {
                                ReviewCyan
                            } else {
                                ReviewSurface
                            }
                        )
                        .border(
                            width =
                                1.dp,
                            color =
                                if (
                                    active
                                ) {
                                    ReviewCyan
                                } else {
                                    ReviewBorder
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
                            ReviewBackground
                        } else {
                            ReviewSecondary
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
private fun ReviewLoading(
    symbol:
    String
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    320.dp
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
                    ReviewCyan
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
                    ReviewSecondary
            )
        }
    }
}

@Composable
private fun ReviewValueRow(
    label:
    String,
    value:
    String,
    valueColor:
    Color =
        ReviewText
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
                ReviewSecondary,
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
private fun ReviewBullet(
    text:
    String,
    color:
    Color
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
                9.sp
        )

        Spacer(
            modifier =
                Modifier.padding(
                    horizontal =
                        5.dp
                )
        )

        Text(
            text =
                text,
            color =
                ReviewText,
            fontSize =
                13.sp,
            lineHeight =
                19.sp,
            modifier =
                Modifier.weight(
                    1f
                )
        )
    }
}

@Composable
private fun ReviewSmallLabel(
    text:
    String
) {

    Text(
        text =
            text,
        color =
            ReviewSecondary,
        fontSize =
            9.sp,
        fontWeight =
            FontWeight.Bold,
        letterSpacing =
            1.1.sp
    )
}

@Composable
private fun ReviewSectionLabel(
    text:
    String,
    color:
    Color
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
private fun ReviewCard(
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
                    ReviewSurface
                )
                .border(
                    width =
                        1.dp,
                    color =
                        ReviewBorder,
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

private fun historicalReviewDateRange(
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

private fun reviewPrice(
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

private fun reviewMoney(
    value:
    Double
): String {

    return String.format(
        Locale.US,
        "\$%,.2f",
        value
    )
}

private fun reviewPercent(
    value:
    Double
): String {

    return String.format(
        Locale.US,
        "%+.2f%%",
        value
    )
}

private fun percentColor(
    value:
    Double?
): Color {

    return when {

        value == null ->
            ReviewSecondary

        value > 0.0 ->
            ReviewGreen

        value < 0.0 ->
            ReviewRed

        else ->
            ReviewText
    }
}

private fun improvementColor(
    value:
    Double?
): Color {

    return when {

        value == null ->
            ReviewSecondary

        value > 0.0 ->
            ReviewGreen

        value < 0.0 ->
            ReviewRed

        else ->
            ReviewText
    }
}

private fun technicalScoreColor(
    score:
    Int?
): Color {

    return when {

        score == null ->
            ReviewSecondary

        score >= 70 ->
            ReviewGreen

        score <= 40 ->
            ReviewRed

        else ->
            ReviewGold
    }
}

private fun scenarioOutcomeLabel(
    outcome:
    CounterfactualOutcome
): String {

    return when (
        outcome
    ) {

        CounterfactualOutcome.PROFIT ->
            "PROFIT"

        CounterfactualOutcome.LOSS ->
            "LOSS"

        CounterfactualOutcome.FLAT ->
            "FLAT"

        CounterfactualOutcome.NO_ENTRY ->
            "NO ENTRY"

        CounterfactualOutcome.STILL_OPEN ->
            "OPEN AT END"

        CounterfactualOutcome.AMBIGUOUS ->
            "AMBIGUOUS"
    }
}

private fun scenarioOutcomeColor(
    outcome:
    CounterfactualOutcome
): Color {

    return when (
        outcome
    ) {

        CounterfactualOutcome.PROFIT ->
            ReviewGreen

        CounterfactualOutcome.LOSS ->
            ReviewRed

        CounterfactualOutcome.FLAT ->
            ReviewSecondary

        CounterfactualOutcome.NO_ENTRY ->
            ReviewGold

        CounterfactualOutcome.STILL_OPEN ->
            ReviewCyan

        CounterfactualOutcome.AMBIGUOUS ->
            ReviewViolet
    }
}

private fun scenarioBorderColor(
    outcome:
    CounterfactualOutcome
): Color {

    return scenarioOutcomeColor(
        outcome
    )
        .copy(
            alpha =
                0.35f
        )
}