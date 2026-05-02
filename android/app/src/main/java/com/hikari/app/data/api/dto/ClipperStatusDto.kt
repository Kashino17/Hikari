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
    val forceUntil: Long = 0,
    val isForceActive: Boolean = false,
)

@Serializable
data class RetryFailedResponse(val retriedCount: Int)

@Serializable
data class ForceWindowResponseDto(val forceUntil: Long)
