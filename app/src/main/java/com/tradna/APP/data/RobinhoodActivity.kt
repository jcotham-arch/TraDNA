package com.tradna.APP.data

data class RobinhoodActivity(
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

data class ImportSummary(
    val activities: List<RobinhoodActivity>,
    val activityCount: Int,
    val buyCount: Int,
    val sellCount: Int,
    val optionCount: Int,
    val instrumentCount: Int,
    val startDate: String,
    val endDate: String,
    val fileName: String
)

