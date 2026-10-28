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
    val notificationTag: String? = null,
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
    val textLines: List<String> = emptyList(),
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
        """^(?:[^:\n]{1,60}:\s*)?(?:🚫\s*)?(?:""" +
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

    // Explizite Bearbeitungs-Kennzeichnungen im Text
    private val EXPLICIT_EDIT_PREFIX = Regex(
        """^(?:✏️\s*)?(?:bearbeitet|edited)\s*:\s*(.+)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val EXPLICIT_EDIT_SUFFIX = Regex(
        """^(.+?)\s*\((?:bearbeitet|edited|изменено)\)\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun stripInvisibleMarks(raw: String): String =
        raw.replace(INVISIBLE_MARKS_REGEX, "").trim()

    fun isSupportedPackage(packageName: String): Boolean =
        packageName in SUPPORTED_PACKAGES

    fun isDeletedPlaceholder(rawText: String): Boolean {
        val cleaned = stripInvisibleMarks(rawText)
        if (cleaned.isEmpty()) return false
        return DELETED_REGEX.matches(cleaned)
    }

    /**
     * Falls eine Zeile wie `"Sarah: 🚫 Diese Nachricht wurde gelöscht"` aufgebaut ist,
     * extrahiert diese Funktion den vorangestellten Absender `"Sarah"`.
     */
    fun extractSenderPrefixFromDeletedLine(rawText: String): String? {
        val cleaned = stripInvisibleMarks(rawText)
        if (!cleaned.contains(":")) return null
        val prefix = cleaned.substringBefore(":").trim()
        val rest = cleaned.substringAfter(":").trim()
        if (prefix.isNotEmpty() && prefix.length <= 60 && isDeletedPlaceholder(rest)) {
            return cleanChatTitle(prefix)
        }
        return null
    }

    fun isGenericAppTitle(title: String): Boolean {
        val t = stripInvisibleMarks(title)
        return t.equals("WhatsApp", ignoreCase = true) ||
            t.equals("WhatsApp Business", ignoreCase = true)
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
     * Prüft, ob der Text eine explizite "(Bearbeitet)"-Markierung trägt, und gibt
     * das Paar `(bereinigterText, isExplicitEdit)` zurück.
     */
    fun extractExplicitEdit(rawText: String): Pair<String, Boolean> {
        val trimmed = stripInvisibleMarks(rawText)
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

    /**
     * Prüft, ob eine Nachricht überhaupt in WhatsApp bearbeitbar ist.
     * Sprachnachrichten, Audios, Sticker, GIFs, Dokumente sowie reine Medien-Platzhalter
     * ohne Bildunterschrift ("📷 Foto", "🎥 Video") können in WhatsApp NICHT bearbeitet werden
     * und dürfen niemals als Vorher/Nachher einer Bearbeitung gewertet werden!
     */
    fun isEditableMessage(text: String, mediaType: String?): Boolean {
        if (mediaType in setOf("voice", "audio", "sticker", "gif", "document")) return false
        val cleaned = stripInvisibleMarks(text)
            .removePrefix("📷")
            .removePrefix("🎥")
            .trim()
        if (cleaned.isEmpty()) return false
        if (cleaned.equals("Foto", ignoreCase = true) ||
            cleaned.equals("Photo", ignoreCase = true) ||
            cleaned.equals("Bild", ignoreCase = true) ||
            cleaned.equals("Video", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    /**
     * Extrahiert alle einzelnen Nachrichten aus einem [RawNotificationPayload].
     */
    fun parse(payload: RawNotificationPayload): List<ParsedWhatsAppMessage> {
        if (!isSupportedPackage(payload.packageName)) return emptyList()
        if (payload.isOngoing) return emptyList()
        if (payload.category in setOf("call", "service", "progress", "status", "sys")) return emptyList()

        val rawTitle = stripInvisibleMarks(payload.title.orEmpty())
        val rawConvTitle = stripInvisibleMarks(payload.conversationTitle.orEmpty())
        val topText = stripInvisibleMarks(payload.text.orEmpty())
        val topBigText = stripInvisibleMarks(payload.bigText.orEmpty())

        // Summary-Notifications ("2 neue Nachrichten aus 2 Chats") ignorieren wir für normale
        // Nachrichten, ABER wenn darin eine Löschnachricht ("Diese Nachricht wurde gelöscht")
        // steckt, werten wir diese trotzdem aus!
        if (payload.isSummary && payload.messagingStyleItems.isEmpty()) {
            val hasDeletionInTop = isDeletedPlaceholder(topText) ||
                topBigText.lines().any { isDeletedPlaceholder(it) } ||
                payload.textLines.any { isDeletedPlaceholder(it) }
            if (!hasDeletionInTop) return emptyList()
        }

        // 1. Wenn MessagingStyle-Einträge vorhanden sind (Standard ab Android 7+ bei WhatsApp)
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

            val parsedItems = payload.messagingStyleItems.mapNotNull { item ->
                val body = stripInvisibleMarks(item.text)
                if (body.isEmpty() || isSystemNoise(resolvedChatTitle, body)) return@mapNotNull null

                val rawSender = stripInvisibleMarks(item.sender.orEmpty())
                val sender = rawSender.takeIf { it.isNotEmpty() }
                    ?: extractSenderPrefixFromDeletedLine(body)
                    ?: if (rawTitle.contains(":")) cleanChatTitle(rawTitle.substringAfter(":")) else resolvedChatTitle

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
                    notificationTag = payload.notificationTag,
                )
            }.toMutableList()

            // WICHTIG: Bei manchen WhatsApp-Versionen wird die gelöschte Nachricht aus
            // EXTRA_MESSAGES entfernt und "🚫 Diese Nachricht wurde gelöscht" steht NUR
            // in EXTRA_TEXT / EXTRA_BIG_TEXT / EXTRA_TEXT_LINES!
            val alreadyHasDeletion = parsedItems.any { it.isDeletionNotice }
            if (!alreadyHasDeletion) {
                val deletionLine = listOfNotNull(
                    topText.takeIf { isDeletedPlaceholder(it) },
                    topBigText.lines().map { stripInvisibleMarks(it) }.lastOrNull { isDeletedPlaceholder(it) },
                    payload.textLines.map { stripInvisibleMarks(it) }.lastOrNull { isDeletedPlaceholder(it) },
                ).firstOrNull()

                if (deletionLine != null) {
                    val fallbackSender = extractSenderPrefixFromDeletedLine(deletionLine)
                        ?: if (rawTitle.contains(":")) cleanChatTitle(rawTitle.substringAfter(":")) else resolvedChatTitle
                    parsedItems.add(
                        ParsedWhatsAppMessage(
                            chatKey = chatKey,
                            chatTitle = resolvedChatTitle,
                            senderName = fallbackSender.ifEmpty { resolvedChatTitle },
                            isGroup = isGroup,
                            packageName = payload.packageName,
                            text = deletionLine,
                            isDeletionNotice = true,
                            isExplicitEdit = false,
                            mediaType = null,
                            timestamp = payload.postTime,
                            notificationKey = payload.notificationKey,
                            notificationTag = payload.notificationTag,
                        ),
                    )
                }
            }

            return parsedItems
        }

        // 2. Fallback für klassische Einzel-/Inbox-Benachrichtigungen ohne EXTRA_MESSAGES
        //    Prüfe zuerst, ob EXTRA_TEXT direkt eine Löschnachricht ist (bevorzugt vor mehrzeiligem bigText!).
        val candidateBodies: List<String> = when {
            isDeletedPlaceholder(topText) -> listOf(topText)
            payload.textLines.isNotEmpty() -> payload.textLines.map { stripInvisibleMarks(it) }.filter { it.isNotEmpty() }
            topBigText.contains("\n") && topBigText.lines().any { isDeletedPlaceholder(it) } ->
                topBigText.lines().map { stripInvisibleMarks(it) }.filter { it.isNotEmpty() }
            else -> listOfNotNull((topBigText.takeIf { it.isNotEmpty() } ?: topText).takeIf { it.isNotEmpty() })
        }

        if (candidateBodies.isEmpty()) return emptyList()

        return candidateBodies.mapNotNull { rawBody ->
            if (isSystemNoise(rawTitle, rawBody)) return@mapNotNull null

            val isDel = isDeletedPlaceholder(rawBody)
            val prefixSender = if (isDel) extractSenderPrefixFromDeletedLine(rawBody) else null

            val isGroup: Boolean
            val chatTitle: String
            val senderName: String

            when {
                rawConvTitle.isNotEmpty() && !isGenericAppTitle(rawConvTitle) -> {
                    isGroup = true
                    chatTitle = cleanChatTitle(rawConvTitle)
                    senderName = prefixSender ?: cleanChatTitle(rawTitle).takeIf { !isGenericAppTitle(it) } ?: chatTitle
                }
                rawTitle.contains(":") && !rawTitle.startsWith("http", ignoreCase = true) -> {
                    isGroup = true
                    chatTitle = cleanChatTitle(rawTitle.substringBefore(":"))
                    senderName = prefixSender ?: cleanChatTitle(rawTitle.substringAfter(":")).ifEmpty { chatTitle }
                }
                isGenericAppTitle(rawTitle) && prefixSender != null -> {
                    isGroup = payload.isGroupConversation
                    chatTitle = prefixSender
                    senderName = prefixSender
                }
                else -> {
                    isGroup = payload.isGroupConversation
                    chatTitle = cleanChatTitle(rawTitle).ifEmpty { prefixSender.orEmpty() }
                    senderName = prefixSender ?: chatTitle
                }
            }

            if (chatTitle.isEmpty()) return@mapNotNull null

            val (cleanedText, explicitEdit) = if (isDel) rawBody to false else extractExplicitEdit(rawBody)
            val chatKey = buildChatKey(payload.packageName, chatTitle)

            ParsedWhatsAppMessage(
                chatKey = chatKey,
                chatTitle = chatTitle,
                senderName = senderName.ifEmpty { chatTitle },
                isGroup = isGroup,
                packageName = payload.packageName,
                text = cleanedText,
                isDeletionNotice = isDel,
                isExplicitEdit = explicitEdit,
                mediaType = if (isDel) null else detectMediaType(cleanedText),
                timestamp = payload.postTime,
                notificationKey = payload.notificationKey,
                notificationTag = payload.notificationTag,
            )
        }
    }

    fun buildChatKey(packageName: String, cleanChatTitle: String): String =
        "$packageName:${stripInvisibleMarks(cleanChatTitle).lowercase().trim()}"
}
