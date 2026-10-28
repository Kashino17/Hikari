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
    val isExplicitEdit: Boolean,
    val mediaType: String?,
    val timestamp: Long,
    val notificationKey: String?,
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
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val isSummary: Boolean,
    val isOngoing: Boolean,
    val category: String?,
    val postTime: Long,
    val messagingStyleItems: List<RawMessagingItem> = emptyList(),
)

data class RawMessagingItem(
    val sender: String?,
    val text: String,
    val timestamp: Long,
)

object WhatsAppNotificationParser {

    val SUPPORTED_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    // Muster für "Nachricht wurde für alle gelöscht" in DE, EN und weiteren verbreiteten Sprachen
    private val DELETED_PATTERNS = listOf(
        Regex("""^(?:🚫\s*)?diese nachricht wurde gelöscht\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?du hast diese nachricht gelöscht\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?this message was deleted\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?you deleted this message\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?este mensaje fue eliminado\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?ce message a été supprimé\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?questo messaggio è stato eliminato\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?bu mesaj silindi\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?это сообщение было удалено\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?esta mensagem foi apagada\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?dit bericht is verwijderd\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:🚫\s*)?ta wiadomość została usunięta\.?$""", RegexOption.IGNORE_CASE),
    )

    // System-/Hintergrund-Meldungen von WhatsApp, die keine echten Chat-Nachrichten sind
    private val SYSTEM_NOISE_PATTERNS = listOf(
        Regex("""^\d+\s+neue\s+nachrichten?$""", RegexOption.IGNORE_CASE),
        Regex("""^\d+\s+new\s+messages?$""", RegexOption.IGNORE_CASE),
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

    // Entfernt "(3 Nachrichten)" oder "(4 messages)" am Ende von Gruppennamen
    private val GROUP_COUNT_SUFFIX = Regex(
        """\s*\(\d+\s*(?:nachrichten?|messages?|новые?\s*сообщения?|mensajes?)\)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // Explizite Bearbeitungs-Kennzeichnungen im Text
    private val EXPLICIT_EDIT_PREFIX = Regex("""^(?:✏️\s*)?(?:bearbeitet|edited)\s*:\s*(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val EXPLICIT_EDIT_SUFFIX = Regex("""^(.+?)\s*\((?:bearbeitet|edited|изменено)\)\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun isSupportedPackage(packageName: String): Boolean =
        packageName in SUPPORTED_PACKAGES

    fun isDeletedPlaceholder(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return false
        return DELETED_PATTERNS.any { it.matches(trimmed) }
    }

    fun isSystemNoise(title: String, text: String): Boolean {
        val t = title.trim()
        val m = text.trim()
        if (t.isEmpty() || m.isEmpty()) return true
        if (t.equals("WhatsApp", ignoreCase = true) || t.equals("WhatsApp Business", ignoreCase = true)) {
            if (SYSTEM_NOISE_PATTERNS.any { it.matches(m) }) return true
        }
        return SYSTEM_NOISE_PATTERNS.any { it.matches(m) || it.matches(t) }
    }

    /**
     * Bereinigt Gruppentitel wie "WG Gruppe (3 Nachrichten)" -> "WG Gruppe".
     */
    fun cleanChatTitle(rawTitle: String): String =
        rawTitle.trim().replace(GROUP_COUNT_SUFFIX, "").trim()

    /**
     * Prüft, ob der Text eine explizite "(Bearbeitet)"-Markierung trägt, und gibt
     * das Paar `(bereinigterText, isExplicitEdit)` zurück.
     */
    fun extractExplicitEdit(rawText: String): Pair<String, Boolean> {
        val trimmed = rawText.trim()
        EXPLICIT_EDIT_PREFIX.matchEntire(trimmed)?.let { match ->
            return match.groupValues[1].trim() to true
        }
        EXPLICIT_EDIT_SUFFIX.matchEntire(trimmed)?.let { match ->
            return match.groupValues[1].trim() to true
        }
        return trimmed to false
    }

    /**
     * Erkennt anhand des typischen WhatsApp-Präfixes den Medientyp.
     */
    fun detectMediaType(text: String): String? {
        val t = text.trim()
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

    /**
     * Extrahiert alle einzelnen Nachrichten aus einem [RawNotificationPayload].
     */
    fun parse(payload: RawNotificationPayload): List<ParsedWhatsAppMessage> {
        if (!isSupportedPackage(payload.packageName)) return emptyList()
        if (payload.isSummary || payload.isOngoing) return emptyList()
        if (payload.category in setOf("call", "service", "progress", "status", "sys")) return emptyList()

        val rawTitle = payload.title?.trim().orEmpty()
        val rawConvTitle = payload.conversationTitle?.trim().orEmpty()

        // Wenn MessagingStyle-Einträge vorhanden sind (Standard ab Android 7+ bei WhatsApp)
        if (payload.messagingStyleItems.isNotEmpty()) {
            val baseGroupTitle = when {
                rawConvTitle.isNotEmpty() -> cleanChatTitle(rawConvTitle)
                rawTitle.contains(":") -> cleanChatTitle(rawTitle.substringBefore(":"))
                else -> cleanChatTitle(rawTitle)
            }
            val isGroup = payload.isGroupConversation || rawConvTitle.isNotEmpty() || rawTitle.contains(":")
            val resolvedChatTitle = baseGroupTitle.ifEmpty { cleanChatTitle(rawTitle) }
            if (resolvedChatTitle.isEmpty()) return emptyList()

            val chatKey = buildChatKey(payload.packageName, resolvedChatTitle)

            return payload.messagingStyleItems.mapNotNull { item ->
                val body = item.text.trim()
                if (body.isEmpty() || isSystemNoise(resolvedChatTitle, body)) return@mapNotNull null

                val sender = item.sender?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (rawTitle.contains(":")) rawTitle.substringAfter(":").trim() else resolvedChatTitle

                val isDel = isDeletedPlaceholder(body)
                val (cleanedText, explicitEdit) = if (isDel) body to false else extractExplicitEdit(body)

                ParsedWhatsAppMessage(
                    chatKey = chatKey,
                    chatTitle = resolvedChatTitle,
                    senderName = sender.ifEmpty { resolvedChatTitle },
                    isGroup = isGroup,
                    packageName = payload.packageName,
                    text = cleanedText,
                    isDeletionNotice = isDel,
                    isExplicitEdit = explicitEdit,
                    mediaType = if (isDel) null else detectMediaType(cleanedText),
                    timestamp = if (item.timestamp > 0L) item.timestamp else payload.postTime,
                    notificationKey = payload.notificationKey,
                )
            }
        }

        // Fallback für klassische Einzel-Benachrichtigungen ohne EXTRA_MESSAGES
        val rawBody = (payload.bigText?.takeIf { it.isNotBlank() } ?: payload.text)?.trim().orEmpty()
        if (isSystemNoise(rawTitle, rawBody)) return emptyList()

        val isGroup: Boolean
        val chatTitle: String
        val senderName: String

        when {
            rawConvTitle.isNotEmpty() -> {
                isGroup = true
                chatTitle = cleanChatTitle(rawConvTitle)
                senderName = cleanChatTitle(rawTitle).ifEmpty { chatTitle }
            }
            rawTitle.contains(":") && !rawTitle.startsWith("http", ignoreCase = true) -> {
                isGroup = true
                chatTitle = cleanChatTitle(rawTitle.substringBefore(":"))
                senderName = rawTitle.substringAfter(":").trim().ifEmpty { chatTitle }
            }
            else -> {
                isGroup = payload.isGroupConversation
                chatTitle = cleanChatTitle(rawTitle)
                senderName = chatTitle
            }
        }

        if (chatTitle.isEmpty()) return emptyList()

        val isDel = isDeletedPlaceholder(rawBody)
        val (cleanedText, explicitEdit) = if (isDel) rawBody to false else extractExplicitEdit(rawBody)
        val chatKey = buildChatKey(payload.packageName, chatTitle)

        return listOf(
            ParsedWhatsAppMessage(
                chatKey = chatKey,
                chatTitle = chatTitle,
                senderName = senderName,
                isGroup = isGroup,
                packageName = payload.packageName,
                text = cleanedText,
                isDeletionNotice = isDel,
                isExplicitEdit = explicitEdit,
                mediaType = if (isDel) null else detectMediaType(cleanedText),
                timestamp = payload.postTime,
                notificationKey = payload.notificationKey,
            ),
        )
    }

    fun buildChatKey(packageName: String, cleanChatTitle: String): String =
        "$packageName:${cleanChatTitle.lowercase().trim()}"
}
