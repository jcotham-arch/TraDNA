package com.tradna.APP.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "normalized_trade_activities",
    indices = [
        Index(value = ["source"]),
        Index(value = ["assetClass"]),
        Index(value = ["symbol"]),
        Index(value = ["activityDate"]),
        Index(value = ["source", "accountId", "symbol"])
    ]
)
data class NormalizedTradeActivityEntity(
    @PrimaryKey
    val id: String,
    val source: String,
    val accountId: String?,
    val assetClass: String,
    val symbol: String,
    val underlyingSymbol: String?,
    val side: String,
    val quantity: Double,
    val price: Double?,
    val activityDate: String,
    val commission: Double,
    val fees: Double,
    val optionExpirationDate: String?,
    val optionStrikePrice: Double?,
    val optionRight: String?,
    val optionContractMultiplier: Double?,
    val futuresRootSymbol: String?,
    val futuresExpirationDate: String?,
    val futuresPointValue: Double?,
    val futuresTickSize: Double?,
    val futuresTickValue: Double?,
    val orderId: String?,
    val status: String?,
    val notes: String?,
    val rawDescription: String?
)
