package com.hikari.app.ui.feed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom SVG icons not present in material-icons-core.
 * Built with ImageVector.Builder so they are proper Compose vector icons.
 */
object HikariIcons {

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
