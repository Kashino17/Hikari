package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TodayCountResponse(
    val dailyBudget: Int,
    val unseenCount: Int,
    val capped: Boolean,
    // Etappe 4 (Zeitbudget) — optional, damit alte Server-Antworten weiter parsen.
    val budgetMinutes: Int? = null,
    val remainingSeconds: Int? = null,
    val totalSeconds: Int? = null,
    /** Tatsächlich geschaute Sekunden — nur die zählen gegen das Tagesbudget. */
    val consumedSeconds: Double? = null,
)
