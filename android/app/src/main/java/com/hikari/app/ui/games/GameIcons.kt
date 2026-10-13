package com.hikari.app.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.theme.HikariSurfaceHigh

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineToRelative(w - 2 * r)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, r)
    verticalLineToRelative(h - 2 * r)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, r)
    horizontalLineToRelative(-(w - 2 * r))
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, -r)
    verticalLineToRelative(-(h - 2 * r))
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, -r)
    close()
}

private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
    moveTo(x, y)
    horizontalLineToRelative(w)
    verticalLineToRelative(h)
    horizontalLineToRelative(-w)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, 2 * r, 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, -2 * r, 0f)
    close()
}

private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = false, 2 * rx, 0f)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = false, -2 * rx, 0f)
    close()
}

private fun PathBuilder.sevenSeg(digit: Int, x: Float, y: Float, w: Float, h: Float) {
    val mid = y + h / 2f
    val i = 0.35f
    val segs = when (digit) {
        0 -> setOf('a', 'b', 'c', 'd', 'e', 'f')
        2 -> setOf('a', 'b', 'g', 'e', 'd')
        4 -> setOf('f', 'g', 'b', 'c')
        8 -> setOf('a', 'b', 'c', 'd', 'e', 'f', 'g')
        else -> emptySet()
    }
    if ('a' in segs) { moveTo(x + i, y); lineTo(x + w - i, y) }
    if ('g' in segs) { moveTo(x + i, mid); lineTo(x + w - i, mid) }
    if ('d' in segs) { moveTo(x + i, y + h); lineTo(x + w - i, y + h) }
    if ('f' in segs) { moveTo(x, y + i); lineTo(x, mid - i * 0.6f) }
    if ('b' in segs) { moveTo(x + w, y + i); lineTo(x + w, mid - i * 0.6f) }
    if ('e' in segs) { moveTo(x, mid + i * 0.6f); lineTo(x, y + h - i) }
    if ('c' in segs) { moveTo(x + w, mid + i * 0.6f); lineTo(x + w, y + h - i) }
}

private fun iconBuilder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
)

private val BlockBlastIcon: ImageVector = iconBuilder("BlockBlast").apply {
    path(fill = SolidColor(Color(0xFFF59E0B))) { roundRect(3f, 13f, 7.5f, 8f, 1.8f) }
    path(fill = SolidColor(Color(0xFF92400E))) { roundRect(13f, 13f, 8f, 8f, 1.8f) }
    path(fill = SolidColor(Color(0xFFFBBF24))) { roundRect(13f, 3f, 8f, 7.5f, 1.8f) }
    path(fill = SolidColor(Color(0xFFFDE68A))) { roundRect(3f, 3f, 7.5f, 7.5f, 1.8f) }
}.build()

private val FruitMergeIcon: ImageVector = iconBuilder("FruitMerge").apply {
    path(fill = SolidColor(Color(0xFF052E16))) { circle(12f, 12.5f, 9f) }
    path(fill = SolidColor(Color(0xFF86EFAC))) { circle(12f, 12.5f, 7.4f) }
    path(fill = SolidColor(Color(0xFFF87171))) { circle(12f, 12.5f, 6.2f) }
    path(fill = SolidColor(Color(0xFF450A0A))) {
        circle(12f, 10f, 0.7f)
        circle(9.5f, 13.8f, 0.7f)
        circle(14.5f, 13.8f, 0.7f)
    }
}.build()

private val SpaceShooterIcon: ImageVector = iconBuilder("SpaceShooter").apply {
    path(fill = SolidColor(Color(0xFFEF4444))) {
        moveTo(9.4f, 12.6f)
        curveTo(7f, 14f, 6.2f, 16.6f, 6f, 18.6f)
        lineTo(9.4f, 16.6f)
        close()
        moveTo(14.6f, 12.6f)
        curveTo(17f, 14f, 17.8f, 16.6f, 18f, 18.6f)
        lineTo(14.6f, 16.6f)
        close()
    }
    path(fill = SolidColor(Color(0xFFF97316))) {
        moveTo(10.6f, 17.2f)
        curveTo(10.2f, 19f, 10.9f, 20.6f, 12f, 21.8f)
        curveTo(13.1f, 20.6f, 13.8f, 19f, 13.4f, 17.2f)
        close()
    }
    path(fill = SolidColor(Color(0xFFFBBF24))) {
        moveTo(11.3f, 17.4f)
        curveTo(11.1f, 18.6f, 11.5f, 19.7f, 12f, 20.5f)
        curveTo(12.5f, 19.7f, 12.9f, 18.6f, 12.7f, 17.4f)
        close()
    }
    path(fill = SolidColor(Color(0xFFF1F5F9))) {
        moveTo(12f, 3f)
        curveTo(14.5f, 5.5f, 15f, 8.5f, 15f, 12.8f)
        lineTo(15f, 15.6f)
        curveTo(15f, 16.4f, 14.4f, 17f, 13.6f, 17f)
        lineTo(10.4f, 17f)
        curveTo(9.6f, 17f, 9f, 16.4f, 9f, 15.6f)
        lineTo(9f, 12.8f)
        curveTo(9f, 8.5f, 9.5f, 5.5f, 12f, 3f)
        close()
    }
    path(fill = SolidColor(Color(0xFF155E75))) { circle(12f, 10.3f, 2.1f) }
    path(fill = SolidColor(Color(0xFF67E8F9))) { circle(12f, 10.3f, 1.2f) }
}.build()

