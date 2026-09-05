package com.tradna.APP.lab

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val PaperBackground = Color(0xFF07090D)
private val PaperSurface = Color(0xFF10151D)
private val PaperText = Color(0xFFF4F7FB)
private val PaperSecondary = Color(0xFF8D98A8)
private val PaperCyan = Color(0xFF72E7FF)
private val PaperGreen = Color(0xFF39D6A0)

@Composable
fun PaperSandboxScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var account by remember { mutableStateOf(PaperSandboxStorage.load(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val predictions = AgentPredictionJournal.loadPredictions(context)
    val eligible = predictions.filter {
        it.decision in setOf("FAVORABLE", "HIGH_CONVICTION") &&
            AgentTradingUniverse.contains(it.symbol) &&
            account.positions.none { position -> position.predictionId == it.id }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(PaperBackground)
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = PaperSurface)) {
            Text("BACK", color = PaperText)
        }
        Spacer(Modifier.height(18.dp))
        Text("PAPER SANDBOX", color = PaperCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("$5,000 agent account", color = PaperText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Frozen recommendations only. No Robinhood orders can be sent from this sandbox.",
            color = PaperSecondary, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Universe: ${AgentTradingUniverse.symbols.joinToString(", ")}",
            color = PaperSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(16.dp))

        PaperCard {
            PaperRow("Equity", money(account.equity))
            PaperRow("Cash", money(account.cash))
            PaperRow("Open positions", account.positions.size.toString())
            PaperRow("Return", String.format(Locale.US, "%+.2f%%", account.totalReturnPercent))
            PaperRow("Max next entry", money(account.equity * PAPER_MAX_ENTRY_PERCENT / 100.0))
        }
        Spacer(Modifier.height(14.dp))

        PaperCard {
            Text("NEW AGENT FINDINGS", color = PaperCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (eligible.isEmpty()) "No unsimulated favorable recommendations." else
                    "${eligible.size} favorable recommendation${if (eligible.size == 1) "" else "s"} ready.",
                color = PaperSecondary
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = eligible.isNotEmpty(),
                onClick = {
                    var next = account
                    var opened = 0
                    eligible.sortedBy { it.createdAtEpochMillis }.forEach { prediction ->
                        val result = PaperTradingEngine.openFromPrediction(next, prediction)
                        next = result.account
                        if (result.accepted) opened++
                    }
                    account = next
                    PaperSandboxStorage.save(context, next)
                    message = "$opened paper position${if (opened == 1) "" else "s"} opened."
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PaperCyan, contentColor = PaperBackground)
            ) { Text("SIMULATE NEW RECOMMENDATIONS", fontWeight = FontWeight.Bold) }
            message?.let { Text(it, color = PaperGreen, modifier = Modifier.padding(top = 8.dp)) }
        }

        if (account.positions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            PaperCard {
                Text("OPEN PAPER POSITIONS", color = PaperCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                account.positions.reversed().forEach { position ->
                    PaperRow(
                        "${position.symbol} • ${position.decision.replace('_', ' ')}",
                        "${money(position.marketValue)} • ${money(position.unrealizedPnl)}"
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Paper fills currently use the price frozen with each saved recommendation. Live mark-to-market and automatic stop/target exits are the next increment.",
            color = PaperSecondary, fontSize = 11.sp
        )
    }
}

@Composable
private fun PaperCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PaperSurface)
    ) { Column(Modifier.padding(18.dp)) { content() } }
}

@Composable
private fun PaperRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = PaperSecondary, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Text(value, color = PaperText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

private fun money(value: Double) = String.format(Locale.US, "$%,.2f", value)

private object PaperSandboxStorage {
    private const val PREFS = "tradna_paper_sandbox"
    private const val KEY = "account"

    fun load(context: Context): PaperAccount {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return PaperAccount()
        return try {
            val root = JSONObject(raw)
            val positions = root.optJSONArray("positions") ?: JSONArray()
            PaperAccount(
                startingCash = root.optDouble("startingCash", PAPER_STARTING_CASH),
                cash = root.optDouble("cash", PAPER_STARTING_CASH),
                realizedPnl = root.optDouble("realizedPnl", 0.0),
                positions = buildList {
                    for (index in 0 until positions.length()) {
                        val item = positions.getJSONObject(index)
                        add(PaperPosition(
                            predictionId = item.getString("predictionId"), symbol = item.getString("symbol"),
                            openedAtEpochMillis = item.getLong("openedAt"), quantity = item.getDouble("quantity"),
                            entryPrice = item.getDouble("entryPrice"), lastPrice = item.getDouble("lastPrice"),
                            decision = item.getString("decision"), confidencePercent = item.getInt("confidence"),
                            stopPrice = item.optNullableDouble("stop"), targetPrice = item.optNullableDouble("target")
                        ))
                    }
                }
            )
        } catch (_: Exception) { PaperAccount() }
    }

    fun save(context: Context, account: PaperAccount) {
        val positions = JSONArray()
        account.positions.forEach { p ->
            positions.put(JSONObject().put("predictionId", p.predictionId).put("symbol", p.symbol)
                .put("openedAt", p.openedAtEpochMillis).put("quantity", p.quantity)
                .put("entryPrice", p.entryPrice).put("lastPrice", p.lastPrice)
                .put("decision", p.decision).put("confidence", p.confidencePercent)
                .put("stop", p.stopPrice ?: JSONObject.NULL).put("target", p.targetPrice ?: JSONObject.NULL))
        }
        val root = JSONObject().put("startingCash", account.startingCash).put("cash", account.cash)
            .put("realizedPnl", account.realizedPnl).put("positions", positions)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else getDouble(key)
}
