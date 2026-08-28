package com.tradna.APP.data.local

import com.tradna.APP.data.NormalizedAssetClass
import com.tradna.APP.data.NormalizedTradeActivity
import com.tradna.APP.data.NormalizedTradeSide
import com.tradna.APP.data.TradingPlatformSource

object NormalizedTradeActivityMapper {

    fun toEntity(activity: NormalizedTradeActivity) =
        NormalizedTradeActivityEntity(
            id = activity.id,
            source = activity.source.name,
            accountId = activity.accountId,
            assetClass = activity.assetClass.name,
            symbol = activity.symbol,
            underlyingSymbol = activity.underlyingSymbol,
            side = activity.side.name,
            quantity = activity.quantity,
            price = activity.price,
            activityDate = activity.activityDate,
            commission = activity.commission,
            fees = activity.fees,
            optionExpirationDate = activity.optionExpirationDate,
            optionStrikePrice = activity.optionStrikePrice,
            optionRight = activity.optionRight,
            optionContractMultiplier = activity.optionContractMultiplier,
            futuresRootSymbol = activity.futuresRootSymbol,
            futuresExpirationDate = activity.futuresExpirationDate,
            futuresPointValue = activity.futuresPointValue,
            futuresTickSize = activity.futuresTickSize,
            futuresTickValue = activity.futuresTickValue,
            orderId = activity.orderId,
            status = activity.status,
            notes = activity.notes,
            rawDescription = activity.rawDescription
        )

    fun toDomain(entity: NormalizedTradeActivityEntity) =
        NormalizedTradeActivity(
            id = entity.id,
            source = enumValueOrDefault(entity.source, TradingPlatformSource.GENERIC_CSV),
            accountId = entity.accountId,
            assetClass = enumValueOrDefault(entity.assetClass, NormalizedAssetClass.UNKNOWN),
            symbol = entity.symbol,
            underlyingSymbol = entity.underlyingSymbol,
            side = enumValueOrDefault(entity.side, NormalizedTradeSide.UNKNOWN),
            quantity = entity.quantity,
            price = entity.price,
            activityDate = entity.activityDate,
            commission = entity.commission,
            fees = entity.fees,
            optionExpirationDate = entity.optionExpirationDate,
            optionStrikePrice = entity.optionStrikePrice,
            optionRight = entity.optionRight,
            optionContractMultiplier = entity.optionContractMultiplier,
            futuresRootSymbol = entity.futuresRootSymbol,
            futuresExpirationDate = entity.futuresExpirationDate,
            futuresPointValue = entity.futuresPointValue,
            futuresTickSize = entity.futuresTickSize,
            futuresTickValue = entity.futuresTickValue,
            orderId = entity.orderId,
            status = entity.status,
            notes = entity.notes,
            rawDescription = entity.rawDescription
        )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        default: T
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: default
}