private val FruitHoleIcon: ImageVector = iconBuilder("FruitHole").apply {
    group(rotate = -18f, pivotX = 12f, pivotY = 12f) {
        path(
            stroke = SolidColor(Color(0xFF8B5CF6)),
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Round,
        ) { ellipse(12f, 12f, 6.8f, 2.8f) }
        path(
            stroke = SolidColor(Color(0xFFC4B5FD)),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        ) { ellipse(12f, 12f, 8.6f, 3.6f) }
    }
    path(fill = SolidColor(Color(0xFF0D0618))) { circle(12f, 12f, 4.4f) }
    path(
        stroke = SolidColor(Color(0xFFA78BFA)),
        strokeLineWidth = 0.9f,
    ) { circle(12f, 12f, 4.4f) }
    path(fill = SolidColor(Color(0xFF2E1065))) { circle(12f, 12f, 1.6f) }
    group(rotate = -18f, pivotX = 12f, pivotY = 12f) {
        path(
            stroke = SolidColor(Color(0xFFDDD6FE)),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(3.4f, 12f)
            arcTo(8.6f, 3.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.6f, 12f)
        }
    }
    path(fill = SolidColor(Color(0xFFDDD6FE))) {
        circle(19f, 6.5f, 0.5f)
        circle(5.5f, 17.5f, 0.45f)
    }
}.build()

private val ColorSortIcon: ImageVector = iconBuilder("ColorSort").apply {
    group(
        clipPathData = PathBuilder().apply {
            moveTo(8.8f, 5f)
            lineTo(8.8f, 15f)
            arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15.2f, 15f)
            lineTo(15.2f, 5f)
            close()
        }.nodes,
    ) {
        path(fill = SolidColor(Color(0xFF22D3EE))) { rect(8.8f, 12.6f, 6.4f, 5.8f) }
        path(fill = SolidColor(Color(0xFFFBBF24))) { rect(8.8f, 10.2f, 6.4f, 2.4f) }
        path(fill = SolidColor(Color(0xFFA78BFA))) { rect(8.8f, 7.8f, 6.4f, 2.4f) }
        path(fill = SolidColor(Color(0xCCFFFFFF))) {
            circle(11f, 14.5f, 0.55f)
            circle(13f, 9f, 0.5f)
        }
    }
    path(
        stroke = SolidColor(Color(0xFFFCE7F3)),
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(8f, 4.6f)
        lineTo(8f, 15f)
        arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16f, 15f)
        lineTo(16f, 4.6f)
        moveTo(7f, 4.4f)
        lineTo(17f, 4.4f)
    }
}.build()

private val Twenty48Icon: ImageVector = iconBuilder("Twenty48").apply {
    path(fill = SolidColor(Color(0xFFFFEDD5))) { roundRect(2.5f, 4f, 19f, 16f, 4f) }
    path(
        stroke = SolidColor(Color(0xFFC2410C)),
        strokeLineWidth = 0.95f,
        strokeLineCap = StrokeCap.Round,
    ) {
        sevenSeg(2, 4.35f, 8.8f, 3f, 6.4f)
        sevenSeg(0, 8.45f, 8.8f, 3f, 6.4f)
        sevenSeg(4, 12.55f, 8.8f, 3f, 6.4f)
        sevenSeg(8, 16.65f, 8.8f, 3f, 6.4f)
    }
}.build()

