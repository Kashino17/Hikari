package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

/** Zeitbudget des Tagesmixes (GET/PUT /feed/budget). */
@Serializable
data class BudgetBody(val minutes: Int)
