package com.hikari.app.data.api.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class FilterConfigDto(
    val likeTags: List<String>,
    val dislikeTags: List<String>,
    val moodTags: List<String>,
    val depthTags: List<String>,
    val languages: List<String>,
    val minDurationSec: Int,
    val maxDurationSec: Int,
    val examples: String,
    val scoreThreshold: Int,
)

@Serializable
data class FilterStateDto(
    val filter: FilterConfigDto,
    val promptOverride: String? = null,
    val assembledPrompt: String,
    val updatedAt: Long,
)

/** PUT body for filter-only update. promptOverride field is omitted on the wire. */
@Serializable
data class UpdateFilterRequest(val filter: FilterConfigDto)

/** Sets a manual override. */
@Serializable
data class SetOverrideRequest(val promptOverride: String)

/**
 * Clears the override. Backend distinguishes "absent" (no change) from "null"
 * (clear), so we force null encoding.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ClearOverrideRequest(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val promptOverride: String? = null,
)
