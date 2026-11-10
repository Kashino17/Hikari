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

    /** Alles Gerettete — gelöschte und bearbeitete Nachrichten, neueste zuerst. `null` = lädt noch. */
    val messages: StateFlow<List<WhatsAppMessageEntity>?> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stats: StateFlow<WhatsAppStats> = repository.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatsAppStats())

    val alertsEnabled: StateFlow<Boolean> = repository.alertsEnabled
    val directChatHistory: StateFlow<List<DirectChatHistoryItem>> = repository.directChatHistory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasMediaFolderAccess = MutableStateFlow(mediaVault.hasMediaFolderAccess())
    val hasMediaFolderAccess: StateFlow<Boolean> = _hasMediaFolderAccess.asStateFlow()

    private val _chatAnalysis = MutableStateFlow<WhatsAppChatAnalysis?>(null)
    val chatAnalysis: StateFlow<WhatsAppChatAnalysis?> = _chatAnalysis.asStateFlow()

    init {
        viewModelScope.launch {
            repository.pruneExpiredData()
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
    }

    fun refreshMediaFolderAccess() {
        _hasMediaFolderAccess.value = mediaVault.hasMediaFolderAccess()
    }

    /** Beim Verlassen der Ecke gilt alles Gezeigte als gesehen. */
    fun markAllSeen() {
        viewModelScope.launch {
            repository.markAllSeen()
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
