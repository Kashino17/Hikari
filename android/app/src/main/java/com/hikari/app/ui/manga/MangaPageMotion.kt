package com.hikari.app.ui.manga

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Leserichtung: japanisch (rechts nach links), westlich, oder als langer Streifen (Webtoon). */
enum class ReadDirection { RTL, LTR, VERTICAL }

/** Wie umgeblättert wird. */
enum class PageAnim { BOOK, SLIDE, FADE, NONE }

enum class TapAction { NEXT, PREV, MENU, CLOSE_MENU }

/** Leser-Einstellungen für Mangas — gelten für alle Reihen. */
@Serializable
data class MangaReaderSettings(
    val direction: ReadDirection = ReadDirection.RTL,
    val anim: PageAnim = PageAnim.BOOK,
    val tapTurn: Boolean = true,
    val background: String = "schwarz",
    val volumeKeys: Boolean = true,
    /** Blaulichtfilter, siehe [com.hikari.app.ui.reader.BlueFilter]. */
    val blueFilter: Int = 0,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(raw: String?): MangaReaderSettings =
            raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: MangaReaderSettings()
    }
}

/** Was eine Seite gerade zeichnen soll. `shade` = Abdunklung 0..1, `pivotX` = Drehachse 0 (links) oder 1 (rechts). */
data class PageTransform(
    val translationX: Float = 0f,
    val rotationY: Float = 0f,
    val pivotX: Float = 0.5f,
    val alpha: Float = 1f,
    val shade: Float = 0f,
    val z: Float = 0f,
)

/** Breite der Tippbereiche am Rand — schmal, damit ein versehentlicher Daumen nicht blättert. */
const val TAP_EDGE = 0.22f

/** Wie weit sich eine Seite beim Blättern höchstens dreht; ab 90° ist sie von der Kante her unsichtbar. */
private const val MAX_TURN = 110f

/**
 * Lage einer Pager-Seite beim Umblättern. Der Pager schiebt Seiten nebeneinander; für Blättern, Gleiten und
 * Ruhig wird diese Verschiebung (teilweise) aufgehoben, damit die Seiten wie in einem Buch übereinander liegen.
 * Die frühere Seite liegt immer oben: vorwärts dreht sie sich weg, rückwärts dreht sie sich zurück.
 *
 * @param position aktuelle Pager-Stelle (Seite + Bruchteil)
 */
fun pageTransform(idx: Int, position: Float, width: Float, direction: ReadDirection, anim: PageAnim): PageTransform {
    val p = position - idx // 0 = liegt da, 0..1 = wird weggeblättert, -1..0 = liegt darunter
    val rtl = direction == ReadDirection.RTL
    val cancel = (if (rtl) -p else p) * width // hebt die Pager-Verschiebung auf
    val z = -idx.toFloat()
    if (p == 0f) return PageTransform(z = z)
    // Ganz weg (≥ 1) oder noch nicht dran (≤ -1): an der Pager-Stelle lassen, also außerhalb des Bildes —
    // sonst lägen sie unsichtbar über der aktuellen Seite und fingen Zoom und Doppeltippen ab.
    if (p >= 1f || p <= -1f) return PageTransform(alpha = 0f, z = z)
    return when (anim) {
        PageAnim.NONE -> PageTransform(z = z)
        PageAnim.SLIDE ->
            if (p > 0f) PageTransform(z = z)
            else PageTransform(translationX = cancel, shade = 0.3f * (-p).coerceAtMost(1f), alpha = if (p <= -1f) 0f else 1f, z = z)
        PageAnim.FADE ->
            if (p > 0f) PageTransform(translationX = cancel, alpha = (1f - p).coerceIn(0f, 1f), z = z)
            else PageTransform(translationX = cancel, alpha = if (p <= -1f) 0f else 1f, z = z)
        PageAnim.BOOK ->
            if (p > 0f) {
                val angle = p.coerceAtMost(1f) * MAX_TURN
                PageTransform(
                    translationX = cancel,
                    rotationY = if (rtl) angle else -angle,
                    pivotX = if (rtl) 1f else 0f,
                    alpha = if (angle > 90f) 0f else 1f,
                    shade = 0.35f * p.coerceAtMost(1f),
                    z = z,
                )
            } else {
                PageTransform(translationX = cancel, shade = 0.35f * (-p).coerceAtMost(1f), alpha = if (p <= -1f) 0f else 1f, z = z)
            }
    }
}

/** Was ein Tippen an der Stelle [x] (0 = links, 1 = rechts) bewirkt. Offenes Menü: Tippen schließt nur. */
fun tapAction(x: Float, direction: ReadDirection, tapTurn: Boolean, menuOpen: Boolean): TapAction {
    if (menuOpen) return TapAction.CLOSE_MENU
    if (!tapTurn || direction == ReadDirection.VERTICAL) return TapAction.MENU
    val left = x < TAP_EDGE
    val right = x > 1f - TAP_EDGE
    return when {
        !left && !right -> TapAction.MENU
        direction == ReadDirection.RTL -> if (left) TapAction.NEXT else TapAction.PREV
        else -> if (right) TapAction.NEXT else TapAction.PREV
    }
}

/**
 * Blättern über einer Ansicht, die selbst sofort umblättert (Buch-Leser): zwei Standbilder liegen darüber —
 * [top] die frühere Seite, die sich wegbewegt, darunter die spätere. [p] 0 = obere liegt da, 1 = ganz weg.
 * Gleiche Bewegung wie im Manga-Pager (westliche Richtung), nur ohne dessen Verschiebung.
 */
fun turnOverlay(top: Boolean, p: Float, width: Float, anim: PageAnim): PageTransform {
    val t = pageTransform(if (top) 0 else 1, p, width, ReadDirection.LTR, anim)
    // Die untere liegt als Standbild immer still und ganz sichtbar da.
    if (!top) return t.copy(translationX = 0f, rotationY = 0f, alpha = 1f)
    // Die obere: dort, wo der Pager sie hingeschoben hätte, plus die Bewegung der Blätter-Art.
    return t.copy(translationX = t.translationX - p * width)
}
