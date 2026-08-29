package com.tradna.APP.data.local

import com.tradna.APP.data.RobinhoodActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RobinhoodActivityMapperTest {

    @Test
    fun `all Robinhood fields survive mapping`() {
        val activity = activity()
        val restored = RobinhoodActivityMapper.toDomain(
            RobinhoodActivityMapper.toEntities(listOf(activity)).single()
        )

        assertEquals(activity, restored)
    }

    @Test
    fun `stable identities preserve legitimate identical executions`() {
        val activity = activity()
        val first = RobinhoodActivityMapper.toEntities(listOf(activity, activity))
        val second = RobinhoodActivityMapper.toEntities(listOf(activity, activity))

        assertNotEquals(first[0].id, first[1].id)
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(listOf(0, 1), first.map { it.occurrenceIndex })
        assertEquals(listOf(0, 1), first.map { it.sourceOrder })
    }

    @Test
    fun `source order preserves same-day execution sequence`() {
        val sell = activity().copy(transCode = "Sell", amount = "$220.00")
        val buy = activity()

        val entities = RobinhoodActivityMapper.toEntities(listOf(sell, buy))

        assertEquals(listOf(0, 1), entities.map { it.sourceOrder })
        assertEquals(listOf("Sell", "Buy"), entities.map { it.transCode })
    }

    @Test
    fun `identity normalization ignores harmless case and whitespace changes`() {
        val original = activity()
        val reformatted = original.copy(
            instrument = " aapl ",
            description = "Apple   purchase"
        )

        val originalId = RobinhoodActivityMapper.toEntities(listOf(original)).single().id
        val reformattedId = RobinhoodActivityMapper.toEntities(listOf(reformatted)).single().id

        assertEquals(originalId, reformattedId)
    }

    private fun activity() = RobinhoodActivity(
        activityDate = "8/28/2026",
        processDate = "8/28/2026",
        settleDate = "9/1/2026",
        instrument = "AAPL",
        description = "Apple purchase",
        transCode = "Buy",
        quantity = "2",
        price = "$100.00",
        amount = "($200.00)"
    )
}
