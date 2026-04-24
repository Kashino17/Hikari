package com.hikari.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SavedViewModel @Inject constructor(
    repo: FeedRepository,
) : ViewModel() {
    val saved: StateFlow<List<FeedItem>> =
        repo.savedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
