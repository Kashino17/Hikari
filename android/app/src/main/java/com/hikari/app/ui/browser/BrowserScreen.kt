package com.hikari.app.ui.browser

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.PendingImportDto
import com.hikari.app.domain.browser.AdBlocker
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
    var addressField by remember { mutableStateOf(TextFieldValue(START_URL)) }
    var addressFocused by remember { mutableStateOf(false) }
    var showBasket by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showShield by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloadsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val shieldSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Der Interceptor meldet Funde nicht selbst — er läuft auf einem
    // Hintergrund-Thread. Kurzes Nachfassen hält die Anzeige aktuell und
    // treibt zugleich den Auto-Durchlauf voran.
    LaunchedEffect(Unit) {
        while (true) {
            // Beim Laden/Durchlauf zügig, sonst ruhig — jede Runde kostet
            // eine Neuzusammensetzung der Leiste.
            delay(if (ui.loading || ui.crawl != null) 700 else 1500)
            vm.refreshFindings()
        }
    }

    LaunchedEffect(Unit) { vm.startDownloadPolling() }

    LaunchedEffect(Unit) {
        vm.navigate.collect { url ->
            addressField = TextFieldValue(url)
            webView?.loadUrl(url)
        }
    }

    // Die Adresszeile folgt der echten Seite (Links, Weiterleitungen, Zurück) —
    // nur nicht, solange der Nutzer gerade selbst tippt.
    LaunchedEffect(ui.currentUrl) {
        if (!addressFocused && ui.currentUrl.isNotBlank() && ui.currentUrl != addressField.text) {
            addressField = TextFieldValue(ui.currentUrl)
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
            shieldOn = ui.shieldEnabled,
            blocked = ui.blockedCount,
            activeDownloads = ui.transfers.count { it.status != "failed" },
            onValueChange = { addressField = it },
            onFocusChange = { focused ->
                addressFocused = focused
                // Wie im Browser: Antippen markiert die ganze Adresse.
                if (focused) addressField = addressField.copy(selection = TextRange(0, addressField.text.length))
            },
            onOpenShield = { showShield = true },
            onOpenDownloads = {
                vm.loadHistory()
                showDownloads = true
            },
            onGo = {
                val url = normalizeUrl(addressField.text)
                addressField = TextFieldValue(url)
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
                        // Neue Fenster (window.open, target=_blank) laufen über
                        // onCreateWindow, wo der Blocker sie prüft; ohne diese
                        // Einstellung würden sie stumm verschluckt oder
                        // ungeprüft geöffnet.
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = false

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message,
                            ): Boolean {
                                if (vm.adBlocker.shouldBlockPopup(null, isUserGesture)) return false
                                // Erlaubtes Pop-up (echter Klick): im selben Tab laden
                                // statt ein Fenster zu stapeln — Ad-Ziele fängt der
                                // Blocker erst hier ab, weil die URL vorher fehlt.
                                val carrier = WebView(view.context)
                                carrier.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val target = request.url.toString()
                                        if (!vm.adBlocker.shouldBlockPopup(target, true) &&
                                            !vm.adBlocker.shouldBlockNavigation(target, true, false)
                                        ) {
                                            view.post { view.loadUrl(target) }
                                        }
                                        v.post { v.destroy() }
                                        return true
                                    }
                                }
                                (resultMsg.obj as WebView.WebViewTransport).webView = carrier
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val req = request ?: return false
                                if (!req.isForMainFrame) return false
                                val target = req.url.toString()
                                // true = blockiert: Ad-Weiterleitungen ohne Klick
                                // und App-Sprünge (intent://, market://).
                                return vm.adBlocker.shouldBlockNavigation(
                                    target,
                                    req.hasGesture(),
                                    target == vm.intendedNavigation,
                                )
                            }

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
                                    if (vm.adBlocker.shouldBlockRequest(
                                            url,
                                            request.isForMainFrame,
                                            request.hasGesture() || url == vm.intendedNavigation,
                                        )
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
                                if (vm.adBlocker.enabled) view?.evaluateJavascript(AdBlocker.COSMETIC_SCRIPT, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val page = url ?: return
                                vm.onPageFinished(
                                    page,
                                    PageTitleFilter.clean(view?.title).orEmpty(),
                                    view?.canGoBack() == true,
                                )
                                if (vm.adBlocker.enabled) view?.evaluateJavascript(AdBlocker.COSMETIC_SCRIPT, null)
                                view?.evaluateJavascript(PageScripts.AUTOPLAY, null)
                                view?.evaluateJavascript(PageScripts.SCAN) { raw ->
                                    parseScan(raw)?.let { scan ->
                                        vm.onPageScanned(
                                            scan.url.ifBlank { page },
                                            scan.title,
                                            scan.videos,
                                            scan.links,
                                            scan.description,
                                            scan.meta,
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
                onItemSeason = vm::setItemSeason,
                onItemEpisode = vm::setItemEpisode,
                onRemove = vm::removeFromBasket,
                onClear = vm::clearBasket,
                onSubmit = {
                    vm.submit()
                    showBasket = false
                    // Der Fortschritt erscheint direkt hier im Browser — kein
                    // Wechsel in die Kanalansicht.
                    showDownloads = true
                    onSubmitted()
                },
            )
        }
    }

    if (showDownloads) {
        ModalBottomSheet(onDismissRequest = { showDownloads = false }, sheetState = downloadsSheetState) {
            DownloadsSheet(
                transfers = ui.transfers,
                history = ui.history,
                onRetry = vm::retryTransfer,
                onDismiss = vm::dismissTransfer,
            )
        }
    }

    if (showShield) {
        ModalBottomSheet(onDismissRequest = { showShield = false }, sheetState = shieldSheetState) {
            ShieldSheet(
                enabled = ui.shieldEnabled,
                blocked = ui.blockedCount,
                onToggle = vm::setShield,
                onReload = { webView?.reload() },
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
    value: TextFieldValue,
    loading: Boolean,
    shieldOn: Boolean,
    blocked: Int,
    activeDownloads: Int,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onOpenShield: () -> Unit,
    onOpenDownloads: () -> Unit,
    onGo: () -> Unit,
    onClose: () -> Unit,
    onReload: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val pill = MaterialTheme.colorScheme.surfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.ArrowBack, "Schließen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(
            Modifier
                .weight(1f)
                .height(40.dp)
                .background(pill, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (editing) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = { new ->
                        // Teilmarkierung aus Doppeltipp/Langdruck -> ganze URL.
                        val newlyPartial = value.selection.collapsed && !new.selection.collapsed &&
                            new.text == value.text && new.selection.length < new.text.length
                        onValueChange(
                            if (newlyPartial) new.copy(selection = TextRange(0, new.text.length)) else new,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            // onFocusChanged feuert schon beim Einblenden mit
                            // "nicht fokussiert" — das darf das Feld nicht
                            // sofort wieder schließen (Eingabe war unmöglich).
                            if (it.isFocused) hadFocus = true
                            if (hadFocus) onFocusChange(it.isFocused)
                            if (hadFocus && !it.isFocused) {
                                hadFocus = false
                                editing = false
                            }
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        onGo()
                        focusManager.clearFocus()
                    }),
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                if (value.text.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        "Löschen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(18.dp)
                            .clickable { onValueChange(TextFieldValue("")) },
                    )
                }
            } else {
                Text(
                    displayHost(value.text),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = true },
                )
            }
        }

        IconButton(onClick = onOpenShield, modifier = Modifier.size(40.dp)) {
            BadgedBox(badge = {
                if (shieldOn && blocked > 0) {
                    Badge { Text(if (blocked > 99) "99+" else blocked.toString(), fontSize = 9.sp) }
                }
            }) {
                Icon(
                    Icons.Outlined.Shield,
                    "Werbeschutz",
                    modifier = Modifier.size(22.dp),
                    tint = if (shieldOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        IconButton(onClick = onOpenDownloads, modifier = Modifier.size(40.dp)) {
            BadgedBox(badge = {
                if (activeDownloads > 0) Badge { Text(activeDownloads.toString(), fontSize = 9.sp) }
            }) {
                Icon(
                    Icons.Default.Download,
                    "Downloads",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onReload, enabled = !loading, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Refresh,
                "Neu laden",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ohne Fokus zeigt die Leiste nur den Host ("aniworld.to") — ruhiger, wie im Browser. */
internal fun displayHost(url: String): String {
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.")
    return host?.takeIf { it.isNotBlank() } ?: url
}

@Composable
private fun ShieldSheet(
    enabled: Boolean,
    blocked: Int,
    onToggle: (Boolean) -> Unit,
    onReload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Werbeschutz",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (enabled) "$blocked auf dieser Seite blockiert" else "Aus — Werbung und Pop-ups laufen durch",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = {
                onToggle(it)
                onReload()
            })
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Blockiert Werbe- und Tracker-Hosts, Pop-ups und Pop-unders ohne Klick, " +
                "Weiterleitungen in andere Apps und unsichtbare Klick-Overlays. " +
                "Videostreams bleiben davon unberührt.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Downloadliste im Browser: laufende oben mit Fortschritt, darunter der Verlauf. */
@Composable
private fun DownloadsSheet(
    transfers: List<PendingImportDto>,
    history: List<ChannelVideoDto>,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 20.dp),
    ) {
        item {
            Text(
                "Downloads",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (transfers.isEmpty() && history.isEmpty()) {
            item {
                Text(
                    "Noch keine Downloads.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(transfers, key = { "t_" + it.id }) { t -> TransferItem(t, onRetry, onDismiss) }
        if (history.isNotEmpty()) {
            item {
                Text(
                    "Verlauf",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(history, key = { "h_" + it.videoId }) { v ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        v.title,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun TransferItem(
    t: PendingImportDto,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val failed = t.status == "failed"
    val title = t.title?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(
            t.seriesTitle?.takeIf { it.isNotBlank() },
            t.season?.let { "S$it" },
            t.episode?.let { "E$it" },
        ).joinToString(" ").ifBlank { t.pageUrl }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val pct = t.progress?.let { "${(it * 100).toInt()} %" }
            Text(
                when {
                    failed -> "Fehler"
                    t.status == "queued" -> "Wartet"
                    else -> pct ?: "…"
                },
                fontSize = 12.sp,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (failed) {
                IconButton(onClick = { onRetry(t.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Neu versuchen", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { onDismiss(t.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Entfernen", modifier = Modifier.size(16.dp))
                }
            }
        }
        if (failed) {
            Text(
                t.error ?: "Unbekannter Fehler",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.height(6.dp))
            val progress = t.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
            if (crawl.waitingForHuman) {
                "Folge ${crawl.index + 1} von ${crawl.queue.size} — Bitte Haken setzen und auf Weiter tippen"
            } else {
                "Folge ${crawl.index + 1} von ${crawl.queue.size} — ${crawl.collected} gefunden"
            },
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
    val idle = !hasFinding && episodes == 0 && ui.basket.isEmpty()

    // Ohne Fund gibt es nichts zu tun: Die Leiste schrumpft auf eine
    // Statuszeile statt zwei große Buttons dauerhaft zu zeigen.
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize()
            .padding(horizontal = 12.dp, vertical = if (idle) 6.dp else 8.dp),
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
                    hasFinding && episodes > 0 -> "Video erkannt · $episodes Folgen"
                    hasFinding -> "Video erkannt"
                    episodes > 0 -> "$episodes Folgen auf der Seite"
                    else -> "Kein Video erkannt"
                },
                fontSize = 13.sp,
                color = if (idle) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onToggleDiagnostics),
            )
            if (ui.basket.isNotEmpty()) {
                TextButton(onClick = onOpenBasket) {
                    Text("${ui.basket.size} im Korb", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (diagnosticsOpen) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${ui.inspected} Requests gesehen, ${ui.findings.size} als Video erkannt",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth().height(110.dp).verticalScroll(rememberScrollState())) {
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

        if (!idle) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCollect,
                    enabled = hasFinding && ui.crawl == null,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text("Diese Folge", fontSize = 13.sp) }

                if (episodes > 0) {
                    Button(
                        onClick = onCrawl,
                        enabled = ui.crawl == null,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("Alle $episodes", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun BasketSheet(
    ui: BrowserUiState,
    onSeriesTitle: (String) -> Unit,
    onSeason: (Int?) -> Unit,
    onItemSeason: (String, Int?) -> Unit,
    onItemEpisode: (String, Int?) -> Unit,
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
            "Serie und Staffel oben gelten für alle. Staffel und Folge kannst du pro Video darunter ändern.",
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
                            item.pageTitle.ifBlank { "Unbenannt" },
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
                    NumberField(item.season, "S", Modifier.width(58.dp)) { onItemSeason(item.pageUrl, it) }
                    Spacer(Modifier.width(6.dp))
                    NumberField(item.episode, "E", Modifier.width(66.dp)) { onItemEpisode(item.pageUrl, it) }
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

/** Kleines Zahlenfeld für Staffel/Folge; leer = unbekannt (der Server liest dann die URL). */
@Composable
private fun NumberField(value: Int?, label: String, modifier: Modifier, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { onChange(it.filter(Char::isDigit).take(4).toIntOrNull()) },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ),
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    )
}

// ---- Hilfsfunktionen ----------------------------------------------------

private data class ScanResult(
    val url: String,
    val title: String,
    val description: String?,
    val videos: List<String>,
    val links: List<com.hikari.app.domain.browser.PageLink>,
    /** Was die Seite selbst über Serie/Staffel/Folge/Titel sagt (Überschriften, JSON-LD). */
    val meta: com.hikari.app.domain.browser.PageMetaParser.PageMeta? = null,
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
            runCatching { com.hikari.app.domain.browser.PageMetaParser.fromScan(o) }.getOrNull(),
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
