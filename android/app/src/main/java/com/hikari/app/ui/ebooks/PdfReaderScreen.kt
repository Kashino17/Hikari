package com.hikari.app.ui.ebooks

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** PdfRenderer ist nicht threadsicher: alles über einen eigenen Faden, Bilder im kleinen Zwischenspeicher. */
private class PdfPages(file: File) : AutoCloseable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val executor = Executors.newSingleThreadExecutor()
    val dispatcher = executor.asCoroutineDispatcher()
    private val cache = LruCache<Int, Bitmap>(6)
    val count: Int = renderer.pageCount
    private val ratios = arrayOfNulls<Float>(count)

    /** Seitenverhältnis erst bei Bedarf messen — bei 600 Seiten nicht alle beim Öffnen. */
    suspend fun ratio(index: Int): Float = withContext(dispatcher) {
        ratios[index] ?: renderer.openPage(index).use { it.width.toFloat() / it.height.coerceAtLeast(1) }.also { ratios[index] = it }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(dispatcher) {
        cache.get(index)?.takeIf { it.width == widthPx } ?: renderer.openPage(index).use { p ->
            val h = (widthPx / (p.width.toFloat() / p.height)).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache.put(index, bmp)
            bmp
        }
    }

    override fun close() {
        executor.execute {
            runCatching { renderer.close() }
            runCatching { fd.close() }
        }
        executor.shutdown()
    }
}

@Composable
fun PdfReaderScreen(onBack: () -> Unit, vm: ReaderViewModel) {
    // Beim Lesen soll das Handy nicht abdunkeln oder ausgehen.
    com.hikari.app.ui.player.KeepScreenOn()
    val s by vm.state.collectAsState()
    val book = s.book ?: run {
        Box(Modifier.fillMaxSize().background(Color(0xFF1F1F22)), contentAlignment = Alignment.Center) {
            Text(if (s.missing) "Dieses Buch ist nicht auf dem Handy." else "", color = Color.White)
        }
        return
    }
    // Öffnen abseits des Haupt-Threads; null = lädt noch, Result.failure = kaputt.
    val opened by produceState<Result<PdfPages>?>(null, book.filePath) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { PdfPages(File(book.filePath)) } }
    }
    DisposableEffect(opened) { onDispose { opened?.getOrNull()?.close() } }
    BackHandler { vm.flush(); onBack() }
    DisposableEffect(Unit) { onDispose { vm.flush() } }
    val pages = opened?.getOrNull()
    if (pages == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1F1F22)), contentAlignment = Alignment.Center) {
            Text(if (opened == null) "PDF wird geöffnet …" else "Dieses PDF lässt sich nicht öffnen.", color = Color.White)
        }
        return
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = s.startPage.coerceIn(0, (pages.count - 1).coerceAtLeast(0)))
    val current by remember { derivedStateOf { list.firstVisibleItemIndex } }
    LaunchedEffect(current) { vm.onPdfPage(current, pages.count) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var jump by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val readerSettings by vm.settings.collectAsState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Color(0xFF1F1F22))) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.flush(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = Color.White) }
            PdfBadge()
            Text(book.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            TextButton(onClick = { jump = true }) { Text("Seite ${current + 1} von ${pages.count}", color = BooksAccent, fontSize = 13.sp) }
            val marked = s.bookState.liveBookmarks.any { it.cfi == "page:$current" }
            IconButton(onClick = { vm.pdfBookmark(current) }) {
                Icon(if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Lesezeichen", tint = if (marked) BooksAccent else Color.White)
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset = if (scale == 1f) Offset.Zero else Offset(offset.x + pan.x, offset.y)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2f })
                },
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt().coerceAtMost(2000)
            LazyColumn(
                state = list,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x),
            ) {
                items(pages.count) { i ->
                    val bmp by produceState<Bitmap?>(null, i, widthPx) { value = runCatching { pages.render(i, widthPx) }.getOrNull() }
                    val ratio by produceState(0.707f, i) { value = runCatching { pages.ratio(i) }.getOrDefault(0.707f) }
                    Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.3f, 3f)).background(Color.White)) {
                        bmp?.let { Image(it.asImageBitmap(), "Seite ${i + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }
    // Blaulichtfilter zuoberst (wie im Buch- und Manga-Leser).
    com.hikari.app.ui.reader.BlueLightFilter(readerSettings.blueFilter)
    }
    if (jump) {
        var text by remember { mutableStateOf("${current + 1}") }
        AlertDialog(
            onDismissRequest = { jump = false },
            title = { Text("Zu Seite springen") },
            text = { OutlinedTextField(text, { text = it.filter(Char::isDigit) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = {
                TextButton(onClick = {
                    jump = false
                    val p = (text.toIntOrNull() ?: 1).coerceIn(1, pages.count) - 1
                    scope.launch { list.scrollToItem(p) }
                }) { Text("Springen") }
            },
            dismissButton = { TextButton(onClick = { jump = false }) { Text("Abbrechen") } },
        )
    }
}

/** Leser-Route: EPUB im Kindle-Leser, PDF in der Seitenansicht. */
@Composable
fun ReaderRoute(onBack: () -> Unit, vm: ReaderViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val s by vm.state.collectAsState()
    when {
        s.book?.isPdf == true -> PdfReaderScreen(onBack, vm)
        else -> EpubReaderScreen(onBack, vm)
    }
}
