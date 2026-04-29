package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

// Wire-format mirror of backend `GET /discovery` response.
// Backend file: backend/src/routes/discovery.ts

@Serializable
data class DiscoveryResponseDto(
    val results: List<DiscoveryCandidateDto>,
    val meta: DiscoveryMetaDto,
)

@Serializable
data class DiscoveryCandidateDto(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val bannerUrl: String? = null,
    val subscribers: Long? = null,
    val videoCount: Int,
    val longFormRatio: Double,
    val avgOverallScore: Double,
    val avgEducationalValue: Double,
    val avgClickbaitRisk: Double,
    val categoryDistribution: Map<String, Int> = emptyMap(),
    val score: Double,
    val breakdown: DiscoveryBreakdownDto,
)

@Serializable
data class DiscoveryBreakdownDto(
    val category: Double,
    val similarity: Double,
    val quality: Double,
    val longForm: Double,
)

@Serializable
data class DiscoveryMetaDto(
    val limit: Int,
    val followedCount: Int,
    val candidatePoolSize: Int,
    val qualityThreshold: Int,
    val categoryWeights: Map<String, Double> = emptyMap(),
    val longFormMinSeconds: Int,
)
