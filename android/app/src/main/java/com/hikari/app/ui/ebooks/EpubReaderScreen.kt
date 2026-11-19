package com.hikari.app.ui.ebooks

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.drawToBitmap
import com.hikari.app.ui.manga.PageAnim
import com.hikari.app.ui.manga.ReadDirection
import com.hikari.app.ui.manga.TapAction
import com.hikari.app.ui.manga.tapAction
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.MainActivity
import java.io.File
import java.io.FileInputStream
import org.json.JSONArray
import org.json.JSONObject

private const val HOST = "appassets.androidplatform.net"

/** Lesefarben (Papier, Sepia, Nacht) — gleiche Werte wie reader.js. */
internal data class ReaderColors(val bg: Color, val fg: Color, val faint: Color)
internal fun readerColors(theme: String) = when (theme) {
    "sepia" -> ReaderColors(Color(0xFFF1E3C6), Color(0xFF3B2F1E), Color(0x993B2F1E))
    "nacht" -> ReaderColors(Color(0xFF111111), Color(0xFFCFCAC0), Color(0x99CFCAC0))
    else -> ReaderColors(Color(0xFFF7F3EA), Color(0xFF1E1B16), Color(0x991E1B16))
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(onBack: () -> Unit, vm: ReaderViewModel) {
    val s by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val book = s.book
    val ctx = LocalContext.current
    val colors = readerColors(settings.theme)
    var barsVisible by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) } // "aa" | "toc"
    var web by remember { mutableStateOf<WebView?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }

    fun js(code: String) = web?.evaluateJavascript(code, null)

    // Blätter-Animation: Standbilder über der WebView, die selbst sofort umblättert.
    val scope = rememberCoroutineScope()
    val turner = remember { PageTurner() }
    // Die Leser-Brücke wird nur einmal gebaut und hält diese Funktionen fest — deshalb die Einstellung
    // bei jedem Aufruf frisch lesen, nicht als Wert der ersten Darstellung.
    fun anim() = pageAnimOf(settings.pageAnim)
    fun turnMs() = if (anim() == PageAnim.BOOK) 560 else 340
    fun capture(): ImageBitmap? = web?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { v -> runCatching { v.drawToBitmap().asImageBitmap() }.getOrNull() }

    /** Standbild der aktuellen Seite legen, WebView sofort umblättern lassen; false = ging nicht weiter. */
    suspend fun prepareTurn(forward: Boolean): Boolean {
        val current = capture() ?: return false
        turner.forward = forward
        turner.drainTurned()
        if (forward) { turner.under = null; turner.top = current; turner.p.snapTo(0f) }
        else { turner.top = null; turner.under = current; turner.p.snapTo(1f) }
        turner.active = true
        js("HikariReader.turn(${if (forward) 1 else -1})")
        val moved = withTimeoutOrNull(4_000) { turner.turned.receive() } ?: false
        if (!moved) { turner.reset(); return false }
        if (!forward) {
            val prev = capture()
            if (prev == null) { turner.reset(); return false }
            turner.top = prev
        }
        return true
    }

    fun turnPage(forward: Boolean) {
        if (anim() == PageAnim.NONE) { js("HikariReader.turn(${if (forward) 1 else -1})"); return }
        if (turner.active) return
        scope.launch {
            if (prepareTurn(forward)) turner.p.animateTo(if (forward) 1f else 0f, tween(turnMs(), easing = FastOutSlowInEasing))
            turner.reset()
        }
    }

    fun finishDrag(dx: Float, vx: Float, cancelled: Boolean) {
        if (!turner.dragging) return
        turner.dragging = false
        if (anim() == PageAnim.NONE) {
            if (!cancelled && (abs(dx) > 0.12f || abs(vx) > 0.45f)) js("HikariReader.turn(${if (dx < 0) 1 else -1})")
            return
        }
        scope.launch {
            turner.prep?.join()
            if (!turner.active) return@launch
            val fwd = turner.forward
            val commit = !cancelled && if (fwd) turner.p.value > 0.33f || vx < -0.45f else turner.p.value < 0.67f || vx > 0.45f
            turner.p.animateTo(if (commit == fwd) 1f else 0f, tween((turnMs() * 0.7f).toInt(), easing = FastOutSlowInEasing))
            if (!commit) {
                // zurücknehmen: die WebView war schon umgeblättert
                turner.drainTurned()
                js("HikariReader.turn(${if (fwd) -1 else 1})")
                withTimeoutOrNull(4_000) { turner.turned.receive() }
            }
            turner.reset()
        }
    }

    fun onDrag(d: PageDrag) {
        when (d.phase) {
            "start" -> {
                if (turner.active || turner.dragging) return
                turner.dragging = true
                turner.forward = d.dir > 0
                if (anim() != PageAnim.NONE) turner.prep = scope.launch { if (!prepareTurn(d.dir > 0)) turner.dragging = false }
            }
            "move" -> if (turner.dragging && turner.active && anim() != PageAnim.NONE) {
                val f = (abs(d.dx) / 0.9f).coerceIn(0f, 1f)
                val v = if (turner.forward) (if (d.dx < 0) f else 0f) else 1f - (if (d.dx > 0) f else 0f)
                scope.launch { turner.p.snapTo(v) }
            }
            "end" -> finishDrag(d.dx, d.vx, cancelled = false)
            "cancel" -> finishDrag(0f, 0f, cancelled = true)
        }
    }

    fun onPageTap(t: PageTap) {
        if (t.edge || t.big) return // Griff am Rand oder Handballen: kein Tippen
        when (tapAction(t.x, ReadDirection.LTR, settings.tapTurn, barsVisible)) {
            TapAction.NEXT -> turnPage(true)
            TapAction.PREV -> turnPage(false)
            TapAction.MENU -> barsVisible = true
            TapAction.CLOSE_MENU -> barsVisible = false
        }
    }

    BackHandler { vm.flush(); onBack() }
    DisposableEffect(Unit) { onDispose { vm.flush() } }
    ImmersiveWhileReading(hide = !barsVisible)

    // Lautstärketasten blättern (abschaltbar).
    val volumeOn by rememberUpdatedState(settings.volumeKeys)
    val activity = ctx as? MainActivity
    DisposableEffect(activity) {
        val pager = object : MainActivity.VolumePager {
            override fun wantsVolumeKeys() = volumeOn
            override fun page(forward: Boolean) = turnPage(forward)
        }
        activity?.volumePager = pager
        onDispose { if (activity?.volumePager === pager) activity.volumePager = null }
    }

    if (book == null) {
        Box(Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
            Text(if (s.missing) "Dieses Buch ist nicht auf dem Handy." else "", color = colors.fg)
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.bg)) {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        val settingsJson = vm.settingsJson(columns)
        LaunchedEffect(settingsJson, s.ready) { if (s.ready) js("HikariReader.applySettings(${JSONObject.quote(settingsJson)})") }
        val marksJson = remember(s.bookState.highlights) {
            JSONArray(s.bookState.liveHighlights.map { JSONObject().put("id", it.id).put("cfi", it.cfi).put("color", it.color ?: "gelb") }).toString()
        }
        LaunchedEffect(marksJson, s.ready) { if (s.ready) js("HikariReader.setMarks(${JSONObject.quote(marksJson)})") }

        AndroidView(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(AColor.TRANSPARENT)
                    this.settings.javaScriptEnabled = true
                    this.settings.domStorageEnabled = true
                    this.settings.allowFileAccess = false
                    this.settings.textZoom = 100
                    webViewClient = ReaderWebClient(c, File(book.filePath))
                    // Meldungen der Leser-Seite ins Protokoll (Fehlersuche per adb logcat -s HikariReader).
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                            android.util.Log.d("HikariReader", "${m.messageLevel()}: ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                            return true
                        }
                    }
                    addJavascriptInterface(
                        ReaderBridge(
                            onBoot = {
                                main.post {
                                    val start = s.startCfi?.let(JSONObject::quote) ?: "null"
                                    evaluateJavascript(
                                        "HikariReader.open('https://$HOST/book', $start, ${JSONObject.quote(vm.settingsJson(columns))})",
                                        null,
                                    )
                                }
                            },
                            onOpened = { main.post { vm.onReady() } },
                            onLocation = { loc -> main.post { vm.onLocation(loc) } },
                            onTocLoaded = { toc -> main.post { vm.onToc(toc) } },
                            onSelected = { sel -> main.post { vm.onSelection(sel) } },
                            onPageTap = { t -> main.post { onPageTap(t) } },
                            onPageDrag = { d -> main.post { onDrag(d) } },
                            onPageTurned = { moved -> turner.turned.trySend(moved) },
                            onMarkTap = { m -> main.post { sheet = "toc" } },
                            onFailure = { msg -> android.util.Log.w("HikariReader", "Öffnen fehlgeschlagen: $msg"); main.post { vm.onError(msg) } },
                        ),
                        "HikariBridge",
                    )
                    loadUrl("https://$HOST/")
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )

        PageTurnLayer(turner, pageAnimOf(settings.pageAnim), colors.bg, Modifier.statusBarsPadding().navigationBarsPadding())

        // Auswahl: markieren, nachschlagen, kopieren
        val sel = s.selection
        if (sel != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xF0222226)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for ((name, col) in listOf("gelb" to Color(0xFFFACC15), "gruen" to Color(0xFF4ADE80), "blau" to Color(0xFF60A5FA), "rosa" to Color(0xFFF472B6))) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(col).clickable { vm.highlight(name); js("HikariReader.clearSelection()") })
                }
                IconButton(onClick = { lookUp(ctx, sel.text) }) { Icon(Icons.Outlined.Search, "Nachschlagen", tint = Color.White) }
                IconButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Zitat", sel.text))
                    js("HikariReader.clearSelection()"); vm.onSelection(null)
                }) { Icon(Icons.Outlined.ContentCopy, "Kopieren", tint = Color.White) }
            }
        }

        // Obere Leiste
        AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(colors.bg.copy(alpha = 0.97f)).statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.flush(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = colors.fg) }
                Text(book.title, color = colors.fg, fontFamily = FontFamily.Serif, fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { sheet = "toc" }) { Icon(Icons.AutoMirrored.Outlined.List, "Inhalt", tint = colors.fg) }
                val marked = s.location?.let { loc -> s.bookState.liveBookmarks.any { it.cfi == loc.cfi } } == true
                IconButton(onClick = { vm.toggleBookmark() }) {
                    Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Lesezeichen", tint = if (marked) BooksAccent else colors.fg)
                }
                IconButton(onClick = { sheet = "aa" }) { Icon(Icons.Outlined.FormatSize, "Schrift", tint = colors.fg) }
            }
        }

        // Untere Leiste: Fortschritt, Schieber, Restzeit im Kapitel
        val loc = s.location
        AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(colors.bg.copy(alpha = 0.97f)).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }
                Slider(
                    value = if (dragging) dragValue else (loc?.fraction ?: 0.0).toFloat(),
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = { dragging = false; js("HikariReader.goToFraction($dragValue)") },
                    colors = SliderDefaults.colors(thumbColor = BooksAccent, activeTrackColor = BooksAccent, inactiveTrackColor = colors.faint.copy(alpha = 0.3f)),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("${((loc?.fraction ?: 0.0) * 100).toInt()} %", color = colors.fg, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        loc?.let { l -> if (l.minutesLeftInChapter > 0) "noch ca. ${l.minutesLeftInChapter} Min. im Kapitel" else l.chapter } ?: "",
                        color = colors.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Dezente Zeile, solange die Leisten aus sind (wie beim Kindle)
        if (!barsVisible && loc != null && sel == null) {
            Text(
                "${(loc.fraction * 100).toInt()} %" + if (loc.minutesLeftInChapter > 0) "  ·  noch ${loc.minutesLeftInChapter} Min. im Kapitel" else "",
                color = colors.faint, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
            )
        }
    }

    when (sheet) {
        "aa" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color(0xFF1C1C1F)) {
            ReaderSettingsSheet(settings) { f -> vm.updateSettings(f) }
        }
        "toc" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color(0xFF1C1C1F)) {
            TocSheet(
                toc = s.toc,
                current = s.location?.chapter,
                state = s.bookState,
                onGo = { target -> js("HikariReader.goTo(${JSONObject.quote(target)})"); sheet = null; barsVisible = false },
                onDelete = { vm.deleteMark(it) },
            )
        }
    }
}

