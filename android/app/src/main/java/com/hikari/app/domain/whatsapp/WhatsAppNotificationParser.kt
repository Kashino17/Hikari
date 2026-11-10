package com.hikari.app.domain.whatsapp

/**
 * Einzelne aus einer WhatsApp-Benachrichtigung extrahierte Nachricht.
 */
data class ParsedWhatsAppMessage(
    val chatKey: String,
    val chatTitle: String,
    val senderName: String,
    val isGroup: Boolean,
    val packageName: String,
    val text: String,
    val isDeletionNotice: Boolean,
    val mediaType: String?,
    val timestamp: Long,
    val notificationKey: String?,
    val notificationTag: String? = null,
    /** Bild-Anhang direkt aus der Benachrichtigung (content://), falls WhatsApp einen mitliefert. */
    val dataUri: String? = null,
    val dataMimeType: String? = null,
)

/**
 * Rohdaten einer Benachrichtigung (entkoppelt von Android-Bundle für 100%ige Unit-Testbarkeit).
 */
data class RawNotificationPayload(
    val packageName: String,
    val notificationKey: String?,
    val notificationTag: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val summaryText: String? = null,
    val tickerText: String? = null,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val isSummary: Boolean,
    val isOngoing: Boolean,
    val category: String?,
    val postTime: Long,
    val messagingStyleItems: List<RawMessagingItem> = emptyList(),
    val textLines: List<String> = emptyList(),
)

data class RawMessagingItem(
    /** `null` = eigene Nachricht (Antwort direkt aus der Benachrichtigung). */
    val sender: String?,
    val text: String,
    val timestamp: Long,
    val dataUri: String? = null,
    val dataMimeType: String? = null,
)

object WhatsAppNotificationParser {

    val SUPPORTED_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    /**
     * Unsichtbare Unicode-Steuerzeichen, die WhatsApp regelmäßig in Push-Benachrichtigungen
     * vor Emojis (z. B. `\u200E` vor `🚫`, `\uFE0F` nach `🚫`) oder um Telefonnummern/Namen
     * (`\u202A`..`\u202C`, `\u2066`..`\u2069`) einfügt.
     * Normales `.trim()` in Kotlin entfernt diese Zeichen NICHT!
     */
    private val INVISIBLE_MARKS_REGEX = Regex(
        """[\u200B-\u200F\u202A-\u202E\u2060-\u206F\uFE00-\uFE0F\uFEFF]""",
    )

