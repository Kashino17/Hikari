package com.hikari.app.ui.games

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

// ————— Daten —————

private val BbColors = listOf(
    Color(0xFFFBBF24), // Amber
    Color(0xFF60A5FA), // Blau
    Color(0xFFEC4899), // Pink
    Color(0xFF4ADE80), // Grün
    Color(0xFF22D3EE), // Cyan
    Color(0xFFA78BFA), // Lila
)

// Zellen als (Zeile, Spalte)
private val BbShapes: List<List<Pair<Int, Int>>> = listOf(
    listOf(0 to 0),
    // Linien horizontal
    listOf(0 to 0, 0 to 1),
    listOf(0 to 0, 0 to 1, 0 to 2),
    listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
    // Linien vertikal
    listOf(0 to 0, 1 to 0),
    listOf(0 to 0, 1 to 0, 2 to 0),
    listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
    // Quadrate
    listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1),
    listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2, 2 to 0, 2 to 1, 2 to 2),
    // L-Formen (4 Rotationen)
    listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1),
    listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0),
    listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
    listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2),
    // T-Form
    listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1),
    // S / Z
    listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1),
    listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2),
)

private class BbPiece(val cells: List<Pair<Int, Int>>, val colorIndex: Int, val id: Int) {
    val rows = cells.maxOf { it.first } + 1
    val cols = cells.maxOf { it.second } + 1
}

private class BbClearFx(val cells: List<Triple<Int, Int, Int>>, val key: Int)
private class BbReturnFx(val piece: BbPiece, val slot: Int, val from: Offset)

private var bbNextId = 0

private fun bbRandomPiece(): BbPiece =
    BbPiece(BbShapes.random(), Random.nextInt(BbColors.size), bbNextId++)

private fun bbCanPlace(grid: IntArray, piece: BbPiece, row: Int, col: Int): Boolean {
    for ((r, c) in piece.cells) {
        val rr = row + r
        val cc = col + c
        if (rr !in 0..7 || cc !in 0..7) return false
        if (grid[rr * 8 + cc] >= 0) return false
    }
    return true
}

private fun bbFitsAnywhere(grid: IntArray, piece: BbPiece): Boolean {
    for (r in 0..7) for (c in 0..7) if (bbCanPlace(grid, piece, r, c)) return true
    return false
}

private fun bbFullLines(grid: IntArray): Pair<List<Int>, List<Int>> {
    val rows = (0..7).filter { r -> (0..7).all { c -> grid[r * 8 + c] >= 0 } }
    val cols = (0..7).filter { c -> (0..7).all { r -> grid[r * 8 + c] >= 0 } }
    return rows to cols
}

// ————— UI —————

