package com.hikari.app.domain.whatsapp

import java.net.URLEncoder

data class CountryCallingCode(
    val flag: String,
    val name: String,
    val dialCode: String,
)

object WhatsAppToolsHelper {

    val COUNTRY_CODES = listOf(
        CountryCallingCode("🇩🇪", "Deutschland", "+49"),
        CountryCallingCode("🇦🇹", "Österreich", "+43"),
        CountryCallingCode("🇨🇭", "Schweiz", "+41"),
        CountryCallingCode("🇹🇷", "Türkei", "+90"),
        CountryCallingCode("🇺🇸", "USA / Kanada", "+1"),
        CountryCallingCode("🇬🇧", "UK", "+44"),
        CountryCallingCode("🇫🇷", "Frankreich", "+33"),
        CountryCallingCode("🇮🇹", "Italien", "+39"),
        CountryCallingCode("🇪🇸", "Spanien", "+34"),
        CountryCallingCode("🇳🇱", "Niederlande", "+31"),
        CountryCallingCode("🇵🇱", "Polen", "+48"),
        CountryCallingCode("🇷🇺", "Russland / Kasachstan", "+7"),
    )

    /**
     * Bereinigt eine eingegebene Telefonnummer für `wa.me/<digits>`.
     * Gibt `null` zurück, wenn weniger als 6 Ziffern übrig bleiben.
     */
    fun normalizePhoneNumber(countryDialCode: String, rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Hat der Nutzer bereits eine internationale Vorwahl mit '+' oder '00' eingefügt?
        if (trimmed.startsWith("+")) {
            val digits = trimmed.filter { it.isDigit() }
            return digits.takeIf { it.length >= 7 }
        }
        if (trimmed.startsWith("00")) {
            val digits = trimmed.drop(2).filter { it.isDigit() }
            return digits.takeIf { it.length >= 7 }
        }

        val ccDigits = countryDialCode.filter { it.isDigit() }.ifEmpty { "49" }
        val localDigits = trimmed.filter { it.isDigit() }.trimStart('0')
        if (localDigits.length < 5) return null
        return ccDigits + localDigits
    }

    fun formatDisplayPhone(normalizedDigits: String): String =
        if (normalizedDigits.startsWith("+")) normalizedDigits else "+$normalizedDigits"

    fun buildWaMeUrl(normalizedDigits: String, message: String = ""): String {
        val cleanDigits = normalizedDigits.filter { it.isDigit() }
        val base = "https://wa.me/$cleanDigits"
        if (message.isBlank()) return base
        val encoded = URLEncoder.encode(message, "UTF-8")
        return "$base?text=$encoded"
    }

    // ── Text-Studio Formatierungen ───────────────────────────────────────────

    fun formatBold(text: String): String =
        text.lines().joinToString("\n") { line ->
            if (line.isBlank()) line else "*${line.trim()}*"
        }

    fun formatItalic(text: String): String =
        text.lines().joinToString("\n") { line ->
            if (line.isBlank()) line else "_${line.trim()}_"
        }

    fun formatStrikethrough(text: String): String =
        text.lines().joinToString("\n") { line ->
            if (line.isBlank()) line else "~${line.trim()}~"
        }

    fun formatMonospace(text: String): String =
        if (text.isBlank()) "" else "```${text.trim()}```"

    fun formatQuote(text: String): String =
        text.lines().joinToString("\n") { line ->
            if (line.isBlank()) ">" else "> $line"
        }

    /**
     * Erzeugt eine WhatsApp-Spoiler-Nachricht:
     * Durch ~3500 unsichtbare Zero-Width-Zeichen klappt WhatsApp die Nachricht
     * automatisch hinter „… Mehr lesen“ zu. Erst beim Antippen wird [secretText] sichtbar.
     */
    fun buildSpoilerMessage(teaserText: String, secretText: String): String {
        val teaser = teaserText.trim().ifEmpty { "🔒 Spoiler (tippe auf „Mehr lesen“)" }
        val secret = secretText.trim()
        val filler = "\u200B\u200C".repeat(1750)
        return "$teaser$filler\n\n$secret"
    }

