package com.hikari.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.data.prefs.SponsorBlockPrefs
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val sponsorPrefs: SponsorBlockPrefs,
) : ViewModel() {
    val backendUrl: StateFlow<String> =
        store.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val dailyBudget: StateFlow<Int> =
        store.dailyBudget.stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    val segmentBehaviors: StateFlow<Map<String, SegmentBehavior>> =
        sponsorPrefs.behaviors.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            SegmentCategories.all.associate { it.apiKey to it.defaultBehavior }
        )

    val totalSkippedCount: StateFlow<Long> =
        sponsorPrefs.totalSkippedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val totalSkippedMs: StateFlow<Long> =
        sponsorPrefs.totalSkippedMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun setBackendUrl(url: String) = viewModelScope.launch { store.setBackendUrl(url) }
    fun setDailyBudget(value: Int) = viewModelScope.launch { store.setDailyBudget(value) }

    fun setSegmentBehavior(apiKey: String, behavior: SegmentBehavior) =
        viewModelScope.launch { sponsorPrefs.setBehavior(apiKey, behavior) }

    fun resetSponsorStats() = viewModelScope.launch { sponsorPrefs.resetStats() }
}