    // Muster für "Nachricht wurde für alle gelöscht" in DE, EN und weiteren verbreiteten Sprachen.
    // Erlaubt optional auch ein vorangestelltes "Absender: " sowie "🚫".
    private val DELETED_REGEX = Regex(
        """^(?:[^:\n]{1,80}:\s*)*(?:🚫\s*)?(?:""" +
            """diese nachricht wurde (?:für alle |vom absender )?gelöscht|""" +
            """du hast diese nachricht gelöscht|""" +
            """nachricht wurde gelöscht|""" +
            """this message was deleted|""" +
            """you deleted this message|""" +
            """message was deleted|""" +
            """este mensaje fue eliminado|eliminaste este mensaje|""" +
            """ce message a été supprimé|vous avez supprimé ce message|""" +
            """questo messaggio è stato eliminato|hai eliminato questo messaggio|""" +
            """bu mesaj silindi|bu mesajı sildiniz|""" +
            """это сообщение было удалено|вы удалили это сообщение|""" +
            """esta mensagem foi apagada|mensagem apagada|""" +
            """dit bericht is verwijderd|je hebt dit bericht verwijderd|""" +
            """ta wiadomość została usunięta|usunięto wiadomość|""" +
            """تم حذف هذه الرسالة""" +
            """)\.?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val DELETED_CONTAINS_REGEX = Regex(
        """(?:""" +
            """diese nachricht wurde (?:für alle |vom absender |von [^\n.]{1,50} )?gelöscht|""" +
            """du hast diese nachricht gelöscht|""" +
            """this message was deleted|""" +
            """you deleted this message|""" +
            """este mensaje fue eliminado|eliminaste este mensaje|""" +
            """ce message a été supprimé|vous avez supprimé ce message|""" +
            """questo messaggio è stato eliminato|hai eliminato questo messaggio|""" +
            """bu mesaj silindi|bu mesajı sildiniz|""" +
            """это сообщение было удалено|вы удалили это сообщение|""" +
            """esta mensagem foi apagada|""" +
            """dit bericht is verwijderd|je hebt dit bericht verwijderd|""" +
            """ta wiadomość została usunięta|usunięto wiadomość|""" +
            """تم حذف هذه الرسالة""" +
            """)""",
        RegexOption.IGNORE_CASE,
    )

    // System-/Hintergrund-Meldungen von WhatsApp, die keine echten Chat-Nachrichten sind
    private val SYSTEM_NOISE_PATTERNS = listOf(
        Regex("""^\d+\s+(?:neue\s+|ungelesene\s+)?nachrichten?$""", RegexOption.IGNORE_CASE),
        Regex("""^\d+\s+(?:new\s+|unread\s+)?messages?$""", RegexOption.IGNORE_CASE),
        Regex("""^\d+\s+nachrichten?\s+aus\s+\d+\s+chats?$""", RegexOption.IGNORE_CASE),
        Regex("""^\d+\s+messages?\s+from\s+\d+\s+chats?$""", RegexOption.IGNORE_CASE),
        Regex("""^suche nach neuen nachrichten\.{0,3}$""", RegexOption.IGNORE_CASE),
        Regex("""^checking for new messages\.{0,3}$""", RegexOption.IGNORE_CASE),
        Regex("""^whatsapp web\b.*""", RegexOption.IGNORE_CASE),
        Regex("""^backup\s+(läuft|wird\s+erstellt|in\s+progress).*""", RegexOption.IGNORE_CASE),
        Regex("""^(eingehender|laufender|verpasster)\s+(sprach|video)anruf.*""", RegexOption.IGNORE_CASE),
        Regex("""^(incoming|ongoing|missed)\s+(voice|video)\s+call.*""", RegexOption.IGNORE_CASE),
        Regex("""^nachrichten werden (wiederhergestellt|entschlüsselt).*""", RegexOption.IGNORE_CASE),
    )

    // Entfernt "(3 Nachrichten)", "(2 neue Nachrichten)" oder "(4 messages)" am Ende von Chatnamen
    private val GROUP_COUNT_SUFFIX = Regex(
        """\s*\(\d+\s*(?:(?:neue|ungelesene|new|unread)\s+)?(?:nachrichten?|messages?|новые?\s*сообщения?|mensajes?|mesaj|wiadomości|chats?)\)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // Reaktionen ("Hat mit ❤️ auf „…“ reagiert") sind keine Nachrichten. Wird eine Reaktion
    // zurückgenommen, verschwindet die Zeile aus dem Stapel — das darf nie als Löschung zählen.
    private val REACTION_REGEX = Regex(
        """^(?:hat mit .{1,16} auf .+ reagiert\.?|reagierte mit .{1,16} auf\b.+|reacted .{1,16} to\b.+)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // Übergangstexte, die WhatsApp später selbst durch den echten Inhalt ersetzt.
    private val TRANSIENT_TEXT_REGEX = Regex(
        """^(?:warte(?:n)? auf diese nachricht\b.*|waiting for this message\b.*)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // Reine Medien-Bezeichnung ohne Bildunterschrift: "Foto", "2 Fotos", "Video", "Sprachnachricht (0:12)".
    private val BARE_MEDIA_LABEL_REGEX = Regex(
        """^(?:\d+\s+)?(?:fotos?|photos?|bild(?:er)?|videos?|gifs?|sticker|audios?|""" +
            """sprachnachricht(?:en)?|voice messages?|videonachricht(?:en)?|video messages?|""" +
            """dokument(?:e)?|documents?)(?:\s*\(\d{1,2}:\d{2}(?::\d{2})?\))?$""",
        RegexOption.IGNORE_CASE,
    )

    private val MEDIA_EMOJI_PREFIXES = listOf("📷", "🎥", "🎤", "🎵", "🎧", "💟", "👾", "📄", "📹")

    fun stripInvisibleMarks(raw: String): String =
        raw.replace(INVISIBLE_MARKS_REGEX, "").trim()

    fun isSupportedPackage(packageName: String): Boolean =
        packageName in SUPPORTED_PACKAGES

    fun isDeletedPlaceholder(rawText: String): Boolean {
        val cleaned = stripInvisibleMarks(rawText)
        if (cleaned.isEmpty()) return false
        if (DELETED_REGEX.matches(cleaned)) return true
        return cleaned.length <= 160 && DELETED_CONTAINS_REGEX.containsMatchIn(cleaned)
    }

    /**
     * Falls eine Zeile wie `"Sarah: 🚫 Diese Nachricht wurde gelöscht"` oder
     * `"Gruppe @ Sarah: 🚫 Diese Nachricht wurde gelöscht"` aufgebaut ist,
     * extrahiert diese Funktion den vorangestellten Absender `"Sarah"`.
     */
    fun extractSenderPrefixFromDeletedLine(rawText: String): String? {
        val cleaned = stripInvisibleMarks(rawText)
        if (!cleaned.contains(":")) return null
        val prefix = cleaned.substringBeforeLast(":").trim()
        val rest = cleaned.substringAfterLast(":").trim()
        if (prefix.isNotEmpty() && prefix.length <= 80 && isDeletedPlaceholder(rest)) {
            val resolved = when {
                prefix.contains("@") -> prefix.substringAfterLast("@").trim()
                prefix.contains(":") -> prefix.substringAfterLast(":").trim()
                else -> prefix
            }
            return cleanChatTitle(resolved).takeIf { it.isNotEmpty() }
        }
        return null
    }

    fun isGenericAppTitle(title: String): Boolean {
        val t = stripInvisibleMarks(title)
        return t.equals("WhatsApp", ignoreCase = true) ||
            t.equals("WhatsApp Business", ignoreCase = true)
    }

    fun isGenericOrSummaryTitle(title: String): Boolean {
        val t = stripInvisibleMarks(title)
        if (t.isEmpty() || isGenericAppTitle(t)) return true
        return SYSTEM_NOISE_PATTERNS.any { it.matches(t) }
    }

    fun isSystemNoise(title: String, text: String): Boolean {
        val t = stripInvisibleMarks(title)
        val m = stripInvisibleMarks(text)
        if (m.isEmpty()) return true
        // Eine Löschnachricht ist NIEMALS System-Noise!
        if (isDeletedPlaceholder(m)) return false
        if (t.isEmpty()) return true
        if (isGenericAppTitle(t)) {
            if (SYSTEM_NOISE_PATTERNS.any { it.matches(m) }) return true
        }
        return SYSTEM_NOISE_PATTERNS.any { it.matches(m) || it.matches(t) }
    }

    /**
     * Bereinigt Gruppentitel wie "WG Gruppe (3 Nachrichten)" -> "WG Gruppe".
     */
    fun cleanChatTitle(rawTitle: String): String =
        stripInvisibleMarks(rawTitle).replace(GROUP_COUNT_SUFFIX, "").trim()

    /**
     * Erkennt anhand des typischen WhatsApp-Präfixes den Medientyp.
     */
    fun detectMediaType(text: String): String? {
        val t = stripInvisibleMarks(text)
        return when {
            t.startsWith("📷") || t.equals("Foto", ignoreCase = true) || t.equals("Photo", ignoreCase = true) || t.equals("Bild", ignoreCase = true) -> "photo"
            t.startsWith("🎤") || t.contains("Sprachnachricht", ignoreCase = true) || t.contains("Voice message", ignoreCase = true) -> "voice"
            t.startsWith("🎥") || t.equals("Video", ignoreCase = true) -> "video"
            t.startsWith("🎵") || t.startsWith("🎧") || t.equals("Audio", ignoreCase = true) -> "audio"
            t.startsWith("💟") || t.equals("Sticker", ignoreCase = true) -> "sticker"
            t.startsWith("👾") || t.equals("GIF", ignoreCase = true) -> "gif"
            t.startsWith("📄") || t.contains("Dokument", ignoreCase = true) || t.contains("Document", ignoreCase = true) -> "document"
            else -> null
        }
    }

    fun isReaction(text: String): Boolean = REACTION_REGEX.matches(stripInvisibleMarks(text))

    fun isTransientSystemText(text: String): Boolean =
        TRANSIENT_TEXT_REGEX.matches(stripInvisibleMarks(text))

    /**
     * True für reine Medien-Bezeichnungen ohne eigenen Text: "📷 Foto", "📷 3 Fotos", "🎥 Video",
     * "🎤 Sprachnachricht (0:12)". Schickt jemand mehrere Bilder hintereinander, fasst WhatsApp
     * sie in der Benachrichtigung zusammen ("📷 Foto" -> "📷 2 Fotos") — das ist keine Bearbeitung.
     */
    fun isBareMediaLabel(text: String): Boolean {
        var cleaned = stripInvisibleMarks(text)
        val hadEmoji = MEDIA_EMOJI_PREFIXES.any { cleaned.startsWith(it) }
        for (prefix in MEDIA_EMOJI_PREFIXES) cleaned = cleaned.removePrefix(prefix)
        cleaned = cleaned.trim()
        if (cleaned.isEmpty()) return hadEmoji
        return BARE_MEDIA_LABEL_REGEX.matches(cleaned)
    }

    /**
     * Kann dieser Text das Vorher oder Nachher einer echten Bearbeitung sein?
     * In WhatsApp lassen sich nur Texte und Bildunterschriften bearbeiten — keine
     * Sprachnachrichten, Sticker, GIFs, Dokumente und keine Medien ohne Unterschrift.
     */
    fun isEditableMessage(text: String, mediaType: String?): Boolean {
        if (mediaType in setOf("voice", "audio", "sticker", "gif", "document")) return false
        if (isBareMediaLabel(text) || isTransientSystemText(text) || isDeletedPlaceholder(text)) return false
        return stripInvisibleMarks(text).isNotEmpty()
    }

    /**
     * Zerlegt eine WhatsApp-Benachrichtigung in ihren Nachrichtenstapel.
     *
     * Ausgewertet werden ausschließlich Chat-Benachrichtigungen mit MessagingStyle: nur dort trägt
     * jede Nachricht Absender und Zeitstempel, also eine belastbare Identität. Sammel-
     * Benachrichtigungen ("54 Nachrichten aus 11 Chats") wiederholen nur, was die Chat-
     * Benachrichtigungen ohnehin enthalten, und werden ignoriert.
     */
    fun parse(payload: RawNotificationPayload): List<ParsedWhatsAppMessage> {
        if (!isSupportedPackage(payload.packageName)) return emptyList()
        if (payload.isOngoing || payload.isSummary) return emptyList()
        if (payload.category in setOf("call", "service", "progress", "status", "sys")) return emptyList()
        if (payload.messagingStyleItems.isEmpty()) return emptyList()

        val rawTitle = stripInvisibleMarks(payload.title.orEmpty())
        val rawConvTitle = stripInvisibleMarks(payload.conversationTitle.orEmpty())
        val isGroup = payload.isGroupConversation ||
            (rawConvTitle.isNotEmpty() && !isGenericOrSummaryTitle(rawConvTitle))

        val firstSender = payload.messagingStyleItems.firstNotNullOfOrNull { item ->
            cleanChatTitle(item.sender.orEmpty()).takeIf { it.isNotEmpty() && !isGenericOrSummaryTitle(it) }
        }
        val chatTitle = when {
            rawConvTitle.isNotEmpty() && !isGenericOrSummaryTitle(rawConvTitle) -> cleanChatTitle(rawConvTitle)
            isGroup && rawTitle.contains(":") -> cleanChatTitle(rawTitle.substringBefore(":"))
            else -> cleanChatTitle(rawTitle)
        }.takeIf { it.isNotEmpty() && !isGenericOrSummaryTitle(it) }
            ?: firstSender
            ?: return emptyList()

        val chatKey = buildChatKey(payload.packageName, payload.notificationTag, chatTitle)

        val parsed = payload.messagingStyleItems.mapNotNull { item ->
            // Ohne Absender = eigene Antwort aus der Benachrichtigung heraus -> nicht verfolgen.
            val sender = cleanChatTitle(item.sender.orEmpty())
            if (sender.isEmpty() || isGenericOrSummaryTitle(sender)) return@mapNotNull null

            val body = stripInvisibleMarks(item.text)
            if (body.isEmpty() || isReaction(body)) return@mapNotNull null
            val isDel = isDeletedPlaceholder(body)
            if (!isDel && isSystemNoise(chatTitle, body)) return@mapNotNull null

            ParsedWhatsAppMessage(
                chatKey = chatKey,
                chatTitle = chatTitle,
                senderName = sender,
                isGroup = isGroup,
                packageName = payload.packageName,
                text = body,
                isDeletionNotice = isDel,
                mediaType = if (isDel) null else detectMediaType(body),
                timestamp = if (item.timestamp > 0L) item.timestamp else payload.postTime,
                notificationKey = payload.notificationKey,
                notificationTag = payload.notificationTag,
                dataUri = item.dataUri.takeIf { !isDel },
                dataMimeType = item.dataMimeType.takeIf { !isDel },
            )
        }.toMutableList()

        // Manche WhatsApp-Versionen nehmen die gelöschte Nachricht aus dem Stapel und zeigen den
        // Platzhalter nur noch als Benachrichtigungstext. Dann als eigene Stapelzeile nachtragen.
        if (parsed.none { it.isDeletionNotice }) {
            val topLevel = listOf(payload.text, payload.tickerText, payload.summaryText)
                .map { stripInvisibleMarks(it.orEmpty()) }
                .firstOrNull { isDeletedPlaceholder(it) }
            if (topLevel != null) {
                val sender = extractSenderPrefixFromDeletedLine(topLevel)
                    ?: parsed.lastOrNull()?.senderName
                    ?: firstSender
                    ?: chatTitle
                parsed += ParsedWhatsAppMessage(
                    chatKey = chatKey,
                    chatTitle = chatTitle,
                    senderName = sender,
                    isGroup = isGroup,
                    packageName = payload.packageName,
                    text = topLevel,
                    isDeletionNotice = true,
                    mediaType = null,
                    timestamp = payload.postTime,
                    notificationKey = payload.notificationKey,
                    notificationTag = payload.notificationTag,
                )
            }
        }

        return parsed
    }

    /**
     * Stabile Chat-Kennung. WhatsApp vergibt pro Chat einen festen Benachrichtigungs-Tag
     * (ein Hash der Chat-ID) — der bleibt gleich, auch wenn die Gruppe umbenannt wird oder
     * zwei Kontakte denselben Namen tragen. Der Titel ist nur die Notlösung.
     */
    fun buildChatKey(packageName: String, notificationTag: String?, cleanChatTitle: String): String {
        val tag = notificationTag?.trim().orEmpty()
        return if (tag.isNotEmpty()) {
            "$packageName#$tag"
        } else {
            "$packageName:${stripInvisibleMarks(cleanChatTitle).lowercase().trim()}"
        }
    }
}
