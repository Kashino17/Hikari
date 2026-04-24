package com.hikari.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {
    val backendUrl: StateFlow<String> =
        store.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val dailyBudget: StateFlow<Int> =
        store.dailyBudget.stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    fun setBackendUrl(url: String) = viewModelScope.launch { store.setBackendUrl(url) }
    fun setDailyBudget(value: Int) = viewModelScope.launch { store.setDailyBudget(value) }
}
