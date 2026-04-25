package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ImportVideosRequest(
    val urls: List<String>,
    val scrapeLinks: Boolean = false,
)

@Serializable
data class ImportVideosResponse(
    val queued: Int,
    val status: String? = null,
)
