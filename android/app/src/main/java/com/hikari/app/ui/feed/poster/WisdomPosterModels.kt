package com.hikari.app.ui.feed.poster

import androidx.compose.ui.graphics.Color
import com.hikari.app.domain.feed.ArtCultureCardItem
import com.hikari.app.domain.feed.BrainPuzzleCardItem
import com.hikari.app.domain.feed.BreathworkCardItem
import com.hikari.app.domain.feed.FinanceCardItem
import com.hikari.app.domain.feed.GeographyCardItem
import com.hikari.app.domain.feed.HistoryCardItem
import com.hikari.app.domain.feed.LanguageCardItem
import com.hikari.app.domain.feed.MentalModelCardItem
import com.hikari.app.domain.feed.MindfulCard
import com.hikari.app.domain.feed.MindfulModuleType
import com.hikari.app.domain.feed.PhilosophyCardItem
import com.hikari.app.domain.feed.QuoteCardItem
import com.hikari.app.domain.feed.ScienceCardItem
import com.hikari.app.domain.feed.SpeedMathCardItem
import com.hikari.app.domain.feed.VocabularyCardItem
import com.hikari.app.domain.feed.resolveDeepDive

enum class PosterLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String,
) {
    GERMAN("de", "Deutsch", "🇩🇪"),
    ENGLISH("en", "English", "🇬🇧"),
    RUSSIAN("ru", "Русский", "🇷🇺"),
    SPANISH("es", "Español", "🇪🇸");

    companion object {
        fun fromCode(code: String): PosterLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: GERMAN
    }
}

data class PosterData(
    val categoryLabel: String,
    val categoryEmoji: String,
    val headline: String,
    val primaryText: String,
    val secondaryText: String,
    val takeawayText: String,
    val attribution: String? = null,
    val moduleType: MindfulModuleType,
    val language: PosterLanguage,
    val accentColor: Long, // 0xAARRGGBB
    val gradientTop: Long,
    val gradientMid: Long,
    val gradientBottom: Long,
)

object PosterContentResolver {

