package com.tradna.APP.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTradingUniverseTest {
    @Test
    fun `includes the explicit quantum computing theme`() {
        val quantum = AgentTradingUniverse.themes.single { it.id == "quantum_computing" }

        assertEquals(setOf("QBTS", "QUBT", "RGTI"), quantum.symbols)
        assertTrue(AgentTradingUniverse.contains("rgti"))
        assertFalse(AgentTradingUniverse.contains("NVDA"))
        assertFalse(AgentTradingUniverse.contains("SPCX"))
        assertFalse(AgentTradingUniverse.contains("UNRELATED"))
    }
}
