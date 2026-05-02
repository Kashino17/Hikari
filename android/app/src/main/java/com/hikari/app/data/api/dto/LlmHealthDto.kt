package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LlmHealthDto(
    val reachable: Boolean,
    val baseUrl: String,
    val expectedModel: String? = null,
    val modelLoaded: Boolean? = null,
    val error: String? = null,
)
