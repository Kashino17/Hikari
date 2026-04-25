package com.hikari.app.ui.feed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun ImageVector.Builder.bookmarkShape(filled: Boolean) = apply {
    path(
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = if (filled) null else SolidColor(Color.Black),
        strokeLineWidth = if (filled) 0f else 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        // M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z
        moveTo(19f, 21f)
        lineTo(12f, 16f)
        lineTo(5f, 21f)
        verticalLineTo(5f)
        curveTo(5f, 3.9f, 5.9f, 3f, 7f, 3f)
        horizontalLineTo(17f)
        curveTo(18.1f, 3f, 19f, 3.9f, 19f, 5f)
        close()
    }
}

/**
 * Custom SVG icons not present in material-icons-core.
 * Built with ImageVector.Builder so they are proper Compose vector icons.
 */
object HikariIcons {

    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bookmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).bookmarkShape(filled = true).build()
    }

    val BookmarkOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "BookmarkOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).bookmarkShape(filled = false).build()
    }

    /** Two vertical bars — standard pause icon (24x24 dp). */
    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                strokeAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Left bar: x=6, y=19 rect width 4
                moveTo(6f, 19f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                // Right bar: x=14, y=19
                moveTo(14f, 5f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }

    /** Four corner arrows pointing outward — fullscreen enter (24x24 dp). */
    val Fullscreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "Fullscreen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Top-left corner
                moveTo(7f, 14f)
                horizontalLineTo(5f)
                verticalLineTo(19f)
                horizontalLineTo(10f)
                verticalLineTo(17f)
                horizontalLineTo(7f)
                verticalLineTo(14f)
                close()
                // Top-right corner
                moveTo(19f, 14f)
                verticalLineTo(17f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(21f)
                verticalLineTo(14f)
                horizontalLineTo(19f)
                close()
                // Bottom-right corner
                moveTo(16f, 5f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                verticalLineTo(10f)
                horizontalLineTo(21f)
                verticalLineTo(5f)
                horizontalLineTo(16f)
                close()
                // Bottom-left corner
                moveTo(5f, 10f)
                horizontalLineTo(7f)
                verticalLineTo(7f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                verticalLineTo(10f)
                close()
            }
        }.build()
    }

    /**
     * Replay 5 — circular arrow with "5" inside (24x24 dp).
     * Counter-clockwise arc with a triangle arrowhead, matching Material Design's replay_5 shape.
     */
    val Replay5: ImageVector by lazy {
        ImageVector.Builder(
            name = "Replay5",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Circular arrow (counter-clockwise arc from top, arrowhead at start)
                moveTo(11.99f, 5f)
                curveTo(8.68f, 5f, 5.83f, 6.84f, 4.28f, 9.54f)
                lineTo(2f, 7.25f)
                verticalLineTo(13.5f)
                horizontalLineTo(8.25f)
                lineTo(5.95f, 11.22f)
                curveTo(7.07f, 8.84f, 9.34f, 7f, 12f, 7f)
                curveTo(15.86f, 7f, 19f, 10.14f, 19f, 14f)
                curveTo(19f, 17.86f, 15.86f, 21f, 12f, 21f)
                curveTo(8.14f, 21f, 5f, 17.86f, 5f, 14f)
                horizontalLineTo(3f)
                curveTo(3f, 18.97f, 7.03f, 23f, 12f, 23f)
                curveTo(16.97f, 23f, 21f, 18.97f, 21f, 14f)
                curveTo(21f, 9.03f, 16.97f, 5f, 12f, 5f)
                lineTo(11.99f, 5f)
                close()
                // "5" digit rendered as filled paths
                moveTo(10.88f, 16.75f)
                curveTo(11.17f, 16.91f, 11.5f, 17f, 11.86f, 17f)
                curveTo(12.9f, 17f, 13.73f, 16.17f, 13.73f, 15.13f)
                curveTo(13.73f, 14.09f, 12.9f, 13.27f, 11.86f, 13.27f)
                curveTo(11.46f, 13.27f, 11.1f, 13.4f, 10.82f, 13.61f)
                lineTo(11.02f, 11.75f)
                horizontalLineTo(13.56f)
                verticalLineTo(11f)
                horizontalLineTo(10.36f)
                lineTo(10.05f, 14.24f)
                lineTo(10.74f, 14.43f)
                curveTo(10.97f, 14.19f, 11.3f, 14.02f, 11.66f, 14.02f)
                curveTo(12.28f, 14.02f, 12.77f, 14.5f, 12.77f, 15.13f)
                curveTo(12.77f, 15.75f, 12.28f, 16.23f, 11.66f, 16.23f)
                curveTo(11.27f, 16.23f, 10.92f, 16.04f, 10.7f, 15.74f)
                lineTo(10.08f, 16.27f)
                curveTo(10.32f, 16.51f, 10.59f, 16.67f, 10.88f, 16.75f)
                close()
            }
        }.build()
    }

    /**
     * Forward 5 — circular arrow with "5" inside (24x24 dp).
     * Clockwise arc with a triangle arrowhead at the end.
     */
    val Forward5: ImageVector by lazy {
        ImageVector.Builder(
            name = "Forward5",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Clockwise arc (mirror of Replay5), arrowhead at top-right
                moveTo(18f, 7.25f)
                verticalLineTo(13.5f)
                horizontalLineTo(11.75f)
                lineTo(14.05f, 11.22f)
                curveTo(12.93f, 8.84f, 10.66f, 7f, 8f, 7f)
                curveTo(4.14f, 7f, 1f, 10.14f, 1f, 14f)
                curveTo(1f, 17.86f, 4.14f, 21f, 8f, 21f)
                curveTo(11.86f, 21f, 15f, 17.86f, 15f, 14f)
                horizontalLineTo(13f)
                curveTo(13f, 16.76f, 10.76f, 19f, 8f, 19f)
                curveTo(5.24f, 19f, 3f, 16.76f, 3f, 14f)
                curveTo(3f, 11.24f, 5.24f, 9f, 8f, 9f)
                curveTo(9.66f, 9f, 11.14f, 9.84f, 12.05f, 11.22f)
                lineTo(9.75f, 13.5f)
                horizontalLineTo(16f)
                verticalLineTo(7.25f)
                lineTo(18f, 7.25f)
                close()
                // "5" digit
                moveTo(6.88f, 16.75f)
                curveTo(7.17f, 16.91f, 7.5f, 17f, 7.86f, 17f)
                curveTo(8.9f, 17f, 9.73f, 16.17f, 9.73f, 15.13f)
                curveTo(9.73f, 14.09f, 8.9f, 13.27f, 7.86f, 13.27f)
                curveTo(7.46f, 13.27f, 7.1f, 13.4f, 6.82f, 13.61f)
                lineTo(7.02f, 11.75f)
                horizontalLineTo(9.56f)
                verticalLineTo(11f)
                horizontalLineTo(6.36f)
                lineTo(6.05f, 14.24f)
                lineTo(6.74f, 14.43f)
                curveTo(6.97f, 14.19f, 7.3f, 14.02f, 7.66f, 14.02f)
                curveTo(8.28f, 14.02f, 8.77f, 14.5f, 8.77f, 15.13f)
                curveTo(8.77f, 15.75f, 8.28f, 16.23f, 7.66f, 16.23f)
                curveTo(7.27f, 16.23f, 6.92f, 16.04f, 6.7f, 15.74f)
                lineTo(6.08f, 16.27f)
                curveTo(6.32f, 16.51f, 6.59f, 16.67f, 6.88f, 16.75f)
                close()
            }
        }.build()
    }

    /** Four corner arrows pointing inward — fullscreen exit (24x24 dp). */
    val FullscreenExit: ImageVector by lazy {
        ImageVector.Builder(
            name = "FullscreenExit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Top-left corner (arrows point toward center)
                moveTo(5f, 16f)
                horizontalLineTo(8f)
                verticalLineTo(19f)
                horizontalLineTo(10f)
                verticalLineTo(14f)
                horizontalLineTo(5f)
                verticalLineTo(16f)
                close()
                // Top-right
                moveTo(14f, 19f)
                horizontalLineTo(16f)
                verticalLineTo(16f)
                horizontalLineTo(19f)
                verticalLineTo(14f)
                horizontalLineTo(14f)
                verticalLineTo(19f)
                close()
                // Bottom-right
                moveTo(16f, 8f)
                horizontalLineTo(19f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                verticalLineTo(5f)
                horizontalLineTo(16f)
                verticalLineTo(8f)
                close()
                // Bottom-left
                moveTo(5f, 10f)
                horizontalLineTo(8f)
                verticalLineTo(8f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(8f)
                verticalLineTo(8f)
                horizontalLineTo(5f)
                verticalLineTo(10f)
                close()
            }
        }.build()
    }
}
