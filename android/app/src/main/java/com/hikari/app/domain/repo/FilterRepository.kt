package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.SetOverrideRequest
import com.hikari.app.data.api.dto.UpdateFilterRequest
import com.hikari.app.domain.model.FilterConfig
import com.hikari.app.domain.model.FilterState
import com.hikari.app.domain.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterRepository @Inject constructor(private val api: HikariApi) {

    suspend fun fetch(): FilterState = api.getFilter().toDomain()

    suspend fun updateFilter(filter: FilterConfig): FilterState =
        api.updateFilter(UpdateFilterRequest(filter.toDto())).toDomain()

    suspend fun setOverride(prompt: String): FilterState =
        api.setPromptOverride(SetOverrideRequest(prompt)).toDomain()

    suspend fun clearOverride(): FilterState =
        api.clearPromptOverride().toDomain()

    // ── Per-channel variants — fall back to the global filter when the
    //    channel has no own override (`inherited = true`). ──────────────────
    suspend fun fetchForChannel(channelId: String): FilterState =
        api.getChannelFilter(channelId).toDomain()

    suspend fun updateFilterForChannel(channelId: String, filter: FilterConfig): FilterState =
        api.updateChannelFilter(channelId, UpdateFilterRequest(filter.toDto())).toDomain()

    suspend fun setOverrideForChannel(channelId: String, prompt: String): FilterState =
        api.setChannelPromptOverride(channelId, SetOverrideRequest(prompt)).toDomain()

    /** Removes the channel's own filter so it inherits the global one again. */
    suspend fun resetChannelToGlobal(channelId: String): Unit =
        api.clearChannelFilter(channelId)
}
