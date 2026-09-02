package com.hikari.app.ui.navigation

/**
 * Ein per Android-Teilen-Menü an Hikari geschickter Link.
 *
 * [nonce] unterscheidet zwei Teilvorgänge mit derselben URL: Compose-Effekte
 * feuern nur bei geändertem Schlüssel, und derselbe Link zweimal geteilt soll
 * das Import-Sheet trotzdem zweimal öffnen.
 */
data class SharedImport(val url: String, val nonce: Long)

/**
 * Erste http(s)-URL aus geteiltem Text. Browser schicken meist
 * "Seitentitel https://…", manche hängen Satzzeichen an — die gehören nicht
 * zur URL.
 */
fun extractSharedUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return Regex("""https?://\S+""").find(text)?.value
        ?.trimEnd('.', ',', ')', ';', '"', '\'')
        ?.takeIf { it.length > 10 }
}
