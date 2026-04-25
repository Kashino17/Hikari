package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ImportVideosRequest(val urls: List<String>)

@Serializable
data class ImportVideosResponse(val queued: Int)
