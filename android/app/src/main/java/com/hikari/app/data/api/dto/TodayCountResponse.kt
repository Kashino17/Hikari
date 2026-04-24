package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TodayCountResponse(
    val dailyBudget: Int,
    val unseenCount: Int,
    val capped: Boolean,
)