private val SudokuIcon: ImageVector = iconBuilder("Sudoku").apply {
    path(
        fill = SolidColor(Color(0xFF115E59)),
        stroke = SolidColor(Color(0xFF5EEAD4)),
        strokeLineWidth = 1.2f,
    ) { roundRect(3.5f, 3.5f, 17f, 17f, 3f) }
    path(
        stroke = SolidColor(Color(0xFF5EEAD4)),
        strokeLineWidth = 0.9f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(9.17f, 4.3f); lineTo(9.17f, 19.7f)
        moveTo(14.83f, 4.3f); lineTo(14.83f, 19.7f)
        moveTo(4.3f, 9.17f); lineTo(19.7f, 9.17f)
        moveTo(4.3f, 14.83f); lineTo(19.7f, 14.83f)
    }
    path(fill = SolidColor(Color(0xFF134E4A))) {
        roundRect(9.7f, 4.1f, 4.6f, 4.6f, 1.1f)
        roundRect(15.35f, 15.4f, 4.6f, 4.6f, 1.1f)
    }
    path(fill = SolidColor(Color(0xFF5EEAD4))) {
        circle(12f, 6.4f, 0.85f)
        circle(17.65f, 17.7f, 0.85f)
    }
    path(fill = SolidColor(Color(0xFF99F6E4))) { circle(6.3f, 12f, 0.85f) }
}.build()

private val SnakeIcon: ImageVector = iconBuilder("Snake").apply {
    path(
        stroke = SolidColor(Color(0xFFD9F99D)),
        strokeLineWidth = 3.2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(6f, 18.5f)
        curveTo(9.5f, 18.8f, 8.6f, 14.4f, 12f, 13.6f)
        curveTo(15.2f, 12.9f, 14.8f, 10.2f, 16.8f, 9.4f)
    }
    path(fill = SolidColor(Color(0xFF3F6212))) { circle(17.9f, 8.7f, 2.9f) }
    path(fill = SolidColor(Color(0xFFECFCCB))) { circle(18.9f, 7.7f, 0.85f) }
    path(fill = SolidColor(Color(0xFF1A2E05))) { circle(19.1f, 7.6f, 0.4f) }
}.build()

private val TicTacToeIcon: ImageVector = iconBuilder("TicTacToe").apply {
    path(
        stroke = SolidColor(Color(0xFF93C5FD)),
        strokeLineWidth = 1.2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(9.2f, 4f); lineTo(9.2f, 20f)
        moveTo(14.8f, 4f); lineTo(14.8f, 20f)
        moveTo(4f, 9.2f); lineTo(20f, 9.2f)
        moveTo(4f, 14.8f); lineTo(20f, 14.8f)
    }
    path(
        stroke = SolidColor(Color(0xFFEFF6FF)),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(5f, 5f); lineTo(8.2f, 8.2f)
        moveTo(8.2f, 5f); lineTo(5f, 8.2f)
    }
    path(
        stroke = SolidColor(Color(0xFFFBBF24)),
        strokeLineWidth = 1.8f,
    ) { circle(17.4f, 17.4f, 2.6f) }
}.build()

private class GameIconSpec(val background: Color, val glyph: ImageVector?)

private fun gameIconSpec(gameId: String): GameIconSpec = when (gameId) {
    "blockblast" -> GameIconSpec(Color(0xFFD97706), BlockBlastIcon)
    "fruitmerge" -> GameIconSpec(Color(0xFF15803D), FruitMergeIcon)
    "spaceshooter" -> GameIconSpec(Color(0xFF0E7490), SpaceShooterIcon)
    "fruithole" -> GameIconSpec(Color(0xFF6D28D9), FruitHoleIcon)
    "colorsort" -> GameIconSpec(Color(0xFFDB2777), ColorSortIcon)
    "2048" -> GameIconSpec(Color(0xFFEA580C), Twenty48Icon)
    "sudoku" -> GameIconSpec(Color(0xFF0D9488), SudokuIcon)
    "snake" -> GameIconSpec(Color(0xFF65A30D), SnakeIcon)
    "tictactoe" -> GameIconSpec(Color(0xFF2563EB), TicTacToeIcon)
    else -> GameIconSpec(HikariSurfaceHigh, null)
}

@Composable
fun GameAppIcon(gameId: String, size: Dp, modifier: Modifier = Modifier) {
    val spec = gameIconSpec(gameId)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(spec.background),
        contentAlignment = Alignment.Center,
    ) {
        if (spec.glyph != null) {
            Image(
                imageVector = spec.glyph,
                contentDescription = null,
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}
