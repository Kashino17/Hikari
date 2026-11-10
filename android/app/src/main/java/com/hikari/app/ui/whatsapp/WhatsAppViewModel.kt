package com.hikari.app.ui.whatsapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.db.WhatsAppMessageEntity
import com.hikari.app.domain.whatsapp.DirectChatHistoryItem
import com.hikari.app.domain.whatsapp.MediaFolderState
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
import java.io.File
import java.io.FileOutputStream
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

    /** Zustand der Medienordner-Freigabe; bis zur ersten Prüfung gilt eine vorhandene Freigabe als gut. */
    private val _mediaFolderState = MutableStateFlow(
        if (mediaVault.hasMediaFolderAccess()) MediaFolderState.READY else MediaFolderState.MISSING,
    )
    val mediaFolderState: StateFlow<MediaFolderState> = _mediaFolderState.asStateFlow()

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
        refreshMediaFolderAccess()
    }

    fun refreshMediaFolderAccess() {
        viewModelScope.launch {
            _mediaFolderState.value = mediaVault.mediaFolderState()
        }
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

    fun runDemoScenario(context: Context) {
        viewModelScope.launch {
            val photo = withContext(Dispatchers.IO) { createSamplePhoto(context) }
            repository.runLiveDemoScenario(samplePhotoPath = photo)
        }
    }

    /** Malt ein schlichtes Beispielbild (Abendhimmel), damit das Beispiel ohne fremde Fotos auskommt. */
    private fun createSamplePhoto(context: Context): String? = runCatching {
        val width = 900
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val sky = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF1E1B4B.toInt(), 0xFF7C3AED.toInt(), 0xFFF472B6.toInt(), 0xFFFBBF24.toInt()),
                floatArrayOf(0f, 0.45f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
        canvas.drawCircle(width * 0.68f, height * 0.7f, 110f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF7D6.toInt() })
        val hills = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14122B.toInt() }
        canvas.drawOval(-200f, height * 0.82f, width * 0.75f, height * 1.3f, hills)
        canvas.drawOval(width * 0.35f, height * 0.88f, width * 1.3f, height * 1.3f, hills)

        val dir = File(context.filesDir, WhatsAppRepository.MEDIA_DIR).apply { mkdirs() }
        val file = File(dir, "wa_photo_beispiel_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        file.absolutePath
    }.getOrNull()

    fun recordDirectChat(phoneNumber: String, displayFormatted: String, messagePreview: String) {
        repository.addDirectChatHistory(phoneNumber, displayFormatted, messagePreview)
    }

    fun clearDirectChatHistory() {
        repository.clearDirectChatHistory()
    }

    fun decodeEditHistory(editHistoryJson: String?): List<WhatsAppEditVersion> =
        repository.decodeEditHistory(editHistoryJson)
}
