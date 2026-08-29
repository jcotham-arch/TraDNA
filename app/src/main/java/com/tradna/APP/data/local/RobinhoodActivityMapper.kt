package com.tradna.APP.data.local

import com.tradna.APP.data.RobinhoodActivity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

object RobinhoodActivityMapper {

    fun toEntities(
        activities: List<RobinhoodActivity>
    ): List<RobinhoodActivityEntity> {
        val occurrences = mutableMapOf<String, Int>()

        return activities.mapIndexed { sourceOrder, activity ->
            val fingerprint = fingerprint(activity)
            val occurrence = occurrences.getOrDefault(fingerprint, 0)
            occurrences[fingerprint] = occurrence + 1

            RobinhoodActivityEntity(
                id = stableId(fingerprint, occurrence),
                occurrenceIndex = occurrence,
                activitySortKey = dateSortKey(activity.activityDate),
                sourceOrder = sourceOrder,
                activityDate = activity.activityDate,
                processDate = activity.processDate,
                settleDate = activity.settleDate,
                instrument = activity.instrument,
                description = activity.description,
                transCode = activity.transCode,
                quantity = activity.quantity,
                price = activity.price,
                amount = activity.amount
            )
        }
    }

    fun toDomain(entity: RobinhoodActivityEntity) = RobinhoodActivity(
        activityDate = entity.activityDate,
        processDate = entity.processDate,
        settleDate = entity.settleDate,
        instrument = entity.instrument,
        description = entity.description,
        transCode = entity.transCode,
        quantity = entity.quantity,
        price = entity.price,
        amount = entity.amount
    )

    private fun fingerprint(activity: RobinhoodActivity): String = listOf(
        activity.activityDate,
        activity.processDate,
        activity.settleDate,
        activity.instrument,
        activity.description,
        activity.transCode,
        activity.quantity,
        activity.price,
        activity.amount
    ).joinToString("|") { normalize(it) }

    private fun normalize(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .uppercase(Locale.US)

    private fun stableId(fingerprint: String, occurrence: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "$fingerprint|$occurrence".toByteArray(StandardCharsets.UTF_8)
        )
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun dateSortKey(value: String): Long = try {
        SimpleDateFormat("M/d/yyyy", Locale.US).apply {
            isLenient = false
        }.parse(value)?.time ?: Long.MIN_VALUE
    } catch (_: Exception) {
        Long.MIN_VALUE
    }
}