@Composable
fun BlockBlastGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    var highscore by remember { mutableIntStateOf(prefs.getInt("blockblast_highscore", 0)) }
    var newRecord by remember { mutableStateOf(false) }

    var grid by remember { mutableStateOf(IntArray(64) { -1 }) }
    var tray by remember { mutableStateOf<List<BbPiece?>>(List(3) { bbRandomPiece() }) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var deadFlash by remember { mutableStateOf(false) }

    var dragSlot by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridSizePx by remember { mutableFloatStateOf(0f) }
    val slotCenters = remember { mutableStateMapOf<Int, Offset>() }

    var returnFx by remember { mutableStateOf<BbReturnFx?>(null) }
    val returnAnim = remember { Animatable(0f) }
    LaunchedEffect(returnFx) {
        if (returnFx != null) {
            returnAnim.snapTo(0f)
            returnAnim.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            returnFx = null
        }
    }

    var clearFx by remember { mutableStateOf<BbClearFx?>(null) }
    val clearAnim = remember { Animatable(1f) }
    LaunchedEffect(clearFx?.key) {
        if (clearFx != null) {
            clearAnim.snapTo(0f)
            clearAnim.animateTo(1f, tween(340))
            clearFx = null
        }
    }

    var popup by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val density = LocalDensity.current
    val liftPx = with(density) { 90.dp.toPx() }

    val fits = remember(grid, tray) { tray.map { it == null || bbFitsAnywhere(grid, it) } }
    val isDead = remember(grid, tray) {
        val alive = tray.filterNotNull()
        alive.isNotEmpty() && alive.none { bbFitsAnywhere(grid, it) }
    }
    LaunchedEffect(isDead) {
        if (isDead && !gameOver) {
            repeat(3) {
                deadFlash = true
                delay(160)
                deadFlash = false
                delay(120)
            }
            if (score > highscore) {
                newRecord = true
                highscore = score
                prefs.edit().putInt("blockblast_highscore", score).apply()
            } else {
                newRecord = false
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            gameOver = true
        }
    }

    fun dropTarget(piece: BbPiece): Triple<Int, Int, Boolean>? {
        if (gridSizePx <= 0f) return null
        val cellPx = gridSizePx / 8f
        val topLeftX = dragPos.x - piece.cols * cellPx / 2f
        val topLeftY = dragPos.y - liftPx - piece.rows * cellPx / 2f
        val col = ((topLeftX - gridOrigin.x) / cellPx).roundToInt()
        val row = ((topLeftY - gridOrigin.y) / cellPx).roundToInt()
        return Triple(row, col, bbCanPlace(grid, piece, row, col))
    }

    fun place(piece: BbPiece, row: Int, col: Int, slot: Int) {
        val newGrid = grid.copyOf()
        for ((r, c) in piece.cells) newGrid[(row + r) * 8 + (col + c)] = piece.colorIndex
        var gained = piece.cells.size
        val (fullRows, fullCols) = bbFullLines(newGrid)
        val lineCount = fullRows.size + fullCols.size
        if (lineCount > 0) {
            combo += 1
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val bonus = lineCount * (lineCount + 1) / 2 * 100 * combo
            gained += bonus
            val fxCells = ArrayList<Triple<Int, Int, Int>>()
            for (r in fullRows) for (c in 0..7) fxCells.add(Triple(r, c, newGrid[r * 8 + c]))
            for (c in fullCols) for (r in 0..7) if (r !in fullRows) fxCells.add(Triple(r, c, newGrid[r * 8 + c]))
            for ((r, c, _) in fxCells) newGrid[r * 8 + c] = -1
            clearFx = BbClearFx(fxCells, (clearFx?.key ?: 0) + 1)
        } else {
            combo = 0
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        score += gained
        val text = "+$gained" + if (lineCount > 0 && combo >= 2) " · Combo x$combo" else ""
        popup = ((popup?.first ?: 0) + 1) to text
        grid = newGrid
        tray = tray.toMutableList().also { it[slot] = null }
        if (tray.all { it == null }) tray = List(3) { bbRandomPiece() }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(HikariBg)
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
    ) {
        val gridDp = if (maxWidth - 32.dp < 340.dp) maxWidth - 32.dp else 340.dp
        val cellDp = gridDp / 8

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Zurück", color = HikariTextMuted) }
                Text("Block Blast", fontSize = 18.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End) {
                    Text("$score", fontSize = 16.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    Text("Best: $highscore", fontSize = 11.sp, color = HikariTextMuted)
                }
            }

            Text(
                if (combo >= 2) "Combo x$combo" else " ",
                fontSize = 13.sp,
                color = HikariPrimary,
                fontWeight = FontWeight.Bold,
            )

            // Überschüssige Höhe sammelt sich oben: auf langen Displays rutschen
            // Feld und Steine zusammen nach unten in Daumenreichweite.
            Spacer(Modifier.weight(1f))

            Canvas(
                Modifier
                    .size(gridDp)
                    .onGloballyPositioned {
                        gridOrigin = it.positionInRoot()
                        gridSizePx = it.size.width.toFloat()
                    }
            ) {
                val cell = size.width / 8f
                val pad = cell * 0.06f
                val corner = CornerRadius(cell * 0.18f, cell * 0.18f)
                for (r in 0..7) for (c in 0..7) {
                    val v = grid[r * 8 + c]
                    val topLeft = Offset(c * cell + pad, r * cell + pad)
                    val sz = Size(cell - pad * 2, cell - pad * 2)
                    if (v < 0) {
                        drawRoundRect(HikariSurfaceHigh, topLeft, sz, corner)
                    } else {
                        val col = BbColors[v]
                        drawRoundRect(col, topLeft, sz, corner)
                        drawRoundRect(
                            lerpColor(col, Color.Black, 0.45f), topLeft, sz, corner,
                            style = Stroke(width = cell * 0.06f),
                        )
                    }
                }

                // Ghost-Preview während des Drags
                val dPiece = if (dragSlot >= 0) tray.getOrNull(dragSlot) else null
                if (dPiece != null) {
                    val target = dropTarget(dPiece)
                    if (target != null && target.third) {
                        val (row, colBase, _) = target
                        val gcol = BbColors[dPiece.colorIndex]
                        // Reihen/Spalten, die komplett würden, schimmern lassen
                        val sim = grid.copyOf()
                        for ((r, c) in dPiece.cells) sim[(row + r) * 8 + (colBase + c)] = dPiece.colorIndex
                        val (fr, fc) = bbFullLines(sim)
                        for (r in fr) for (c in 0..7) drawRoundRect(
                            gcol.copy(alpha = 0.22f),
                            Offset(c * cell + pad, r * cell + pad),
                            Size(cell - pad * 2, cell - pad * 2), corner,
                        )
                        for (c in fc) for (r in 0..7) drawRoundRect(
                            gcol.copy(alpha = 0.22f),
                            Offset(c * cell + pad, r * cell + pad),
                            Size(cell - pad * 2, cell - pad * 2), corner,
                        )
                        for ((r, c) in dPiece.cells) drawRoundRect(
                            gcol.copy(alpha = 0.45f),
                            Offset((colBase + c) * cell + pad, (row + r) * cell + pad),
                            Size(cell - pad * 2, cell - pad * 2), corner,
                        )
                    }
                }

                // Clear-Animation (Blink/Fade)
                val fx = clearFx
                if (fx != null) {
                    val p = clearAnim.value
                    for ((r, c, ci) in fx.cells) {
                        val col = lerpColor(BbColors[ci.coerceIn(0, BbColors.size - 1)], Color.White, 0.5f)
                        val shrink = cell * 0.5f * p
                        drawRoundRect(
                            col.copy(alpha = 1f - p),
                            Offset(c * cell + pad + shrink / 2, r * cell + pad + shrink / 2),
                            Size(cell - pad * 2 - shrink, cell - pad * 2 - shrink),
                            corner,
                        )
                    }
                }
            }

            // Deutlich kleiner als der obere Freiraum — Feld und Steine bleiben
            // ein zusammenhängender Block statt an den Rändern zu kleben.
            Spacer(Modifier.weight(0.3f))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(108.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (slot in 0..2) {
                    val piece = tray[slot]
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned {
                                slotCenters[slot] = it.positionInRoot() +
                                    Offset(it.size.width / 2f, it.size.height / 2f)
                            }
                            .pointerInput(piece?.id, gameOver) {
                                detectDragGestures(
                                    onDragStart = { off ->
                                        if (piece != null && !gameOver && returnFx == null) {
                                            dragSlot = slot
                                            val center = slotCenters[slot] ?: Offset.Zero
                                            dragPos = center - Offset(size.width / 2f, size.height / 2f) + off
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragSlot == slot && piece != null) {
                                            val t = dropTarget(piece)
                                            if (t != null && t.third) {
                                                place(piece, t.first, t.second, slot)
                                            } else {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                returnFx = BbReturnFx(piece, slot, dragPos)
                                            }
                                        }
                                        dragSlot = -1
                                    },
                                    onDragCancel = {
                                        if (dragSlot == slot && piece != null) {
                                            returnFx = BbReturnFx(piece, slot, dragPos)
                                        }
                                        dragSlot = -1
                                    },
                                    onDrag = { change, amount ->
                                        if (dragSlot == slot) {
                                            change.consume()
                                            dragPos += amount
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val hidden = dragSlot == slot || returnFx?.slot == slot
                        if (piece != null && !hidden) {
                            val appear = remember(piece.id) { Animatable(0.5f) }
                            LaunchedEffect(piece.id) {
                                appear.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                            val fitsHere = fits[slot]
                            BbPieceView(
                                piece = piece,
                                cellSize = 17.dp,
                                alpha = if (fitsHere) 1f else 0.35f,
                                tint = if (deadFlash && !fitsHere) HikariDanger else null,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = appear.value
                                    scaleY = appear.value
                                },
                            )
                        }
                    }
                }
            }
        }

        // Gezogenes Piece — ~90dp über dem Finger, leicht vergrößert
        val dragPiece = if (dragSlot >= 0) tray.getOrNull(dragSlot) else null
        if (dragPiece != null && gridSizePx > 0f) {
            val cellPx = gridSizePx / 8f
            val w = dragPiece.cols * cellPx
            val h = dragPiece.rows * cellPx
            val tl = Offset(dragPos.x - w / 2f, dragPos.y - liftPx - h / 2f) - rootOrigin
            BbPieceView(
                piece = dragPiece,
                cellSize = cellDp,
                modifier = Modifier
                    .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                    .graphicsLayer { scaleX = 1.07f; scaleY = 1.07f },
            )
        }

        // Zurück-in-den-Slot-Animation bei ungültigem Drop
        returnFx?.let { fx ->
            val target = slotCenters[fx.slot] ?: Offset.Zero
            val t = returnAnim.value
            val startX = fx.from.x
            val startY = fx.from.y - liftPx
            val cx = startX + (target.x - startX) * t
            val cy = startY + (target.y - startY) * t
            val cellNow = cellDp + (17.dp - cellDp) * t
            val wPx = with(density) { (cellNow * fx.piece.cols).toPx() }
            val hPx = with(density) { (cellNow * fx.piece.rows).toPx() }
            val tl = Offset(cx - wPx / 2f, cy - hPx / 2f) - rootOrigin
            BbPieceView(
                piece = fx.piece,
                cellSize = cellNow,
                modifier = Modifier.offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) },
            )
        }

        // Punkte-Popup
        popup?.let { (key, text) ->
            val anim = remember(key) { Animatable(0f) }
            LaunchedEffect(key) {
                anim.animateTo(1f, tween(800))
                popup = null
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text,
                    fontSize = 22.sp,
                    color = HikariPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        translationY = -140f - anim.value * 90f
                        alpha = 1f - anim.value * anim.value
                    },
                )
            }
        }

        // Game-Over-Overlay
        if (gameOver) {
            Box(
                Modifier.fillMaxSize().background(Color(0xE60A0A0A)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Game Over", fontSize = 28.sp, color = HikariText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Punkte: $score", fontSize = 20.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (newRecord) {
                        Text("Neuer Rekord!", fontSize = 16.sp, color = HikariPrimary, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Highscore: $highscore", fontSize = 14.sp, color = HikariTextMuted)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            grid = IntArray(64) { -1 }
                            tray = List(3) { bbRandomPiece() }
                            score = 0
                            combo = 0
                            newRecord = false
                            gameOver = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Nochmal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BbPieceView(
    piece: BbPiece,
    cellSize: Dp,
    alpha: Float = 1f,
    tint: Color? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(cellSize * piece.cols, cellSize * piece.rows)) {
        val cell = size.width / piece.cols
        val pad = cell * 0.07f
        val corner = CornerRadius(cell * 0.2f, cell * 0.2f)
        val base = tint ?: BbColors[piece.colorIndex]
        for ((r, c) in piece.cells) {
            val topLeft = Offset(c * cell + pad, r * cell + pad)
            val sz = Size(cell - pad * 2, cell - pad * 2)
            drawRoundRect(base.copy(alpha = alpha), topLeft, sz, corner)
            drawRoundRect(
                lerpColor(base, Color.Black, 0.45f).copy(alpha = alpha),
                topLeft, sz, corner,
                style = Stroke(width = cell * 0.07f),
            )
        }
    }
}