    fun resolve(card: MindfulCard, targetLang: PosterLanguage): PosterData {
        val deepDive = card.resolveDeepDive()

        val (accent, gTop, gMid, gBottom) = when (card.type) {
            MindfulModuleType.QUOTE -> Quad(0xFFFBBF24, 0xFF140F0A, 0xFF28190B, 0xFF0A0806)
            MindfulModuleType.LANGUAGE -> Quad(0xFF10B981, 0xFF051713, 0xFF0C2E25, 0xFF040D0B)
            MindfulModuleType.BRAIN_PUZZLE -> Quad(0xFF3B82F6, 0xFF061421, 0xFF0D2840, 0xFF040A10)
            MindfulModuleType.MENTAL_MODEL -> Quad(0xFF8B5CF6, 0xFF120A24, 0xFF241447, 0xFF080412)
            MindfulModuleType.HISTORY -> Quad(0xFFEC4899, 0xFF1C0812, 0xFF350F22, 0xFF0A0307)
            MindfulModuleType.SCIENCE -> Quad(0xFF14B8A6, 0xFF041718, 0xFF0A2E30, 0xFF030D0E)
            MindfulModuleType.FINANCE -> Quad(0xFFF59E0B, 0xFF171106, 0xFF2E220C, 0xFF0B0803)
            MindfulModuleType.PHILOSOPHY -> Quad(0xFFE11D48, 0xFF1C080E, 0xFF38101C, 0xFF0B0305)
            MindfulModuleType.GEOGRAPHY -> Quad(0xFF6366F1, 0xFF0B0C24, 0xFF181A47, 0xFF050512)
            MindfulModuleType.SPEED_MATH -> Quad(0xFFEF4444, 0xFF1C0909, 0xFF381212, 0xFF0C0404)
            MindfulModuleType.ART_CULTURE -> Quad(0xFFA855F7, 0xFF160824, 0xFF2B0F47, 0xFF090312)
            MindfulModuleType.VOCABULARY -> Quad(0xFFF97316, 0xFF1A0D05, 0xFF331A0B, 0xFF0B0502)
            MindfulModuleType.BREATHWORK -> Quad(0xFF06B6D4, 0xFF04161C, 0xFF082C38, 0xFF030D10)
        }

        val categoryEmoji = when (card.type) {
            MindfulModuleType.QUOTE -> "📜"
            MindfulModuleType.LANGUAGE -> "🗣️"
            MindfulModuleType.BRAIN_PUZZLE -> "🧩"
            MindfulModuleType.MENTAL_MODEL -> "💡"
            MindfulModuleType.HISTORY -> "🏛️"
            MindfulModuleType.SCIENCE -> "🔬"
            MindfulModuleType.FINANCE -> "💰"
            MindfulModuleType.PHILOSOPHY -> "⚖️"
            MindfulModuleType.GEOGRAPHY -> "🌍"
            MindfulModuleType.SPEED_MATH -> "🔢"
            MindfulModuleType.ART_CULTURE -> "🎨"
            MindfulModuleType.VOCABULARY -> "📚"
            MindfulModuleType.BREATHWORK -> "🫁"
        }

        val categoryLabel = when (targetLang) {
            PosterLanguage.GERMAN -> when (card.type) {
                MindfulModuleType.QUOTE -> "ZITAT & LEBENSWEISHEIT"
                MindfulModuleType.LANGUAGE -> "SPRACHLERNEN & VOKABEL"
                MindfulModuleType.BRAIN_PUZZLE -> "KOGNITIONS-RÄTSEL"
                MindfulModuleType.MENTAL_MODEL -> "KRITISCHES DENKEN"
                MindfulModuleType.HISTORY -> "HEUTE IN DER GESCHICHTE"
                MindfulModuleType.SCIENCE -> "WISSENSCHAFTS-HAPPEN"
                MindfulModuleType.FINANCE -> "FINANZIELLE BILDUNG"
                MindfulModuleType.PHILOSOPHY -> "PHILOSOPHISCHES DILEMMA"
                MindfulModuleType.GEOGRAPHY -> "WELTATLAS & GEOGRAFIE"
                MindfulModuleType.SPEED_MATH -> "KOPFRECHNEN-SHORTCUT"
                MindfulModuleType.ART_CULTURE -> "KUNST & KULTUR"
                MindfulModuleType.VOCABULARY -> "WORTSCHATZ-MEISTER"
                MindfulModuleType.BREATHWORK -> "ACHTSAMKEIT & ATEM"
            }
            PosterLanguage.ENGLISH -> when (card.type) {
                MindfulModuleType.QUOTE -> "DAILY WISDOM & QUOTE"
                MindfulModuleType.LANGUAGE -> "LANGUAGE LEARNING"
                MindfulModuleType.BRAIN_PUZZLE -> "COGNITIVE PUZZLE"
                MindfulModuleType.MENTAL_MODEL -> "MENTAL MODEL"
                MindfulModuleType.HISTORY -> "TODAY IN HISTORY"
                MindfulModuleType.SCIENCE -> "SCIENCE BITE"
                MindfulModuleType.FINANCE -> "FINANCIAL LITERACY"
                MindfulModuleType.PHILOSOPHY -> "PHILOSOPHICAL DILEMMA"
                MindfulModuleType.GEOGRAPHY -> "WORLD ATLAS"
                MindfulModuleType.SPEED_MATH -> "MENTAL MATH SHORTCUT"
                MindfulModuleType.ART_CULTURE -> "ART & CULTURE"
                MindfulModuleType.VOCABULARY -> "VOCABULARY MASTER"
                MindfulModuleType.BREATHWORK -> "MINDFUL BREATHWORK"
            }
            PosterLanguage.RUSSIAN -> when (card.type) {
                MindfulModuleType.QUOTE -> "МУДРОСТЬ И ЦИТАТА ДНЯ"
                MindfulModuleType.LANGUAGE -> "ИЗУЧЕНИЕ ЯЗЫКА"
                MindfulModuleType.BRAIN_PUZZLE -> "КОГНИТИВНАЯ ГОЛОВОЛОМКА"
                MindfulModuleType.MENTAL_MODEL -> "МЕНТАЛЬНАЯ МОДЕЛЬ"
                MindfulModuleType.HISTORY -> "ЭТОТ ДЕНЬ В ИСТОРИИ"
                MindfulModuleType.SCIENCE -> "НАУЧНЫЙ ФАКТ"
                MindfulModuleType.FINANCE -> "ФИНАНСОВАЯ ГРАМОТНОСТЬ"
                MindfulModuleType.PHILOSOPHY -> "ФИЛОСОФСКАЯ ДИЛЕММА"
                MindfulModuleType.GEOGRAPHY -> "ГЕОГРАФИЯ И АТЛАС"
                MindfulModuleType.SPEED_MATH -> "БЫСТРЫЙ СЧЕТ В УМЕ"
                MindfulModuleType.ART_CULTURE -> "ИСКУССТВО И КУЛЬТУРА"
                MindfulModuleType.VOCABULARY -> "СЛОВАРНЫЙ ЗАПАС"
                MindfulModuleType.BREATHWORK -> "ОСОЗНАННОЕ ДЫХАНИЕ"
            }
            PosterLanguage.SPANISH -> when (card.type) {
                MindfulModuleType.QUOTE -> "CITA Y SABIDURÍA DIARIA"
                MindfulModuleType.LANGUAGE -> "APRENDIZAJE DE IDIOMAS"
                MindfulModuleType.BRAIN_PUZZLE -> "ACERTIJO COGNITIVO"
                MindfulModuleType.MENTAL_MODEL -> "MODELO MENTAL"
                MindfulModuleType.HISTORY -> "HOY EN LA HISTORIA"
                MindfulModuleType.SCIENCE -> "CIENCIA COTIDIANA"
                MindfulModuleType.FINANCE -> "EDUCACIÓN FINANCIERA"
                MindfulModuleType.PHILOSOPHY -> "DILEMA FILOSÓFICO"
                MindfulModuleType.GEOGRAPHY -> "ATLAS MUNDIAL"
                MindfulModuleType.SPEED_MATH -> "TRUCOS MATEMÁTICOS"
                MindfulModuleType.ART_CULTURE -> "ARTE Y CULTURA"
                MindfulModuleType.VOCABULARY -> "MAESTRO DE VOCABULARIO"
                MindfulModuleType.BREATHWORK -> "RESPIRACIÓN CONSCIENTE"
            }
        }

        // Translation of content based on card type and language
        return when (card) {
            is QuoteCardItem -> {
                val (quoteText, authorInfo, takeaway) = when (targetLang) {
                    PosterLanguage.GERMAN -> Triple(card.quote, "— ${card.author} · ${card.contextEra}", card.reflectionPrompt)
                    PosterLanguage.ENGLISH -> Triple(
                        translateQuoteEn(card.author, card.quote),
                        "— ${card.author} · ${card.contextEra}",
                        "Reflect: How does this truth apply to your inner peace today?",
                    )
                    PosterLanguage.RUSSIAN -> Triple(
                        translateQuoteRu(card.author, card.quote),
                        "— ${card.author} · ${card.contextEra}",
                        "Подумай: Как эта мудрость помогает сохранить спокойствие сегодня?",
                    )
                    PosterLanguage.SPANISH -> Triple(
                        translateQuoteEs(card.author, card.quote),
                        "— ${card.author} · ${card.contextEra}",
                        "Reflexiona: ¿Cómo puedes aplicar esta verdad a tu serenidad hoy?",
                    )
                }
                PosterData(
                    categoryLabel = categoryLabel,
                    categoryEmoji = categoryEmoji,
                    headline = "TÄGLICHE INSPIRATION",
                    primaryText = "»$quoteText«",
                    secondaryText = authorInfo,
                    takeawayText = takeaway,
                    attribution = card.author,
                    moduleType = card.type,
                    language = targetLang,
                    accentColor = accent,
                    gradientTop = gTop,
                    gradientMid = gMid,
                    gradientBottom = gBottom,
                )
            }
            is LanguageCardItem -> {
                val translationText = when (targetLang) {
                    PosterLanguage.GERMAN -> card.nativeTranslation
                    PosterLanguage.ENGLISH -> translateWordEn(card.foreignWord, card.nativeTranslation)
                    PosterLanguage.RUSSIAN -> "Оригинал: ${card.foreignWord}"
                    PosterLanguage.SPANISH -> translateWordEs(card.foreignWord, card.nativeTranslation)
                }
                val exampleText = when (targetLang) {
                    PosterLanguage.GERMAN -> "»${card.exampleForeign}«\n${card.exampleTranslation}"
                    PosterLanguage.ENGLISH -> "»${card.exampleForeign}«\nExample in practice"
                    PosterLanguage.RUSSIAN -> "»${card.exampleForeign}«\nПример использования в речи"
                    PosterLanguage.SPANISH -> "»${card.exampleForeign}«\nEjemplo práctico en contexto"
                }
                PosterData(
                    categoryLabel = categoryLabel,
                    categoryEmoji = categoryEmoji,
                    headline = "SPRACHE DES TAGES",
                    primaryText = card.foreignWord,
                    secondaryText = "${card.phonetic}  ·  $translationText",
                    takeawayText = "$exampleText\n\n💡 Kontext: ${deepDive.keyInsight}",
                    attribution = "${card.language.title} · Hikari Language Mastery",
                    moduleType = card.type,
                    language = targetLang,
                    accentColor = accent,
                    gradientTop = gTop,
                    gradientMid = gMid,
                    gradientBottom = gBottom,
                )
            }
            is MentalModelCardItem -> {
                val (title, explain, defense) = when (targetLang) {
                    PosterLanguage.GERMAN -> Triple(card.modelName, card.explanation, "Anwendung: ${card.actionableDefense}")
                    PosterLanguage.ENGLISH -> Triple(
                        card.modelName,
                        translateConceptEn(card.modelName, card.explanation),
                        "Actionable Defense: ${card.actionableDefense}",
                    )
                    PosterLanguage.RUSSIAN -> Triple(
                        translateConceptRu(card.modelName),
                        card.explanation,
                        "Практическое применение: ${card.actionableDefense}",
                    )
                    PosterLanguage.SPANISH -> Triple(
                        card.modelName,
                        card.explanation,
                        "Aplicación práctica: ${card.actionableDefense}",
                    )
                }
                PosterData(
                    categoryLabel = categoryLabel,
                    categoryEmoji = categoryEmoji,
                    headline = card.category.uppercase(),
                    primaryText = title,
                    secondaryText = explain,
                    takeawayText = defense,
                    attribution = "Hikari Mental Models",
                    moduleType = card.type,
                    language = targetLang,
                    accentColor = accent,
                    gradientTop = gTop,
                    gradientMid = gMid,
                    gradientBottom = gBottom,
                )
            }
            else -> {
                // Generic resolution for other card types
                PosterData(
                    categoryLabel = categoryLabel,
                    categoryEmoji = categoryEmoji,
                    headline = deepDive.title.uppercase(),
                    primaryText = deepDive.subtitle,
                    secondaryText = deepDive.fullContext,
                    takeawayText = "Kern-Erkenntnis: ${deepDive.keyInsight}",
                    attribution = "Hikari Daily Clarity",
                    moduleType = card.type,
                    language = targetLang,
                    accentColor = accent,
                    gradientTop = gTop,
                    gradientMid = gMid,
                    gradientBottom = gBottom,
                )
            }
        }
    }

