package com.tradna.APP.lab

data class AgentTradingTheme(
    val id: String,
    val title: String,
    val symbols: Set<String>
)

object AgentTradingUniverse {
    const val VERSION = "personal-universe-v2"

    val themes = listOf(
        AgentTradingTheme(
            id = "technology_infrastructure",
            title = "Technology & AI infrastructure",
            symbols = setOf("RXT")
        ),
        AgentTradingTheme(
            id = "aerospace_autonomy",
            title = "Aerospace, defense & autonomy",
            symbols = setOf("ONDS")
        ),
        AgentTradingTheme(
            id = "advanced_energy",
            title = "Advanced energy",
            symbols = setOf("OKLO")
        ),
        AgentTradingTheme(
            id = "quantum_computing",
            title = "Quantum computing",
            symbols = setOf("QBTS", "QUBT", "RGTI")
        )
    )

    val symbols: Set<String> = themes.flatMap { it.symbols }.toSet()

    fun contains(symbol: String): Boolean = symbol.trim().uppercase() in symbols
}
