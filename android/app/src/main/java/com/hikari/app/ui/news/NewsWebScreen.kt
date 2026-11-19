package com.hikari.app.ui.news

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayInputStream
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Tagesbericht im Discover-Stil (Kadir 20.11.2026, in der Tailscale-Vorschau abgesegnet):
 * eigene Stichwörter, nur frei lesbare Volltexte, alle Karten gleich groß, voller Artikel in der App.
 *
 * Oberfläche: assets/news-web (ohne Build-Schritt). Diese Datei ist die Brücke:
 *  - Dateien: https://appassets.androidplatform.net/… → Assets
 *  - Server:  …/api/… → Hikari-Server (/news/discover, /news/article, /news/keywords, /news/img)
 *  - Videos:  YouTube im Player der App, Teilen über Android.
 * Vorschau ohne App-Build: ~/Developer/hikari-mockups/profil-bereiche (news.html).
 */
private const val HOST = "appassets.androidplatform.net"

@HiltViewModel
class NewsWebViewModel @Inject constructor(val okHttp: OkHttpClient) : ViewModel()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NewsWebScreen(
    onClose: () -> Unit,
    onOpenVideo: (id: String, title: String) -> Unit,
    vm: NewsWebViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val bridge = remember { NewsWebBridge(context.applicationContext, onOpenVideo) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                // Ohne eigene LayoutParams rechnet Chromium mit Viewport-Höhe 0.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // Gleiche Grundfarbe wie die App-Hülle (#1F1F22) — keine andersfarbigen Balken.
                setBackgroundColor(Color.parseColor("#1F1F22"))
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.textZoom = 100
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(bridge, "HikariNative")
                webViewClient = NewsWebClient(ctx.applicationContext, vm.okHttp)
                bridge.webView = this
                loadUrl("https://$HOST/index.html")
            }
        },
        onRelease = { it.destroy() },
    )

    BackHandler {
        val web = bridge.webView
        if (web == null) onClose()
        else web.evaluateJavascript("(window.__hkBack && window.__hkBack()) === true") { handled -> if (handled != "true") onClose() }
    }
    DisposableEffect(Unit) { onDispose { bridge.webView = null } }
}

/** Liefert die Oberfläche aus den Assets und reicht GET-Anfragen an den Hikari-Server durch. */
private class NewsWebClient(private val app: Context, private val okHttp: OkHttpClient) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.url.host == HOST) return false
        runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return null
        val path = url.path.orEmpty().ifEmpty { "/" }
        return if (path.startsWith("/api/")) proxy(path.removePrefix("/api") + (url.encodedQuery?.let { "?$it" } ?: ""), request)
        else asset("news-web${if (path == "/") "/index.html" else path}")
    }

    private fun asset(name: String): WebResourceResponse {
        if (".." in name) return status(400, "Bad Request")
        val stream = runCatching { app.assets.open(name) }.getOrNull() ?: return status(404, "Not Found")
        val mime = when (name.substringAfterLast('.', "")) {
            "html" -> "text/html"
            "js" -> "text/javascript"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
        return WebResourceResponse(mime, "utf-8", 200, "OK", mapOf("Cache-Control" to "no-cache"), stream)
    }

    private fun proxy(pathAndQuery: String, request: WebResourceRequest): WebResourceResponse {
        if (request.method != "GET") return status(405, "Method Not Allowed")
        return try {
            // Der Host ist ein Platzhalter — der OkHttp-Client der App schreibt ihn auf die eingestellte Server-Adresse um.
            val res = okHttp.newCall(Request.Builder().url("http://hikari.invalid$pathAndQuery").build()).execute()
            val type = res.header("Content-Type").orEmpty()
            WebResourceResponse(
                type.substringBefore(';').ifBlank { "application/octet-stream" },
                if ("json" in type || type.startsWith("text/")) "utf-8" else null,
                res.code.takeIf { it in 200..299 || it in 400..599 } ?: 502,
                res.message.ifBlank { "OK" },
                mapOf("Cache-Control" to "no-store"),
                res.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0)),
            )
        } catch (e: Exception) {
            WebResourceResponse("application/json", "utf-8", 502, "Bad Gateway", mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream("""{"error":"backend_unreachable"}""".toByteArray()))
        }
    }

    private fun status(code: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/** `window.HikariNative` im Tagesbericht. Die Methoden kommen auf einem Hintergrund-Thread an. */
class NewsWebBridge(
    private val app: Context,
    private val onOpenVideo: (String, String) -> Unit,
) {
    var webView: WebView? = null
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openVideo(id: String, title: String) {
        main.post { onOpenVideo(id, title) }
    }

    @JavascriptInterface
    fun share(title: String, url: String) {
        main.post {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            }
            runCatching {
                app.startActivity(Intent.createChooser(send, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
