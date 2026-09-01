package com.hikari.app.ui.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hikari.app.domain.browser.AdHosts
import com.hikari.app.domain.browser.PageScripts
import com.hikari.app.domain.browser.PageTitleFilter
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONTokener

private const val START_URL = "https://www.google.com"

/**
 * In-App-Browser mit Stream-Erkennung.
 *
 * Der Nutzer surft ganz normal; im Hintergrund liest der Interceptor jeden
 * Request der Seite mit. Sobald der Player anläuft, kennt die App die echte
 * Medien-URL — samt Referer und Cookie, die der Server später zum Laden
 * braucht. Der Umweg über einen echten Browser ist der Grund, warum das auch
 * bei Hostern funktioniert, für die es keinen funktionierenden Extraktor gibt:
 * Die Seite entschlüsselt ihren Stream selbst, wir schauen nur zu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onClose: () -> Unit,
    onSubmitted: () -> Unit = {},
    vm: BrowserViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var addressField by remember { mutableStateOf(START_URL) }
    var showBasket by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Der Interceptor meldet Funde nicht selbst — er läuft auf einem
    // Hintergrund-Thread. Kurzes Nachfassen hält die Anzeige aktuell und
    // treibt zugleich den Auto-Durchlauf voran.
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            vm.refreshFindings()
        }
    }

    LaunchedEffect(Unit) {
        vm.navigate.collect { url ->
            addressField = url
            webView?.loadUrl(url)
        }
    }

    BackHandler(enabled = ui.canGoBack) { webView?.goBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {

        AddressBar(
            value = addressField,
            loading = ui.loading,
            onValueChange = { addressField = it },
            onGo = {
                val url = normalizeUrl(addressField)
                addressField = url
                // Als bewusst angesteuert merken, sonst blockiert der
                // Ad-Schutz auch einen absichtlichen Besuch dieser Domain.
                vm.onAddressBarGo(url)
                webView?.loadUrl(url)
            },
            onClose = onClose,
            onReload = { webView?.reload() },
        )

        if (ui.crawl != null) CrawlBanner(ui.crawl!!, onStop = vm::stopCrawl)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        // Ohne das startet kein Player von selbst — und ohne
                        // laufenden Player gibt es keinen Stream mitzulesen.
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // Der Standard-WebView-Kennung steht ein "wv" im
                        // User-Agent, an dem etliche Seiten den eingebetteten
                        // Browser erkennen und abweisen. Ohne das verhalten sie
                        // sich wie gegenüber Chrome.
                        settings.userAgentString = settings.userAgentString
                            ?.replace(" wv", "")
                            ?.replace("Version/4.0 ", "")
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                // Läuft für JEDEN Subrequest der Seite —
                                // XHR, fetch, Media. Genau hier taucht die
                                // Stream-URL auf, sobald der Player startet.
                                val url = request?.url?.toString()
                                if (url != null) {
                                    // Ad-/Tracker-Hosts werden ganz blockiert:
                                    // Ihre Subrequests braucht niemand, und ein
                                    // Ad-Redirect ins Hauptfenster (z. B.
                                    // s.lazada.co.th/s.…) würde die eigentliche
                                    // Seite verdrängen. Durchgelassen wird ein
                                    // Hauptframe-Aufruf nur mit Nutzer-Geste
                                    // (angeklickter Link) oder wenn die URL
                                    // bewusst angesteuert wurde (Adressleiste,
                                    // Auto-Durchlauf) — letzteres darüber, dass
                                    // das ViewModel sie als intendedNavigation
                                    // hält. Die Unterscheidung "absichtlich vs.
                                    // automatisch" ist damit nur näherungsweise
                                    // möglich; die einfachere Variante wurde
                                    // gewählt, weil shouldOverrideUrlLoading
                                    // bewusst nicht gesetzt ist.
                                    if (AdHosts.isAdUrl(url) &&
                                        !(request.isForMainFrame &&
                                            (request.hasGesture() || url == vm.intendedNavigation))
                                    ) {
                                        return emptyResponse()
                                    }
                                    val headers = HashMap(request.requestHeaders ?: emptyMap())
                                    // Den Cookie setzt der Netzwerk-Stack erst
                                    // nach diesem Aufruf, er fehlt hier also
                                    // meistens — ohne ihn verweigert der Hoster
                                    // den späteren Serverdownload. Der
                                    // CookieManager kennt ihn bereits.
                                    if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
                                        CookieManager.getInstance().getCookie(url)
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { headers["Cookie"] = it }
                                    }
                                    vm.sniffer.onRequest(url, headers)
                                }
                                return null // nichts ersetzen, nur mitlesen
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                url?.let { vm.onPageStarted(it) }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val page = url ?: return
                                vm.onPageFinished(
                                    page,
                                    PageTitleFilter.clean(view?.title).orEmpty(),
                                    view?.canGoBack() == true,
                                )
                                view?.evaluateJavascript(PageScripts.AUTOPLAY, null)
                                view?.evaluateJavascript(PageScripts.SCAN) { raw ->
                                    parseScan(raw)?.let { scan ->
                                        vm.onPageScanned(
                                            scan.url.ifBlank { page },
                                            scan.title,
                                            scan.videos,
                                            scan.links,
                                            scan.description,
                                        )
                                    }
                                }
                            }
                        }
                        loadUrl(START_URL)
                        webView = this
                    }
                },
            )

            if (ui.loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        FindingBar(
            ui = ui,
            diagnosticsOpen = showDiagnostics,
            onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
            onCollect = { vm.collectCurrent() },
            onCrawl = { vm.startCrawl(ui.episodeLinks) },
            onOpenBasket = { showBasket = true },
        )
    }

    if (showBasket) {
        ModalBottomSheet(onDismissRequest = { showBasket = false }, sheetState = sheetState) {
            BasketSheet(
                ui = ui,
                onSeriesTitle = vm::setSeriesTitle,
                onSeason = vm::setSeason,
                onRemove = vm::removeFromBasket,
                onClear = vm::clearBasket,
                onSubmit = {
                    vm.submit()
                    showBasket = false
                    onSubmitted()
                },
            )
        }
    }

    ui.message?.let { msg ->
        LaunchedEffect(msg) {
            delay(4000)
            vm.dismissMessage()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg,
                Modifier
                    .padding(bottom = 96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun AddressBar(
    value: String,
    loading: Boolean,
    onValueChange: (String) -> Unit,
    onGo: () -> Unit,
    onClose: () -> Unit,
    onReload: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.ArrowBack, "Schließen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = { Text("Adresse oder Suche", fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            shape = RoundedCornerShape(10.dp),
        )
        IconButton(onClick = onReload, enabled = !loading) {
            Icon(Icons.Default.Refresh, "Neu laden", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CrawlBanner(crawl: CrawlState, onStop: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Folge ${crawl.index + 1} von ${crawl.queue.size} — ${crawl.collected} gefunden",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onStop) { Text("Stoppen", fontSize = 13.sp) }
    }
}

/**
 * Die Leiste am unteren Rand: zeigt an, was auf dieser Seite gefunden wurde,
 * und bietet die beiden Aktionen an — diese eine Folge, oder alle auf einmal.
 */