    private fun translateQuoteEn(author: String, germanQuote: String): String {
        return when {
            author.contains("Marcus Aurelius", ignoreCase = true) ->
                "You have power over your mind - not outside events. Realize this, and you will find strength."
            author.contains("Seneca", ignoreCase = true) ->
                "We suffer more often in imagination than in reality."
            author.contains("Epiktet", ignoreCase = true) || author.contains("Epictetus", ignoreCase = true) ->
                "It's not what happens to you, but how you react to it that matters."
            author.contains("Laozi", ignoreCase = true) || author.contains("Laotse", ignoreCase = true) ->
                "A journey of a thousand miles begins with a single step."
            author.contains("Nietzsche", ignoreCase = true) ->
                "He who has a why to live can bear almost any how."
            author.contains("Konfuzius", ignoreCase = true) || author.contains("Confucius", ignoreCase = true) ->
                "Real knowledge is to know the extent of one's ignorance."
            author.contains("Buddha", ignoreCase = true) ->
                "Peace comes from within. Do not seek it without."
            else -> germanQuote
        }
    }

    private fun translateQuoteRu(author: String, germanQuote: String): String {
        return when {
            author.contains("Marcus Aurelius", ignoreCase = true) ->
                "У тебя есть власть над своим разумом, а не над внешними событиями. Осознай это, и ты обретешь силу."
            author.contains("Seneca", ignoreCase = true) ->
                "Мы страдаем чаще в воображении, чем в реальности."
            author.contains("Epiktet", ignoreCase = true) || author.contains("Epictetus", ignoreCase = true) ->
                "Важно не то, что с вами происходит, а то, как вы на это реагируете."
            author.contains("Laozi", ignoreCase = true) || author.contains("Laotse", ignoreCase = true) ->
                "Путь в тысячу ли начинается с первого шага."
            author.contains("Nietzsche", ignoreCase = true) ->
                "Тот, у кого есть «зачем» жить, может вынести почти любое «как»."
            author.contains("Konfuzius", ignoreCase = true) || author.contains("Confucius", ignoreCase = true) ->
                "Истинное знание — знать пределы своего невежества."
            author.contains("Buddha", ignoreCase = true) ->
                "Мир исходит изнутри. Не ищи его снаружи."
            else -> germanQuote
        }
    }

