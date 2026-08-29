package com.tradna.APP.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "robinhood_activities",
    indices = [
        Index(value = ["activitySortKey"]),
        Index(value = ["activitySortKey", "sourceOrder"]),
        Index(value = ["instrument"]),
        Index(value = ["transCode"])
    ]
)
data class RobinhoodActivityEntity(
    @PrimaryKey
    val id: String,
    val occurrenceIndex: Int,
    val activitySortKey: Long,
    val sourceOrder: Int,
    val activityDate: String,
    val processDate: String,
    val settleDate: String,
    val instrument: String,
    val description: String,
    val transCode: String,
    val quantity: String,
    val price: String,
    val amount: String
)
