package com.hikari.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.profile.formatBytes
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTuning: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val backendUrl by vm.backendUrl.collectAsState(initial = "")
    val budget by vm.dailyBudget.collectAsState(initial = 15)
    val smart by vm.smartDownloads.collectAsState(initial = true)
    val disk by vm.diskUsage.collectAsState()
    var draft by remember(backendUrl) { mutableStateOf(backendUrl) }
    var budgetDraft by remember(budget) { mutableStateOf(budget.toString()) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = HikariText,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "EINSTELLUNGEN",
                    color = HikariTextFaint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Backend URL ──────────────────────────────────────────────────
            SectionLabel("BACKEND")
            SettingsCard {
                Text("Server-URL", color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tailscale-Hostname oder IP, z. B. http://macbook-pro.taile64a95.ts.net:3000",
                    color = HikariTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    cursorBrush = SolidColor(HikariAmber),
                    textStyle = TextStyle(
                        color = HikariText,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HikariBg, RoundedCornerShape(6.dp))
                        .border(0.5.dp, HikariBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
                AmberButton("URL speichern") {
                    scope.launch {
                        vm.setBackendUrl(draft.trim())
                        keyboard?.hide()
                    }
                }
            }

            // ── Downloads ─────────────────────────────────────────────────────
            SectionLabel("DOWNLOADS")
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Smart-Downloads",
                            color = HikariText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Lädt automatisch Folgen abonnierter Serien · nur über WLAN",
                            color = HikariTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Switch(
                        checked = smart,
                        onCheckedChange = vm::setSmartDownloads,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = HikariBg,
                            checkedTrackColor = HikariAmber,
                            checkedBorderColor = HikariAmber,
                            uncheckedThumbColor = HikariTextMuted,
                            uncheckedTrackColor = HikariSurface,
                            uncheckedBorderColor = HikariBorder,
                        ),
                    )
                }
                disk?.let { d ->
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(HikariBorder),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Speicher",
                                color = HikariText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${formatBytes(d.total_bytes)} von ${formatBytes(d.limit_bytes)}",
                                color = HikariTextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(HikariSurfaceHigh),
                        ) {
                            val frac = if (d.limit_bytes > 0) {
                                (d.total_bytes.toFloat() / d.limit_bytes).coerceIn(0f, 1f)
                            } else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(HikariAmber, Color(0xFFB45309)),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }

            // ── Daily budget ──────────────────────────────────────────────────
            SectionLabel("FEED-LIMIT")
            SettingsCard {
                Text("Tägliches Limit", color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Maximale Anzahl Videos pro Tag im Feed (Doom-Scroll-Prävention).",
                    color = HikariTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = budgetDraft,
                        onValueChange = { v ->
                            if (v.all { it.isDigit() } && v.length <= 3) budgetDraft = v
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(HikariAmber),
                        textStyle = TextStyle(
                            color = HikariText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(80.dp)
                            .background(HikariBg, RoundedCornerShape(6.dp))
                            .border(0.5.dp, HikariBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Videos / Tag", color = HikariTextMuted, fontSize = 12.sp)
                }
                AmberButton("Limit speichern") {
                    scope.launch {
                        budgetDraft.toIntOrNull()?.let { vm.setDailyBudget(it) }
                        keyboard?.hide()
                    }
                }
            }

            // ── Filter & AI-Tuning ────────────────────────────────────────────
            SectionLabel("FILTER & AI")
            SettingsCard {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "Tuning öffnen",
                            color = HikariText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Filter-Prompts und AI-Scoring justieren — Tuning-Workflow.",
                            color = HikariTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HikariSurface)
                            .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenTuning)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text("Öffnen", color = HikariAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Hikari · v0.25.0",
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)) {
        Text(
            text,
            color = HikariTextFaint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HikariSurface)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun AmberButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HikariAmber)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, color = HikariBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