    private fun translateQuoteEs(author: String, germanQuote: String): String {
        return when {
            author.contains("Marcus Aurelius", ignoreCase = true) ->
                "Tienes poder sobre tu mente, no sobre los acontecimientos externos. Date cuenta de esto y encontrarás la fuerza."
            author.contains("Seneca", ignoreCase = true) ->
                "Sufrimos más a menudo en la imaginación que en la realidad."
            author.contains("Epiktet", ignoreCase = true) || author.contains("Epictetus", ignoreCase = true) ->
                "No importa lo que te sucede, sino cómo reaccionas a ello."
            author.contains("Laozi", ignoreCase = true) || author.contains("Laotse", ignoreCase = true) ->
                "Un viaje de mil millas comienza con un solo paso."
            author.contains("Nietzsche", ignoreCase = true) ->
                "Quien tiene un porqué para vivir puede soportar casi cualquier cómo."
            author.contains("Konfuzius", ignoreCase = true) || author.contains("Confucius", ignoreCase = true) ->
                "El verdadero conocimiento es conocer la extensión de la propia ignorancia."
            author.contains("Buddha", ignoreCase = true) ->
                "La paz viene de adentro. No la busques afuera."
            else -> germanQuote
        }
    }

    private fun translateWordEn(foreignWord: String, germanTranslation: String): String {
        val dict = mapOf(
            "Здравствуйте" to "Hello / Greetings (Formal)",
            "Спасибо" to "Thank you",
            "Пожалуйста" to "Please / You're welcome",
            "Да" to "Yes",
            "Нет" to "No",
            "Свобода" to "Freedom / Liberty",
            "Друг" to "Friend",
            "Время" to "Time",
            "Правда" to "Truth",
            "Любовь" to "Love",
            "Мир" to "Peace / World",
            "Жизнь" to "Life",
            "Сила" to "Strength / Power",
            "Мудрость" to "Wisdom",
        )
        return dict[foreignWord] ?: germanTranslation
    }