/** Systemleisten ausblenden, solange gelesen wird (Leisten des Lesers aus). */
@Composable
internal fun ImmersiveWhileReading(hide: Boolean) {
    val view = LocalView.current
    DisposableEffect(hide) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hide) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Markiertes Wort nachschlagen: Androids Textmenü (ACTION_PROCESS_TEXT), sonst Websuche. */
private fun lookUp(ctx: Context, text: String) {
    val process = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    val chooser = Intent.createChooser(process, "Nachschlagen")
    runCatching { ctx.startActivity(chooser) }.onFailure {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", text)) }
    }
}

@Composable
private fun ReaderSettingsSheet(s: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((id, label) in listOf("papier" to "Papier", "sepia" to "Sepia", "nacht" to "Nacht")) {
                val c = readerColors(id)
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.bg)
                        .border(2.dp, if (s.theme == id) BooksAccent else Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { update { it.copy(theme = id) } }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = c.fg, fontFamily = FontFamily.Serif, fontSize = 15.sp) }
            }
        }
        Choices("Schrift", listOf("literata" to "Literata", "serif" to "Serif", "sans" to "Sans"), s.font) { v -> update { it.copy(font = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Größe", color = Color(0xB3FFFFFF), fontSize = 14.sp, modifier = Modifier.weight(1f))
            StepButton("A−") { update { it.copy(size = (it.size - 1).coerceAtLeast(14)) } }
            Text("${s.size}", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
            StepButton("A+") { update { it.copy(size = (it.size + 1).coerceAtMost(28)) } }
        }
        Choices("Zeilenabstand", listOf("1.3" to "Eng", "1.5" to "Normal", "1.8" to "Weit"), s.lineHeight.toString()) { v -> update { it.copy(lineHeight = v.toDouble()) } }
        Choices("Rand", listOf("schmal" to "Schmal", "mittel" to "Mittel", "breit" to "Breit"), s.margin) { v -> update { it.copy(margin = v) } }
        Choices("Umblättern", listOf("BOOK" to "Blättern", "SLIDE" to "Gleiten", "FADE" to "Ruhig", "NONE" to "Sofort"), s.pageAnim) { v -> update { it.copy(pageAnim = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tippen zum Blättern", color = Color(0xB3FFFFFF), fontSize = 14.sp)
                Text("Nur die äußeren Ränder blättern. Halten, Rutschen und Handballen zählen nicht. Aus: nur wischen.", color = Color(0x80FFFFFF), fontSize = 12.sp)
            }
            Switch(checked = s.tapTurn, onCheckedChange = { v -> update { it.copy(tapTurn = v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = BooksAccent))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mit Lautstärketasten blättern", color = Color(0xB3FFFFFF), fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = s.volumeKeys, onCheckedChange = { v -> update { it.copy(volumeKeys = v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = BooksAccent))
        }
    }
}

@Composable
private fun Choices(label: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    Column {
        Text(label, color = Color(0xB3FFFFFF), fontSize = 14.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((v, l) in options) {
                val on = v == current
                Text(
                    l, color = if (on) Color(0xFF2A1D0E) else Color.White, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) BooksAccent else Color(0x1AFFFFFF))
                        .clickable { onPick(v) }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x1AFFFFFF)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun TocSheet(
    toc: List<TocEntry>,
    current: String?,
    state: com.hikari.app.domain.ebooks.BookState,
    onGo: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var tab by remember { mutableStateOf("inhalt") }
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in listOf("inhalt" to "Inhalt", "zeichen" to "Lesezeichen ${state.liveBookmarks.size}", "markiert" to "Markiert ${state.liveHighlights.size}")) {
                val on = id == tab
                Text(label, color = if (on) Color(0xFF2A1D0E) else Color.White, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) BooksAccent else Color(0x1AFFFFFF))
                        .clickable { tab = id }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        LazyColumn(Modifier.padding(top = 10.dp)) {
            when (tab) {
                "inhalt" -> {
                    if (toc.isEmpty()) item { Empty("Dieses Buch hat kein Inhaltsverzeichnis.") }
                    items(toc) { t ->
                        Text(
                            t.label.ifBlank { "—" },
                            color = if (t.label == current) BooksAccent else Color.White,
                            fontSize = 15.sp, fontFamily = FontFamily.Serif,
                            modifier = Modifier.fillMaxWidth().clickable { onGo(t.href) }
                                .padding(start = (20 + t.depth * 16).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
                else -> {
                    val list = if (tab == "zeichen") state.liveBookmarks else state.liveHighlights
                    if (list.isEmpty()) item { Empty(if (tab == "zeichen") "Noch keine Lesezeichen." else "Noch nichts markiert — Text lange drücken.") }
                    items(list, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth().clickable { onGo(m.cfi) }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (m.color != null) Box(Modifier.size(10.dp).clip(CircleShape).background(markColor(m.color)))
                            Text(m.text ?: m.label ?: "", color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 10.dp))
                            IconButton(onClick = { onDelete(m.id) }) { Icon(Icons.Outlined.DeleteOutline, "Löschen", tint = Color(0x99FFFFFF)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) = Text(text, color = Color(0x99FFFFFF), fontSize = 14.sp, modifier = Modifier.padding(20.dp))

private fun markColor(c: String?) = when (c) {
    "gruen" -> Color(0xFF4ADE80)
    "blau" -> Color(0xFF60A5FA)
    "rosa" -> Color(0xFFF472B6)
    else -> Color(0xFFFACC15)
}

/** Liefert die Leser-Seite aus den Assets und das Buch aus dem App-Speicher. */
private class ReaderWebClient(private val ctx: Context, private val bookFile: File) : WebViewClient() {
    // Nur fremde Web-Links im Hauptfenster abfangen. Der Leser lädt jedes Kapitel in einen Rahmen
    // mit blob:-Adresse (ohne Host) — würde das blockiert, bliebe er bei „Buch wird geöffnet …“ stehen.
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        return request.isForMainFrame && (url.scheme == "http" || url.scheme == "https") && url.host != HOST
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url: Uri = request.url
        if (url.host != HOST) return null
        val path = url.path.orEmpty()
        if (path == "/book") {
            return WebResourceResponse("application/epub+zip", null, 200, "OK", mapOf("Cache-Control" to "no-store"), FileInputStream(bookFile))
        }
        val name = "ebook-reader" + if (path == "/" || path.isEmpty()) "/index.html" else path
        if (".." in name) return WebResourceResponse("text/plain", "utf-8", 400, "Bad Request", emptyMap(), null)
        val stream = runCatching { ctx.assets.open(name) }.getOrNull()
            ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
        val ext = name.substringAfterLast('.')
        val mime = when (ext) {
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "html" -> "text/html"
            "woff2" -> "font/woff2"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        return WebResourceResponse(mime, if (mime.startsWith("text/")) "utf-8" else null, 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*"), stream)
    }
}
