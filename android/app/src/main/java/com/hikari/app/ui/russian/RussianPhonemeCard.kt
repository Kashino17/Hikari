package com.hikari.app.ui.russian

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.PronunciationDto
import com.hikari.app.domain.russian.RuPhonemeTips
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

sealed interface RuPhonemeState {
    data object Loading : RuPhonemeState
    data class Ready(val result: PronunciationDto) : RuPhonemeState
    data class Unavailable(val message: String) : RuPhonemeState
}

@HiltViewModel
class RussianPhonemeViewModel @Inject constructor(private val api: HikariApi) : ViewModel() {
    private val _state = MutableStateFlow<RuPhonemeState>(RuPhonemeState.Loading)
    val state: StateFlow<RuPhonemeState> = _state.asStateFlow()
    private var lastKey: String? = null

    fun check(clip: String, take: File, stamp: Long) {
        val key = "$clip#$stamp"
        if (key == lastKey) return
        lastKey = key
        _state.value = RuPhonemeState.Loading
        viewModelScope.launch {
            // Erster Aufruf kann das Modell laden (bis ~1 Min) — bei Timeout noch zweimal versuchen.
            var message = "Phonem-Check nicht erreichbar."
            repeat(3) { attempt ->
                try {
                    val bytes = withContext(Dispatchers.IO) { take.readBytes() }
                    val part = MultipartBody.Part.createFormData(
                        "file", "take.m4a", bytes.toRequestBody("audio/mp4".toMediaType()),
                    )
                    val clipBody = clip.toRequestBody("text/plain".toMediaType())
                    _state.value = RuPhonemeState.Ready(api.checkRussianPronunciation(clipBody, part))
                    return@launch
                } catch (e: HttpException) {
                    message = when (e.code()) {
                        503 -> "Phonem-Check ist am Server noch nicht eingerichtet (tools/russian_audio/setup_phoneme_server.sh)."
                        422 -> "Nichts gehört — sprich etwas lauter."
                        else -> "Phonem-Check ist fehlgeschlagen."
                    }
                    if (e.code() != 500) {
                        _state.value = RuPhonemeState.Unavailable(message)
                        return@launch
                    }
                } catch (e: IOException) {
                    message = "Server nicht erreichbar — der Phonem-Check läuft über deinen Mac."
                }
                if (attempt < 2) delay(4000)
            }
            _state.value = RuPhonemeState.Unavailable(message)
        }
    }
}

/**
 * Phonem-Vergleich deiner Aufnahme mit dem Muttersprachler-Clip (läuft auf dem
 * Hikari-Server): jedes Soll-Phonem grün/rot, dazu konkrete Tipps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RuPhonemeCard(
    refClip: String,
    take: File,
    takeStamp: Long,
    modifier: Modifier = Modifier,
    vm: RussianPhonemeViewModel = hiltViewModel(),
) {
    LaunchedEffect(refClip, takeStamp) { vm.check(refClip, take, takeStamp) }
    val state by vm.state.collectAsState()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .padding(12.dp),
    ) {
        when (val s = state) {
            RuPhonemeState.Loading -> Text(
                "Phonem-Check läuft … (erster Start kann bis zu einer Minute dauern)",
                color = HikariTextMuted, fontSize = 12.5.sp,
            )
            is RuPhonemeState.Unavailable -> Text(s.message, color = HikariTextMuted, fontSize = 12.5.sp)
            is RuPhonemeState.Ready -> {
                val r = s.result
                Text(
                    "Aussprache ${r.score} %",
                    color = if (r.score >= 80) RuGood else HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    r.marks.forEach { m ->
                        val color = when (m.status) {
                            "ok" -> RuGood
                            "sub" -> RuBad
                            else -> Color(0xFFF59E0B)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (m.status == "sub") "${m.ref}→${m.user}" else m.ref,
                                color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                val tips = RuPhonemeTips.hints(r.marks.filter { it.status != "ok" }.map { it.ref to it.user })
                tips.forEach { t ->
                    Spacer(Modifier.height(8.dp))
                    Text(t, color = HikariText, fontSize = 12.5.sp)
                }
                if (tips.isEmpty() && r.score < 80) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Hör das Original nochmal und achte auf die rot markierten Laute.",
                        color = HikariTextMuted, fontSize = 12.5.sp,
                    )
                }
            }
        }
    }
}