    /**
     * Wiederholt [text] [count]-mal (maximal 500x).
     */
    fun repeatText(
        text: String,
        count: Int,
        newLine: Boolean = true,
        addNumbering: Boolean = false,
    ): String {
        if (text.isEmpty()) return ""
        val safeCount = count.coerceIn(1, 500)
        val sep = if (newLine) "\n" else " "
        return (1..safeCount).joinToString(sep) { idx ->
            if (addNumbering) "$idx. $text" else text
        }
    }

    /**
     * Erzeugt eine unsichtbare WhatsApp-Nachricht mit [lines] Zeilen.
     * Nutzt das Braille-Blank-Zeichen `\u2800`, das von WhatsApp beim Senden
     * nicht weggetrimmt wird.
     */
    fun buildInvisibleMessage(lines: Int = 1): String {
        val safeLines = lines.coerceIn(1, 50)
        return (1..safeLines).joinToString("\n") { "\u2800" }
    }

    // ── Unicode Schrift-Generator (für WhatsApp-Info, Gruppennamen & Nachrichten) ──

    enum class UnicodeFontStyle(val label: String, val preview: String) {
        BOLD_SANS("Fett Sans", "𝗛𝗶𝗸𝗮𝗿𝗶"),
        ITALIC_SANS("Kursiv Sans", "𝘏𝘪𝘬𝘢𝘳𝘪"),
        MONOSPACE("Monospace", "𝙷𝚒𝚔𝚊𝚛𝚒"),
        DOUBLE_STRUCK("Outline", "ℍ𝕚𝕜𝕒𝕣𝕚"),
        CIRCLED("Kreise", "Ⓗⓘⓚⓐⓡⓘ"),
        SMALL_CAPS("Kapitälchen", "Hɪᴋᴀʀɪ"),
    }

    private const val SMALL_CAPS_MAP = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ"

    fun applyUnicodeFont(text: String, style: UnicodeFontStyle): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            when (style) {
                UnicodeFontStyle.BOLD_SANS -> sb.append(mapMathFont(ch, 0x1D5D4, 0x1D5EE, 0x1D7EC))
                UnicodeFontStyle.ITALIC_SANS -> sb.append(mapMathFont(ch, 0x1D608, 0x1D622, null))
                UnicodeFontStyle.MONOSPACE -> sb.append(mapMathFont(ch, 0x1D670, 0x1D68A, 0x1D7F6))
                UnicodeFontStyle.DOUBLE_STRUCK -> sb.append(mapDoubleStruck(ch))
                UnicodeFontStyle.CIRCLED -> sb.append(mapCircled(ch))
                UnicodeFontStyle.SMALL_CAPS -> {
                    if (ch in 'a'..'z') {
                        sb.append(SMALL_CAPS_MAP[ch - 'a'])
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun mapMathFont(ch: Char, upperStart: Int, lowerStart: Int, digitStart: Int?): String {
        return when {
            ch in 'A'..'Z' -> String(Character.toChars(upperStart + (ch - 'A')))
            ch in 'a'..'z' -> String(Character.toChars(lowerStart + (ch - 'a')))
            digitStart != null && ch in '0'..'9' -> String(Character.toChars(digitStart + (ch - '0')))
            else -> ch.toString()
        }
    }

    private fun mapDoubleStruck(ch: Char): String {
        return when (ch) {
            'C' -> "ℂ"
            'H' -> "ℍ"
            'N' -> "ℕ"
            'P' -> "ℙ"
            'Q' -> "ℚ"
            'R' -> "ℝ"
            'Z' -> "ℤ"
            in 'A'..'Z' -> String(Character.toChars(0x1D538 + (ch - 'A')))
            in 'a'..'z' -> String(Character.toChars(0x1D552 + (ch - 'a')))
            in '0'..'9' -> String(Character.toChars(0x1D7D8 + (ch - '0')))
            else -> ch.toString()
        }
    }

    private fun mapCircled(ch: Char): String {
        return when (ch) {
            in 'A'..'Z' -> (0x24B6 + (ch - 'A')).toChar().toString()
            in 'a'..'z' -> (0x24D0 + (ch - 'a')).toChar().toString()
            '0' -> "⓪"
            in '1'..'9' -> (0x2460 + (ch - '1')).toChar().toString()
            else -> ch.toString()
        }
    }
}
