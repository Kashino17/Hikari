package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PollResponse(
    val queued: Int,
    val skipped: Int,
    val errors: List<String>,
)
