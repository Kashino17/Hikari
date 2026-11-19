package com.hikari.app.ui.ebooks

import android.webkit.JavascriptInterface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wo der Leser gerade steht (aus foliate-js „relocate“). */
@Serializable
data class ReaderLocation(
    val cfi: String,
    val fraction: Double = 0.0,
    val chapter: String = "",
    val minutesLeftInChapter: Int = 0,
    val page: Int = 0,
    val pages: Int = 0,
)

@Serializable
data class TocEntry(val label: String, val href: String, val depth: Int = 0)

@Serializable
data class TextSelection(val cfi: String, val text: String)

@Serializable
data class TappedMark(val id: String, val cfi: String)

/** Nachrichten der Leser-Seite (assets/ebook-reader/reader.js) lesen — nie abstürzen. */
object ReaderMessages {
    private val json = Json { ignoreUnknownKeys = true }

    fun relocate(s: String): ReaderLocation? = runCatching { json.decodeFromString<ReaderLocation>(s) }.getOrNull()
    fun toc(s: String): List<TocEntry> = runCatching { json.decodeFromString<List<TocEntry>>(s) }.getOrDefault(emptyList())
    fun selection(s: String): TextSelection? = runCatching { json.decodeFromString<TextSelection>(s) }.getOrNull()
    fun tappedMark(s: String): TappedMark? = runCatching { json.decodeFromString<TappedMark>(s) }.getOrNull()
}

/** Die Brücke, die die Leser-Seite als window.HikariBridge sieht. Aufrufe kommen nicht im Haupt-Thread an. */
class ReaderBridge(
    private val onBoot: () -> Unit,
    private val onOpened: () -> Unit,
    private val onLocation: (ReaderLocation) -> Unit,
    private val onTocLoaded: (List<TocEntry>) -> Unit,
    private val onSelected: (TextSelection?) -> Unit,
    private val onCenterTap: () -> Unit,
    private val onMarkTap: (TappedMark) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    @JavascriptInterface fun onReady(s: String) = if ("\"boot\"" in s) onBoot() else onOpened()
    @JavascriptInterface fun onRelocate(s: String) { ReaderMessages.relocate(s)?.let(onLocation) }
    @JavascriptInterface fun onToc(s: String) = onTocLoaded(ReaderMessages.toc(s))
    @JavascriptInterface fun onSelection(s: String) = onSelected(ReaderMessages.selection(s))
    @JavascriptInterface fun onTapCenter(@Suppress("UNUSED_PARAMETER") s: String) = onCenterTap()
    @JavascriptInterface fun onTapMark(s: String) { ReaderMessages.tappedMark(s)?.let(onMarkTap) }
    @JavascriptInterface fun onError(s: String) = onFailure(s)
}
