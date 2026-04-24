package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyStatsDto(
    val windowDays: Int,
    val viewed: Int,
    val approved: Int,
    val rejected: Int,
    val byCategory: Map<String, Int>,
    val avgScore: Double,
    val diskUsedMb: Int,
)
