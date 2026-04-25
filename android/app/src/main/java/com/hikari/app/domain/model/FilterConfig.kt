package com.hikari.app.domain.model

import com.hikari.app.data.api.dto.FilterConfigDto
import com.hikari.app.data.api.dto.FilterStateDto

data class FilterConfig(
    val likeTags: List<String>,
    val dislikeTags: List<String>,
    val moodTags: List<String>,
    val depthTags: List<String>,
    val languages: List<String>,
    val minDurationSec: Int,
    val maxDurationSec: Int,
    val examples: String,
    val scoreThreshold: Int,
) {
    fun toDto() = FilterConfigDto(
        likeTags, dislikeTags, moodTags, depthTags, languages,
        minDurationSec, maxDurationSec, examples, scoreThreshold,
    )
}

data class FilterState(
    val filter: FilterConfig,
    val promptOverride: String?,
    val assembledPrompt: String,
)

fun FilterConfigDto.toDomain() = FilterConfig(
    likeTags, dislikeTags, moodTags, depthTags, languages,
    minDurationSec, maxDurationSec, examples, scoreThreshold,
)

fun FilterStateDto.toDomain() = FilterState(
    filter = filter.toDomain(),
    promptOverride = promptOverride,
    assembledPrompt = assembledPrompt,
)

/**
 * Catalog presented in the chip pickers. Server prompt assembler doesn't care
 * what the strings are, but the UI lets the user pick from these defaults
 * AND add free-form ones.
 */
object TuningCatalogs {
    val moodOptions = listOf(
        "ruhig", "energetisch", "humorvoll", "ernst",
        "durchdacht", "inspirierend", "analytisch", "persönlich",
    )
    val depthOptions = listOf(
        "lehrreich", "tiefgründig", "locker", "schnell",
        "theoretisch", "praktisch", "visuell",
    )
    val languageOptions = listOf("de" to "Deutsch", "en" to "English", "jp" to "日本語")
}
