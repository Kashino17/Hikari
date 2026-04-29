package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.DiscoveryResponseDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the local AI Discovery Engine endpoint (`GET /discovery`).
 *
 * Returns scored, sorted, and quality-filtered channel candidates the user
 * is NOT following. Backed by `backend/src/discovery/discoveryEngine.ts`.
 */
@Singleton
class DiscoveryRepository @Inject constructor(private val api: HikariApi) {

    suspend fun discover(
        limit: Int = 12,
        longFormMinSeconds: Int? = null,
    ): DiscoveryResponseDto = api.getDiscovery(
        limit = limit,
        longFormMinSeconds = longFormMinSeconds,
    )
}
