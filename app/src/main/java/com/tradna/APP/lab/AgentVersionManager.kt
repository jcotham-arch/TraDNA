package com.tradna.APP.lab

data class AgentScoringConfig(
    val versionName: String,
    val technicalWeight: Double,
    val historyWeight: Double,
    val entryWeight: Double,
    val riskWeight: Double,
    val evidenceWeight: Double,
    val favorableThreshold: Int,
    val highConvictionThreshold: Int,
    val watchThreshold: Int,
    val waitThreshold: Int,
    val notes: String = ""
) {

    fun normalized(): AgentScoringConfig {

        val total =
            technicalWeight +
                    historyWeight +
                    entryWeight +
                    riskWeight +
                    evidenceWeight

        if (
            total <= 0.0
        ) {
            return this
        }

        return copy(
            technicalWeight =
                technicalWeight / total,

            historyWeight =
                historyWeight / total,

            entryWeight =
                entryWeight / total,

            riskWeight =
                riskWeight / total,

            evidenceWeight =
                evidenceWeight / total
        )
    }
}

data class AgentVersionComparison(
    val current: AgentScoringConfig,
    val proposed: AgentScoringConfig,
    val changedFields: List<String>,
    val summary: String
)

object AgentVersionManager {

    val currentVersion =
        AgentScoringConfig(
            versionName =
                "v1.0",

            technicalWeight =
                0.30,

            historyWeight =
                0.28,

            entryWeight =
                0.18,

            riskWeight =
                0.12,

            evidenceWeight =
                0.12,

            favorableThreshold =
                72,

            highConvictionThreshold =
                85,

            watchThreshold =
                58,

            waitThreshold =
                42,

            notes =
                "Baseline TraDNA advisory scoring model."
        )

    fun buildProposedVersion(
        calibration: AgentCalibrationReport
    ): AgentScoringConfig {

        var technical =
            currentVersion.technicalWeight

        var history =
            currentVersion.historyWeight

        var entry =
            currentVersion.entryWeight

        var risk =
            currentVersion.riskWeight

        var evidence =
            currentVersion.evidenceWeight

        var favorable =
            currentVersion.favorableThreshold

        var highConviction =
            currentVersion.highConvictionThreshold

        calibration.recommendations
            .forEach {
                    recommendation ->

                when (
                    recommendation.title
                ) {

                    "Tighten FAVORABLE threshold" -> {

                        favorable =
                            77
                    }

                    "Tighten HIGH CONVICTION threshold" -> {

                        highConviction =
                            89
                    }

                    "Historical similarity is highly predictive" -> {

                        history +=
                            0.04

                        technical -=
                            0.02

                        evidence -=
                            0.02
                    }

                    "Technical score is carrying more signal" -> {

                        technical +=
                            0.04

                        history -=
                            0.02

                        evidence -=
                            0.02
                    }
                }
            }

        /*
         * Keep all weights inside sensible bounds.
         */
        technical =
            technical.coerceIn(
                0.15,
                0.45
            )

        history =
            history.coerceIn(
                0.15,
                0.45
            )

        entry =
            entry.coerceIn(
                0.08,
                0.30
            )

        risk =
            risk.coerceIn(
                0.05,
                0.25
            )

        evidence =
            evidence.coerceIn(
                0.05,
                0.25
            )

        return AgentScoringConfig(
            versionName =
                "v1.1-proposed",

            technicalWeight =
                technical,

            historyWeight =
                history,

            entryWeight =
                entry,

            riskWeight =
                risk,

            evidenceWeight =
                evidence,

            favorableThreshold =
                favorable,

            highConvictionThreshold =
                highConviction,

            watchThreshold =
                currentVersion.watchThreshold,

            waitThreshold =
                currentVersion.waitThreshold,

            notes =
                if (
                    calibration.calibrationReady
                ) {
                    "Generated from prospective calibration evidence. Requires validation before promotion."
                } else {
                    "Draft only. Calibration sample is not yet large enough for promotion."
                }
        )
            .normalized()
    }

    fun compare(
        current: AgentScoringConfig,
        proposed: AgentScoringConfig
    ): AgentVersionComparison {

        val changes =
            mutableListOf<String>()

        if (
            current.technicalWeight !=
            proposed.technicalWeight
        ) {

            changes.add(
                "Technical weight ${percent(current.technicalWeight)} → ${percent(proposed.technicalWeight)}"
            )
        }

        if (
            current.historyWeight !=
            proposed.historyWeight
        ) {

            changes.add(
                "History weight ${percent(current.historyWeight)} → ${percent(proposed.historyWeight)}"
            )
        }

        if (
            current.entryWeight !=
            proposed.entryWeight
        ) {

            changes.add(
                "Entry weight ${percent(current.entryWeight)} → ${percent(proposed.entryWeight)}"
            )
        }

        if (
            current.riskWeight !=
            proposed.riskWeight
        ) {

            changes.add(
                "Risk weight ${percent(current.riskWeight)} → ${percent(proposed.riskWeight)}"
            )
        }

        if (
            current.evidenceWeight !=
            proposed.evidenceWeight
        ) {

            changes.add(
                "Evidence weight ${percent(current.evidenceWeight)} → ${percent(proposed.evidenceWeight)}"
            )
        }

        if (
            current.favorableThreshold !=
            proposed.favorableThreshold
        ) {

            changes.add(
                "FAVORABLE threshold ${current.favorableThreshold} → ${proposed.favorableThreshold}"
            )
        }

        if (
            current.highConvictionThreshold !=
            proposed.highConvictionThreshold
        ) {

            changes.add(
                "HIGH CONVICTION threshold ${current.highConvictionThreshold} → ${proposed.highConvictionThreshold}"
            )
        }

        return AgentVersionComparison(
            current =
                current,

            proposed =
                proposed,

            changedFields =
                changes,

            summary =
                if (
                    changes.isEmpty()
                ) {

                    "No version change is currently recommended."

                } else {

                    "${changes.size} controlled Agent parameter changes are proposed for validation."
                }
        )
    }

    private fun percent(
        value: Double
    ): String {

        return String.format(
            java.util.Locale.US,
            "%.0f%%",
            value *
                    100.0
        )
    }
}