    private fun translateWordEs(foreignWord: String, germanTranslation: String): String {
        val dict = mapOf(
            "Здравствуйте" to "Hola / Saludos (Formal)",
            "Спасибо" to "Gracias",
            "Пожалуйста" to "Por favor / De nada",
            "Да" to "Sí",
            "Нет" to "No",
            "Свобода" to "Libertad",
            "Друг" to "Amigo",
            "Время" to "Tiempo",
            "Правda" to "Verdad",
            "Любовь" to "Amor",
            "Мир" to "Paz / Mundo",
            "Жизнь" to "Vida",
            "Сила" to "Fuerza / Poder",
            "Мудрость" to "Sabiduría",
        )
        return dict[foreignWord] ?: germanTranslation
    }

    private fun translateConceptEn(name: String, explain: String): String = when {
        name.contains("Ockhams", ignoreCase = true) -> "Occam's Razor: The simplest explanation is usually the best one."
        name.contains("Bestätigungsfehler", ignoreCase = true) -> "Confirmation Bias: The tendency to search for information that confirms our preconceptions."
        name.contains("Sunk Cost", ignoreCase = true) -> "Sunk Cost Fallacy: Continuing a behavior as a result of previously invested resources."
        name.contains("Erste Prinzipien", ignoreCase = true) || name.contains("First Principles", ignoreCase = true) ->
            "First Principles Thinking: Breaking down complex problems into basic truths and reasoning up from there."
        else -> explain
    }

    private fun translateConceptRu(name: String): String = when {
        name.contains("Ockhams", ignoreCase = true) -> "Бритва Оккама"
        name.contains("Bestätigungsfehler", ignoreCase = true) -> "Предвзятость подтверждения"
        name.contains("Sunk Cost", ignoreCase = true) -> "Ловушка невозвратных затрат"
        name.contains("Erste Prinzipien", ignoreCase = true) || name.contains("First Principles", ignoreCase = true) ->
            "Мышление от первых принципов"
        else -> name
    }

    private data class Quad(val accent: Long, val gTop: Long, val gMid: Long, val gBottom: Long)
}
