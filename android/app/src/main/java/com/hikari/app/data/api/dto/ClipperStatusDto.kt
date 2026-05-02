package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClipperStatusDto(
    val pending: Int,
    val processing: Int,
    val failed: Int,
    val no_highlights: Int,
    val done: Int,
    val isWindowActive: Boolean,
    val lastRanAt: Long?,
)

@Serializable
data class RetryFailedResponse(val retriedCount: Int)
