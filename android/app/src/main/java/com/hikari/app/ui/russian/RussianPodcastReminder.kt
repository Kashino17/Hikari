package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.russian.RussianPodcastReminderScheduler
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RussianPodcastReminderViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val scheduler: RussianPodcastReminderScheduler,
) : ViewModel() {
    val enabled = settings.russianPodcastReminderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val minutes = settings.russianPodcastReminderMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, 1080)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            settings.setRussianPodcastReminderEnabled(value)
            if (value) scheduler.schedule(minutes.value) else scheduler.cancel()
        }
    }

    fun setTime(value: Int) {
        viewModelScope.launch {
            settings.setRussianPodcastReminderMinutes(value)
            if (enabled.value) scheduler.schedule(value)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RussianPodcastReminderScreen(
    onBack: () -> Unit,
    vm: RussianPodcastReminderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val enabled by vm.enabled.collectAsState()
    val minutes by vm.minutes.collectAsState()
    val timeState = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
    LaunchedEffect(minutes) {
        timeState.hour = minutes / 60
        timeState.minute = minutes % 60
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setEnabled(true)
    }
    Column(
        Modifier.fillMaxSize().background(HikariBg).verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
            }
            Column {
                Text("Tägliche Erinnerung", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Podcast-Kurs", color = HikariTextMuted, fontSize = 12.5.sp)
            }
        }
        Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HikariCardBg).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("An die nächste Lektion erinnern", color = HikariText, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Die Erinnerung entfällt, wenn du deine Lektion heute schon abgeschlossen hast.",
                        color = HikariTextMuted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { value ->
                        if (!value) {
                            vm.setEnabled(false)
                        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.setEnabled(true)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = HikariAmber),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("Uhrzeit", color = HikariText, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            TimePicker(state = timeState)
            Spacer(Modifier.height(18.dp))
            RuPrimaryButton("Uhrzeit speichern") {
                vm.setTime(timeState.hour * 60 + timeState.minute)
            }
        }
    }
}
