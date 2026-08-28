package com.tradna.APP.data.local

import com.tradna.APP.data.NormalizedAssetClass
import com.tradna.APP.data.NormalizedTradeActivity
import com.tradna.APP.data.NormalizedTradeSide
import com.tradna.APP.data.TradingPlatformSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizedTradeActivityMapperTest {

    @Test
    fun `all normalized execution fields survive a database round trip`() {
        val activity = NormalizedTradeActivity(
            id = "execution-1",
            source = TradingPlatformSource.WEALTHCHARTS,
            accountId = "account-1",
            assetClass = NormalizedAssetClass.OPTION,
            symbol = "AAPL260821C00200000",
            underlyingSymbol = "AAPL",
            side = NormalizedTradeSide.BUY_TO_OPEN,
            quantity = 2.0,
            price = 1.25,
            activityDate = "2026-08-28T10:00:00Z",
            commission = 0.50,
            fees = 0.10,
            optionExpirationDate = "2026-08-21",
            optionStrikePrice = 200.0,
            optionRight = "CALL",
            optionContractMultiplier = 100.0,
            futuresRootSymbol = null,
            futuresExpirationDate = null,
            futuresPointValue = null,
            futuresTickSize = null,
            futuresTickValue = null,
            orderId = "order-1",
            status = "FILLED",
            notes = "planned entry",
            rawDescription = "original broker row"
        )

        val restored = NormalizedTradeActivityMapper.toDomain(
            NormalizedTradeActivityMapper.toEntity(activity)
        )

        assertEquals(activity, restored)
    }

    @Test
    fun `unknown stored enum values degrade safely`() {
        val entity = NormalizedTradeActivityEntity(
            id = "future-version",
            source = "NEW_BROKER",
            accountId = null,
            assetClass = "NEW_ASSET_CLASS",
            symbol = "UNKNOWN",
            underlyingSymbol = null,
            side = "NEW_SIDE",
            quantity = 1.0,
            price = 10.0,
            activityDate = "2026-08-28",
            commission = 0.0,
            fees = 0.0,
            optionExpirationDate = null,
            optionStrikePrice = null,
            optionRight = null,
            optionContractMultiplier = null,
            futuresRootSymbol = null,
            futuresExpirationDate = null,
            futuresPointValue = null,
            futuresTickSize = null,
            futuresTickValue = null,
            orderId = null,
            status = null,
            notes = null,
            rawDescription = null
        )

        val restored = NormalizedTradeActivityMapper.toDomain(entity)

        assertEquals(TradingPlatformSource.GENERIC_CSV, restored.source)
        assertEquals(NormalizedAssetClass.UNKNOWN, restored.assetClass)
        assertEquals(NormalizedTradeSide.UNKNOWN, restored.side)
    }
}
