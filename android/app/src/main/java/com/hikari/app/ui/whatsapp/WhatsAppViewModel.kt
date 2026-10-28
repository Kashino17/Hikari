package com.hikari.app.ui.whatsapp

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.db.WhatsAppMessageEntity
import com.hikari.app.domain.whatsapp.DirectChatHistoryItem
import com.hikari.app.domain.whatsapp.WhatsAppChatAnalysis
import com.hikari.app.domain.whatsapp.WhatsAppChatAnalyzer
import com.hikari.app.domain.whatsapp.WhatsAppEditVersion
import com.hikari.app.domain.whatsapp.WhatsAppMediaVault
import com.hikari.app.domain.whatsapp.WhatsAppRepository
import com.hikari.app.domain.whatsapp.WhatsAppStats
import com.hikari.app.domain.whatsapp.WhatsAppStatusItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WhatsAppViewModel @Inject constructor(
    private val repository: WhatsAppRepository,
    private val mediaVault: WhatsAppMediaVault,
) : ViewModel() {

    val deletedMessages: StateFlow<List<WhatsAppMessageEntity>> = repository.deletedMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val editedMessages: StateFlow<List<WhatsAppMessageEntity>> = repository.editedMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<WhatsAppStats> = repository.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatsAppStats())

    val alertsEnabled: StateFlow<Boolean> = repository.alertsEnabled
    val directChatHistory: StateFlow<List<DirectChatHistoryItem>> = repository.directChatHistory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasMediaFolderAccess = MutableStateFlow(mediaVault.hasMediaFolderAccess())
    val hasMediaFolderAccess: StateFlow<Boolean> = _hasMediaFolderAccess.asStateFlow()

    private val _statusItems = MutableStateFlow<List<WhatsAppStatusItem>>(emptyList())
    val statusItems: StateFlow<List<WhatsAppStatusItem>> = _statusItems.asStateFlow()

    private val _chatAnalysis = MutableStateFlow<WhatsAppChatAnalysis?>(null)
    val chatAnalysis: StateFlow<WhatsAppChatAnalysis?> = _chatAnalysis.asStateFlow()

    init {
        // Beim Öffnen sofort normale Altlasten und Nachrichten > 7 Tage bereinigen
        viewModelScope.launch {
            repository.pruneExpiredData()
        }
        if (_hasMediaFolderAccess.value) {
            refreshStatuses()
        }
    }

    fun pruneNow() {
        viewModelScope.launch {
            repository.pruneExpiredData()
        }
    }

    fun buildInitialMediaTreeUri(): Uri = mediaVault.buildInitialWhatsAppMediaUri()

    fun onMediaFolderGranted(uri: Uri) {
        mediaVault.saveTreeUri(uri)
        _hasMediaFolderAccess.value = mediaVault.hasMediaFolderAccess()
        refreshStatuses()
    }

    fun refreshStatuses() {
        _hasMediaFolderAccess.value = mediaVault.hasMediaFolderAccess()
        if (!_hasMediaFolderAccess.value) return
        viewModelScope.launch {
            _statusItems.value = mediaVault.listWhatsAppStatuses()
        }
    }

    fun saveStatusToGallery(status: WhatsAppStatusItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = mediaVault.saveStatusToGallery(status)
            onResult(ok)
        }
    }

    fun analyzeExportedChatUri(context: Context, uri: Uri, onError: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val pathSegment = uri.lastPathSegment
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        WhatsAppChatAnalyzer.analyzeStream(stream, pathSegment)
                    }
                }.getOrNull()
            }
            if (result != null) {
                _chatAnalysis.value = result
            } else {
                onError()
            }
        }
    }

    fun runSampleChatAnalysis() {
        _chatAnalysis.value = WhatsAppChatAnalyzer.analyzeText(
            WhatsAppChatAnalyzer.buildSampleExportText(),
        )
    }

    fun clearChatAnalysis() {
        _chatAnalysis.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAlertsEnabled(enabled: Boolean) {
        repository.setAlertsEnabled(enabled)
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun runDemoScenario() {
        viewModelScope.launch {
            repository.runLiveDemoScenario()
        }
    }

    fun recordDirectChat(phoneNumber: String, displayFormatted: String, messagePreview: String) {
        repository.addDirectChatHistory(phoneNumber, displayFormatted, messagePreview)
    }

    fun clearDirectChatHistory() {
        repository.clearDirectChatHistory()
    }

    fun decodeEditHistory(editHistoryJson: String?): List<WhatsAppEditVersion> =
        repository.decodeEditHistory(editHistoryJson)
}