@Composable
private fun FindingBar(
    ui: BrowserUiState,
    diagnosticsOpen: Boolean,
    onToggleDiagnostics: () -> Unit,
    onCollect: () -> Unit,
    onCrawl: () -> Unit,
    onOpenBasket: () -> Unit,
) {
    val hasFinding = ui.findings.isNotEmpty()
    val episodes = ui.episodeLinks.size

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (hasFinding) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(4.dp),
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    hasFinding && episodes > 0 -> "Video erkannt · $episodes Folgen auf der Seite"
                    hasFinding -> "Video erkannt"
                    episodes > 0 -> "$episodes Folgen auf der Seite"
                    else -> "Kein Video erkannt — Wiedergabe starten"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onToggleDiagnostics),
            )
            // Antippbarer Zähler: Steht hier 0, läuft der Interceptor nicht.
            // Steht hier eine große Zahl ohne Fund, ist der Filter zu streng.
            Text(
                "${ui.inspected}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onToggleDiagnostics)
                    .padding(horizontal = 8.dp),
            )
            if (ui.basket.isNotEmpty()) {
                TextButton(onClick = onOpenBasket) {
                    Text("${ui.basket.size} im Korb", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (diagnosticsOpen) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${ui.inspected} Requests gesehen, ${ui.findings.size} als Video erkannt",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())) {
                for (u in ui.recentUrls.take(20)) {
                    Text(
                        u,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCollect,
                enabled = hasFinding && ui.crawl == null,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Diese Folge", fontSize = 13.sp) }

            Button(
                onClick = onCrawl,
                enabled = episodes > 0 && ui.crawl == null,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text("Alle $episodes", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun BasketSheet(
    ui: BrowserUiState,
    onSeriesTitle: (String) -> Unit,
    onSeason: (Int?) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(
            "${ui.basket.size} Videos bereit",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Serie und Staffel gelten für alle — die Folgennummer kommt aus dem Link.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.seriesTitle,
                onValueChange = onSeriesTitle,
                label = { Text("Serie", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = ui.season?.toString().orEmpty(),
                onValueChange = { onSeason(it.toIntOrNull()) },
                label = { Text("Staffel", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
            items(ui.basket, key = { it.pageUrl }) { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.episode?.let { "Folge $it" } ?: item.pageTitle.ifBlank { "Unbenannt" },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${item.finding.kind.name} · ${item.pageUrl}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onRemove(item.pageUrl) }) {
                        Icon(
                            Icons.Default.Close,
                            "Entfernen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text("Leeren", fontSize = 13.sp)
            }
            Button(
                onClick = onSubmit,
                enabled = ui.basket.isNotEmpty() && !ui.submitting,
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (ui.submitting) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Herunterladen", fontSize = 13.sp)
                }
            }
        }
    }
}

// ---- Hilfsfunktionen ----------------------------------------------------

private data class ScanResult(
    val url: String,
    val title: String,
    val description: String?,
    val videos: List<String>,
    val links: List<com.hikari.app.domain.browser.PageLink>,
)

/**
 * `evaluateJavascript` liefert das Ergebnis als JSON-Literal — unser JSON
 * steckt also als String IN einem JSON-Wert und muss zweimal ausgepackt werden.
 */
private fun parseScan(raw: String?): ScanResult? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching {
        val inner = JSONTokener(raw).nextValue() as? String ?: return null
        val o = JSONObject(inner)
        val videos = o.optJSONArray("videos")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()
        val links = o.optJSONArray("links")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val l = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = l.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
                com.hikari.app.domain.browser.PageLink(url, l.optString("label"))
            }
        }.orEmpty()
        ScanResult(
            o.optString("url"),
            o.optString("title"),
            BrowserViewModel.cleanDescription(o.optString("description")),
            videos,
            links,
        )
    }.getOrNull()
}

/** Antwort-Ersatz für blockierte Ad-/Tracker-Requests: leer, aber gültig. */
private fun emptyResponse() =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

/** Adressleisten-Eingabe: URL übernehmen, alles andere als Suche behandeln. */
internal fun normalizeUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return START_URL
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    // Sieht es wie ein Hostname aus (Punkt, kein Leerzeichen), dann als URL.
    if (t.contains('.') && !t.contains(' ')) return "https://$t"
    return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8")
}
