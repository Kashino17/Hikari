package com.hikari.app.ui.feed.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.hikari.app.domain.feed.MindfulCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object WisdomPosterRenderer {

    const val POSTER_WIDTH = 1080
    const val POSTER_HEIGHT = 1920

    suspend fun renderPoster(
        context: Context,
        card: MindfulCard,
        language: PosterLanguage,
    ): Pair<Bitmap, Uri> = withContext(Dispatchers.IO) {
        val poster = PosterContentResolver.resolve(card, language)
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPoster(canvas, poster)

        // Save to cache directory
        val posterDir = File(context.cacheDir, "posters").apply { mkdirs() }
        val posterFile = File(posterDir, "hikari_poster_${System.currentTimeMillis()}.png")
        FileOutputStream(posterFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            posterFile,
        )

        Pair(bitmap, uri)
    }

    fun drawPoster(canvas: Canvas, poster: PosterData) {
        val w = POSTER_WIDTH.toFloat()
        val h = POSTER_HEIGHT.toFloat()

        // 1. Background Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    poster.gradientTop.toInt(),
                    poster.gradientMid.toInt(),
                    poster.gradientBottom.toInt(),
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. Ambient Radial Glow behind the main content
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w / 2f, 750f, 550f,
                intArrayOf(
                    (poster.accentColor and 0x00FFFFFF or 0x40000000).toInt(),
                    (poster.accentColor and 0x00FFFFFF or 0x15000000).toInt(),
                    0x00000000,
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(w / 2f, 750f, 550f, glowPaint)

        // 3. Decorative subtle corner frames (Minimalist Apple Cupertino line aesthetics)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x22FFFFFF
        }
        canvas.drawRoundRect(RectF(48f, 48f, w - 48f, h - 48f), 36f, 36f, strokePaint)

        // 4. Header: Hikari Logo & Wisdom Tag
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.25f
        }
        canvas.drawText("H I K A R I", 96f, 130f, headerPaint)

        val subHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            textSize = 18f
            letterSpacing = 0.15f
        }
        canvas.drawText("MINDFUL ARCHIVE · TÄGLICHE KLARHEIT", 96f, 162f, subHeaderPaint)

        // Category Badge Pill
        val badgeText = "${poster.categoryEmoji}  ${poster.categoryLabel.uppercase()}"
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = poster.accentColor.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.10f
        }
        val badgeWidth = badgeTextPaint.measureText(badgeText) + 48f
        val badgeRect = RectF(w - 96f - badgeWidth, 115f, w - 96f, 168f)

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (poster.accentColor and 0x00FFFFFF or 0x28000000).toInt()
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (poster.accentColor and 0x00FFFFFF or 0x60000000).toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(badgeRect, 26f, 26f, badgeBgPaint)
        canvas.drawRoundRect(badgeRect, 26f, 26f, badgeBorderPaint)
        canvas.drawText(badgeText, badgeRect.left + 24f, badgeRect.top + 35f, badgeTextPaint)

        // 5. Large decorative quote symbol (for Quote cards)
        if (poster.moduleType == com.hikari.app.domain.feed.MindfulModuleType.QUOTE) {
            val decoQuotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (poster.accentColor and 0x00FFFFFF or 0x30000000).toInt()
                textSize = 260f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("“", 92f, 460f, decoQuotePaint)
        }

        // 6. Main Centerpiece (Typography)
        var cursorY = 480f

        // Headline / Module Subtitle
        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (poster.accentColor and 0x00FFFFFF or 0xD0000000).toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.20f
        }
        canvas.drawText(poster.headline, 96f, cursorY, headlinePaint)
        cursorY += 56f

        // Primary Text (Quote, Foreign Word, or Concept Title)
        val isQuote = poster.primaryText.startsWith("»")
        val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = if (isQuote) 56f else 68f
            typeface = if (isQuote) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val primaryLayout = buildStaticLayout(poster.primaryText, primaryPaint, (w - 192f).toInt())
        canvas.save()
        canvas.translate(96f, cursorY)
        primaryLayout.draw(canvas)
        canvas.restore()
        cursorY += primaryLayout.height + 40f

        // Secondary Text (Author, Phonetics & Translation, or Concept explanation)
        val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val secondaryLayout = buildStaticLayout(poster.secondaryText, secondaryPaint, (w - 192f).toInt())
        canvas.save()
        canvas.translate(96f, cursorY)
        secondaryLayout.draw(canvas)
        canvas.restore()

        // 7. Frosted Glass Bottom Takeaway Card
        val cardRect = RectF(96f, 1340f, w - 96f, 1740f)

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xC8141418.toInt()
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(cardRect, 32f, 32f, cardBgPaint)
        canvas.drawRoundRect(cardRect, 32f, 32f, cardBorderPaint)

        // Card Header Badge
        val takeawayBadgeText = "💡  SCHLÜSSEL-ERKENNTNIS & REFLEXION"
        val takeawayBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = poster.accentColor.toInt()
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.drawText(takeawayBadgeText, cardRect.left + 36f, cardRect.top + 60f, takeawayBadgePaint)

        // Card Divider
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1AFFFFFF
            strokeWidth = 1f
        }
        canvas.drawLine(cardRect.left + 36f, cardRect.top + 84f, cardRect.right - 36f, cardRect.top + 84f, dividerPaint)

        // Takeaway Text
        val takeawayTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDDFFFFFF.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val takeawayLayout = buildStaticLayout(
            poster.takeawayText,
            takeawayTextPaint,
            (cardRect.width() - 72f).toInt(),
        )
        canvas.save()
        canvas.translate(cardRect.left + 36f, cardRect.top + 110f)
        takeawayLayout.draw(canvas)
        canvas.restore()

        // 8. Footer Watermark
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            textSize = 20f
            letterSpacing = 0.20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("H I K A R I   ·   A P P L E - G R A D E   M I N D F U L N E S S", w / 2f, 1830f, footerPaint)
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1.15f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                1.15f,
                8f,
                false,
            )
        }
    }
}
