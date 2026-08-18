package com.hikari.app.ui.profile.tabs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.api.dto.ChannelSearchResultDto
import com.hikari.app.data.api.dto.RecommendationDto
import com.hikari.app.domain.model.Channel
import com.hikari.app.ui.channels.ChannelsViewModel
import com.hikari.app.ui.profile.components.BannerCard
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun ChannelsTab(
    onOpenChannel: (String) -> Unit,
    onOpenImport: () -> Unit,
    vm: ChannelsViewModel = hiltViewModel(),
) {
    val channels by vm.channels.collectAsState()
    val query by vm.query.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val recommendations by vm.recommendations.collectAsState()
    val ctx = LocalContext.current

    // Refresh the subscribed list on (re-)entry — e.g. after unsubscribing on the
    // channel detail screen, so the removed channel doesn't linger here.
    LaunchedEffect(Unit) { vm.load() }

    val isSearching = query.trim().length >= 2

    fun openInBrowser(url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    var unsubTarget by remember { mutableStateOf<Channel?>(null) }
    unsubTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unsubTarget = null },
            containerColor = HikariBg,
            title = { Text("Kanal deabonnieren?", color = HikariText) },
            text = {
                Text(
                    "Du bekommst dann keine neuen Videos mehr von „${target.title}“.",
                    color = HikariTextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(target.id)
                    unsubTarget = null
                }) { Text("Deabonnieren", color = HikariDanger) }
            },
            dismissButton = {
                TextButton(onClick = { unsubTarget = null }) {
                    Text("Abbrechen", color = HikariTextMuted)
                }
            },
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Search bar + add button
        item(span = { GridItemSpan(2) }) {
            SearchRow(
                query = query,
                onQueryChange = { vm.setQuery(it) },
                onClear = { vm.clearQuery() },
                onAdd = onOpenImport,
            )
        }

        if (isSearching) {
            item(span = { GridItemSpan(2) }) {
                SectionLabel(
                    when {
                        searching -> "SUCHE…"
                        searchResults.isEmpty() -> "NICHTS GEFUNDEN"
                        else -> "${searchResults.size} TREFFER"
                    },
                )
            }
            items(searchResults, key = { it.channelId }) { r ->
                SearchResultCard(
                    result = r,
                    onClick = {
                        // Tap = preview (no silent subscribe). Subscribed → open in-app.
                        if (r.subscribed) onOpenChannel(r.channelId) else openInBrowser(r.channelUrl)
                    },
                    onFollowToggle = {
                        if (r.subscribed) vm.remove(r.channelId) else vm.follow(r)
                    },
                )
            }
        } else {
            item(span = { GridItemSpan(2) }) {
                Column {
                    SectionLabel("ABONNIERT · ${channels.size}")
                    if (channels.isNotEmpty()) {
                        Text(
                            "Zum Deabonnieren auf „Folgst“ tippen",
                            color = HikariTextFaint,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            if (channels.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        "Suche oben nach einem Kanal-Namen.",
                        color = HikariTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                items(channels, key = { it.first.id }) { (channel, _) ->
                    ChannelBannerCard(
                        channel = channel,
                        onClick = { onOpenChannel(channel.id) },
                        onLongClick = { unsubTarget = channel },
                        topEndBadge = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Vertrauenskanal: Videos ohne KI-Pruefung sofort im Feed.
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (channel.autoApprove) HikariAmber.copy(alpha = 0.2f)
                                            else Color.Black.copy(alpha = 0.45f),
                                        )
                                        .clickable {
                                            vm.toggleTrusted(channel.id, !channel.autoApprove)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = if (channel.autoApprove) {
                                            "Vertrauenskanal aus"
                                        } else {
                                            "Vertrauenskanal an"
                                        },
                                        tint = if (channel.autoApprove) HikariAmber
                                        else Color.White.copy(alpha = 0.55f),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                FollowPill(subscribed = true, onToggle = { unsubTarget = channel })
                            }
                        },
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    SectionLabel("EMPFOHLEN")
                }
                items(recommendations, key = { it.channelId }) { rec ->
                    RecommendationBannerCard(
                        rec = rec,
                        onClick = {
                            // Tap = preview on YouTube; subscribing is the explicit pill only.
                            if (rec.subscribed) onOpenChannel(rec.channelId)
                            else openInBrowser(rec.channelUrl)
                        },
                        onFollowToggle = {
                            if (rec.subscribed) vm.remove(rec.channelId)
                            else vm.followRecommendation(rec)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(HikariSurface, RoundedCornerShape(8.dp))
                .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.size(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = HikariText, fontSize = 13.sp),
                        cursorBrush = SolidColor(HikariAmber),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "Kanal suchen…",
                                    color = HikariTextFaint,
                                    style = TextStyle(fontSize = 13.sp),
                                )
                            }
                            inner()
                        },
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(22.dp).clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Leeren",
                            tint = HikariTextFaint,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(HikariSurface)
                .border(0.5.dp, HikariBorder, CircleShape)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Direkten Video-Link einfügen",
                tint = HikariAmber,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
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
private fun ChannelBannerCard(
    channel: Channel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    topEndBadge: (@Composable () -> Unit)? = null,
) {
    val subs = formatSubs(channel.subscribers)
    val handle = channel.handle
    val subtitle = listOfNotNull(subs, handle).joinToString(" · ")
    BannerCard(
        title = channel.title,
        subtitle = subtitle.ifBlank { null },
        seed = channel.id,
        avatarText = channel.title.firstOrNull()?.uppercaseChar()?.toString(),
        bannerUrl = channel.bannerUrl,
        avatarUrl = channel.thumbnail,
        onClick = onClick,
        onLongClick = onLongClick,
        topEndBadge = topEndBadge,
    )
}

@Composable
private fun SearchResultCard(
    result: ChannelSearchResultDto,
    onClick: () -> Unit,
    onFollowToggle: () -> Unit,
) {
    val subs = formatSubs(result.subscribers)
    val parts = listOfNotNull(subs, result.handle)
    BannerCard(
        title = result.title,
        subtitle = parts.joinToString(" · ").ifBlank { null },
        seed = result.channelId,
        avatarText = result.title.firstOrNull()?.uppercaseChar()?.toString(),
        bannerUrl = null,
        avatarUrl = result.thumbnail,
        onClick = onClick,
        topEndBadge = { FollowPill(subscribed = result.subscribed, onToggle = onFollowToggle) },
    )
}

@Composable
private fun RecommendationBannerCard(
    rec: RecommendationDto,
    onClick: () -> Unit,
    onFollowToggle: () -> Unit,
) {
    val subs = formatSubs(rec.subscribers)
    val tagPart = if (rec.matchingTags.isNotEmpty()) "passt zu: ${rec.matchingTags.first()}" else null
    val parts = listOfNotNull(subs, tagPart)
    BannerCard(
        title = rec.title,
        subtitle = parts.joinToString(" · ").ifBlank { null },
        seed = rec.channelId,
        avatarText = rec.title.firstOrNull()?.uppercaseChar()?.toString(),
        bannerUrl = rec.banner,
        avatarUrl = rec.thumbnail,
        onClick = onClick,
        topEndBadge = { FollowPill(subscribed = rec.subscribed, onToggle = onFollowToggle) },
    )
}

/** Explicit follow toggle pill, shown top-right on search/recommendation cards. */
@Composable
private fun FollowPill(subscribed: Boolean, onToggle: () -> Unit) {
    val bg = if (subscribed) Color.Black.copy(alpha = 0.55f) else HikariAmber
    val fg = if (subscribed) HikariText else Color.Black
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (subscribed) Modifier.border(0.5.dp, HikariBorder, RoundedCornerShape(20.dp))
                else Modifier,
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            if (subscribed) Icons.Default.Check else Icons.Default.Add,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(12.dp),
        )
        Text(
            if (subscribed) "Folgst" else "Folgen",
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatSubs(n: Long?): String? {
    if (n == null) return null
    return when {
        n >= 1_000_000 -> {
            val m = n / 1_000_000.0
            if (m >= 10.0) "${m.toLong()}M" else "%.1fM".format(m)
        }
        n >= 1_000 -> "${n / 1000}K"
        else -> n.toString()
    } + " Subs"
}
