package com.hikari.app.ui.rejected

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.RejectedItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RejectedViewModel @Inject constructor(private val api: HikariApi) : ViewModel() {
    private val _items = MutableStateFlow<List<RejectedItemDto>>(emptyList())
    val items: StateFlow<List<RejectedItemDto>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        runCatching { api.getRejected(50) }.onSuccess { _items.value = it }
        _loading.value = false
    }
}
