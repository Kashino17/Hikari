package com.hikari.app.ui.tasks

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.tasks.TaskCapture
import com.hikari.app.tasks.TaskCaptureActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Aufgaben (Kadir 21.11.2026, Entwurf über die Tailscale-Vorschau abgesegnet).
 *
 * Oberfläche: assets/tasks-web (ohne Build-Schritt), Vorschau: backend/scripts/tasks-preview.ts.
 * Zwei Einsätze derselben Seite:
 *  - page = null: Reiter »Aufgaben« im Profil (Zeit-Karte, Fristen, zwei Knöpfe)
 *  - page = "kinds" | "areas" | "kind:…" | "focus:…" | "new:<Aufnahme>" …: Unterseite als eigener Bildschirm
 * Brücke: GET /api/… wird durchgereicht, Änderungen laufen über HikariNative.request (POST-Körper
 * kommen in shouldInterceptRequest nicht an), Mikrofon öffnet das Aufnahme-Fenster.
 */
private const val HOST = "appassets.androidplatform.net"

@HiltViewModel
class TasksWebViewModel @Inject constructor(val okHttp: OkHttpClient) : ViewModel()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TasksWebScreen(
    page: String?,
    onOpen: (page: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    vm: TasksWebViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val bridge = remember { TasksBridge(context.applicationContext, vm.okHttp, onOpen, onClose) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // Ohne eigene LayoutParams rechnet Chromium mit Viewport-Höhe 0.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor("#1F1F22"))
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.textZoom = 100
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(bridge, "HikariNative")
                webViewClient = TasksWebClient(ctx.applicationContext, vm.okHttp)
                bridge.webView = this
                val q = page?.let { "?page=" + Uri.encode(it) }.orEmpty()
                loadUrl("https://$HOST/index.html$q")
            }
        },
        onRelease = { it.destroy() },
    )

    // Zurück: erst offene Fenster und Unterseiten in der Oberfläche schließen.
    BackHandler(enabled = page != null) {
        val web = bridge.webView
        if (web == null) onClose()
        else web.evaluateJavascript("(window.__hkBack && window.__hkBack()) === true") { handled -> if (handled != "true") onClose() }
    }

    // Neu laden, wenn man zurückkommt oder eine Aufnahme fertig sortiert ist.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var first = true
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                if (!first) bridge.refresh()
                first = false
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); bridge.webView = null }
    }
    LaunchedEffect(Unit) { TaskCapture.changes.collect { bridge.refresh() } }
}

private class TasksWebClient(private val app: Context, private val okHttp: OkHttpClient) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.host != HOST

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return null
        val path = url.path.orEmpty().ifEmpty { "/" }
        return if (path.startsWith("/api/")) proxy(path.removePrefix("/api") + (url.encodedQuery?.let { "?$it" } ?: ""), request)
        else asset("tasks-web${if (path == "/") "/index.html" else path}")
    }

    private fun asset(name: String): WebResourceResponse {
        if (".." in name) return status(400)
        val stream = runCatching { app.assets.open(name) }.getOrNull() ?: return status(404)
        val mime = when (name.substringAfterLast('.', "")) {
            "html" -> "text/html"; "js" -> "text/javascript"; "css" -> "text/css"; "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
        return WebResourceResponse(mime, "utf-8", 200, "OK", mapOf("Cache-Control" to "no-cache"), stream)
    }

    private fun proxy(pathAndQuery: String, request: WebResourceRequest): WebResourceResponse {
        if (request.method != "GET") return status(405)
        return try {
            val res = okHttp.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url("http://hikari.invalid$pathAndQuery").build()).execute()
            val type = res.header("Content-Type").orEmpty()
            WebResourceResponse(
                type.substringBefore(';').ifBlank { "application/json" }, "utf-8",
                res.code.takeIf { it in 200..299 || it in 400..599 } ?: 502, res.message.ifBlank { "OK" },
                mapOf("Cache-Control" to "no-store"),
                res.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0)),
            )
        } catch (e: Exception) {
            WebResourceResponse("application/json", "utf-8", 502, "Bad Gateway", mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream("""{"error":"backend_unreachable"}""".toByteArray()))
        }
    }

    private fun status(code: Int) = WebResourceResponse("text/plain", "utf-8", code, "Error", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/** `window.HikariNative` der Aufgaben. Methoden kommen auf einem Hintergrund-Thread an. */
class TasksBridge(
    private val app: Context,
    private val okHttp: OkHttpClient,
    private val onOpen: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    var webView: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val http by lazy { okHttp.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    fun refresh() = main.post { webView?.evaluateJavascript("window.__hkRefresh && window.__hkRefresh()", null) }

    /** POST/PATCH/DELETE an den Server; Antwort kommt über window.__hkResp(id, status, text). */
    @JavascriptInterface
    fun request(id: Int, method: String, path: String, body: String) {
        io.execute {
            val (code, text) = try {
                val reqBody = if (method == "GET" || method == "DELETE" && body.isEmpty()) null
                else body.ifEmpty { "{}" }.toRequestBody("application/json".toMediaType())
                http.newCall(Request.Builder().url("http://hikari.invalid$path").method(method, reqBody).build()).execute()
                    .use { r -> r.code to r.body?.string().orEmpty() }
            } catch (e: Exception) {
                0 to ""
            }
            main.post {
                webView?.evaluateJavascript("window.__hkResp($id, $code, ${JSONObject.quote(text)})", null)
            }
        }
    }

    @JavascriptInterface
    fun open(page: String) { main.post { onOpen(page) } }

    @JavascriptInterface
    fun close() { main.post { onClose() } }

    /** Mikrofon: dasselbe Aufnahme-Fenster wie Kachel, Widget und Seitentaste. */
    @JavascriptInterface
    fun record() {
        main.post { app.startActivity(Intent(app, TaskCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    @JavascriptInterface
    fun captureText(text: String) { TaskCapture.enqueueText(app, text, "text") }

    /** Nach jeder Änderung: Widget nachziehen. */
    @JavascriptInterface
    fun changed() { com.hikari.app.tasks.TasksWidget.refresh(app) }
}
