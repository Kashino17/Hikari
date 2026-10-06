package com.hikari.app.domain.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class MindfulModuleType(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val defaultEnabled: Boolean = true,
) {
    QUOTE(
        id = "quote",
        title = "Zitate & Lebensweisheiten",
        subtitle = "Tägliche Stoiker & Denker (Max 2 / Tag)",
        icon = Icons.Outlined.FormatQuote,
        accentColor = Color(0xFFF59E0B),
    ),
    BRAIN_PUZZLE(
        id = "brain_puzzle",
        title = "Gehirnjogging & Kognition",
        subtitle = "Wissenschaftliche Mini-Rätsel (Max 2 / Tag)",
        icon = Icons.Outlined.Psychology,
        accentColor = Color(0xFF3B82F6),
    ),
    LANGUAGE(
        id = "language",
        title = "Sprachen lernen",
        subtitle = "Vokabeln, Sprechen & Mini-Dialoge",
        icon = Icons.Outlined.Translate,
        accentColor = Color(0xFF10B981),
    ),
    HISTORY(
        id = "history",
        title = "Heute in der Geschichte",
        subtitle = "Schlüsselereignisse des Tages",
        icon = Icons.Outlined.AccountBalance,
        accentColor = Color(0xFFEC4899),
    ),
    MENTAL_MODEL(
        id = "mental_model",
        title = "Kritisches Denken",
        subtitle = "Denkfehler & kognitive Verzerrungen",
        icon = Icons.Outlined.Lightbulb,
        accentColor = Color(0xFF8B5CF6),
    ),
    BREATHWORK(
        id = "breathwork",
        title = "1-Minuten-Atemübung",
        subtitle = "Box Breathing zur Cortisolsenkung",
        icon = Icons.Outlined.Air,
        accentColor = Color(0xFF06B6D4),
    ),
    SCIENCE(
        id = "science",
        title = "Wissenschafts-Happen",
        subtitle = "Wie funktioniert die Welt?",
        icon = Icons.Outlined.Science,
        accentColor = Color(0xFF14B8A6),
    ),
    FINANCE(
        id = "finance",
        title = "Finanzielle Bildung",
        subtitle = "Praktische Geld- & Lebenskompetenzen",
        icon = Icons.Outlined.MonetizationOn,
        accentColor = Color(0xFFFBBF24),
    ),
    GEOGRAPHY(
        id = "geography",
        title = "Weltatlas & Geografie-Quiz",
        subtitle = "Länder, Flaggen & Hauptstädte",
        icon = Icons.Outlined.Public,
        accentColor = Color(0xFF6366F1),
    ),
    SPEED_MATH(
        id = "speed_math",
        title = "Kopfrechnen-Tricks",
        subtitle = "Geniale mathematische Shortcuts",
        icon = Icons.Outlined.Calculate,
        accentColor = Color(0xFFEF4444),
    ),
    ART_CULTURE(
        id = "art_culture",
        title = "Kunst & Kultur",
        subtitle = "Meisterwerk & Entstehungsgeschichte",
        icon = Icons.Outlined.Palette,
        accentColor = Color(0xFFA855F7),
    ),
    VOCABULARY(
        id = "vocabulary",
        title = "Wortschatz-Meister",
        subtitle = "Eloquentes Wort des Tages & Etymologie",
        icon = Icons.Outlined.MenuBook,
        accentColor = Color(0xFFF97316),
    ),
    PHILOSOPHY(
        id = "philosophy",
        title = "Philosophisches Dilemma",
        subtitle = "Gedankenexperimente & ethische Fragen",
        icon = Icons.Outlined.Balance,
        accentColor = Color(0xFFE11D48),
    );

    val iconEmoji: String get() = ""
}

enum class LearningLanguage(
    val code: String,
    val title: String,
    val shortCode: String,
) {
    RUSSIAN("ru", "Russisch", "RU"),
    ENGLISH("en", "Englisch", "EN"),
    SPANISH("es", "Spanisch", "ES"),
    JAPANESE("ja", "Japanisch", "JA"),
    FRENCH("fr", "Französisch", "FR"),
    ITALIAN("it", "Italienisch", "IT"),
    GERMAN_ADVANCED("de", "Gehobenes Deutsch", "DE");

    /** Kompatibilität für Aufrufer ohne Emojis */
    val flagEmoji: String get() = ""

    companion object {
        fun fromCode(code: String?): LearningLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RUSSIAN
    }
}

enum class ModuleRank(
    val rankNumber: Int,
    val title: String,
    val shortLabel: String,
    val frequencyLabel: String,
    val badgeColor: Color,
) {
    RANK_1(1, "Stufe 1 · Standard", "Standard", "1x täglich", Color(0xFF9CA3AF)),
    RANK_2(2, "Stufe 2 · Erhöht", "Erhöht", "3–4x täglich", Color(0xFF60A5FA)),
    RANK_3(3, "Stufe 3 · Top-Fokus", "Top-Fokus", "6–8x täglich (alle 2–3 Karten)", Color(0xFFFBBF24));

    companion object {
        fun fromNumber(num: Int): ModuleRank = when (num) {
            1 -> RANK_1
            2 -> RANK_2
            3 -> RANK_3
            else -> RANK_1
        }
    }
}

// ── Modul-Datenstrukturen ───────────────────────────────────────────────────

sealed interface MindfulCard {
    val id: String
    val type: MindfulModuleType
    val rank: ModuleRank get() = ModuleRank.RANK_1
}

data class QuoteCardItem(
    override val id: String,
    val quote: String,
    val author: String,
    val contextEra: String,
    val reflectionPrompt: String,
    override val rank: ModuleRank = ModuleRank.RANK_2,
) : MindfulCard {
    override val type = MindfulModuleType.QUOTE
}

data class BrainPuzzleCardItem(
    override val id: String,
    val title: String,
    val category: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val brainRegionTrained: String,
    override val rank: ModuleRank = ModuleRank.RANK_2,
) : MindfulCard {
    override val type = MindfulModuleType.BRAIN_PUZZLE
}

data class LanguageCardItem(
    override val id: String,
    val language: LearningLanguage,
    val foreignWord: String,
    val nativeTranslation: String,
    val phonetic: String,
    val exampleForeign: String,
    val exampleTranslation: String,
    val dialogueScenario: String,
    val dialoguePartner: String,
    val dialoguePrompt: String,
    val dialogueReplies: List<String>,
    val correctReplyIndex: Int,
    override val rank: ModuleRank = ModuleRank.RANK_3,
    // Echte Sprachkurs-Anbindung:
    val audioRes: String? = null,
    val dayNumber: Int? = null,
    val dayTitle: String? = null,
    val isDueReview: Boolean = false,
    val currentStreak: Int = 0,
    val totalXp: Int = 0,
    val phraseId: String? = null,
    val note: String? = null,
    val literalTranslation: String? = null,
) : MindfulCard {
    override val type = MindfulModuleType.LANGUAGE
}

data class HistoryCardItem(
    override val id: String,
    val dateLabel: String,
    val eventTitle: String,
    val description: String,
    val whyItMatters: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.HISTORY
}

data class MentalModelCardItem(
    override val id: String,
    val modelName: String,
    val category: String,
    val explanation: String,
    val realLifeExample: String,
    val actionableDefense: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.MENTAL_MODEL
}

data class BreathworkCardItem(
    override val id: String,
    val title: String,
    val inhaleSeconds: Int = 4,
    val holdInSeconds: Int = 4,
    val exhaleSeconds: Int = 4,
    val holdOutSeconds: Int = 4,
    val scientificBenefit: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.BREATHWORK
}

data class ScienceCardItem(
    override val id: String,
    val phenomenon: String,
    val question: String,
    val coreExplanation: String,
    val fascinatingDetail: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.SCIENCE
}

data class FinanceCardItem(
    override val id: String,
    val title: String,
    val corePrinciple: String,
    val practicalExample: String,
    val takeawayRule: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.FINANCE
}

data class GeographyCardItem(
    override val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val interestingFact: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.GEOGRAPHY
}

data class SpeedMathCardItem(
    override val id: String,
    val trickTitle: String,
    val formulaShortcut: String,
    val explanation: String,
    val practiceChallenge: String,
    val challengeResult: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.SPEED_MATH
}

data class ArtCultureCardItem(
    override val id: String,
    val masterpieceTitle: String,
    val artist: String,
    val yearAndOrigin: String,
    val backStory: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.ART_CULTURE
}

data class VocabularyCardItem(
    override val id: String,
    val word: String,
    val wordType: String,
    val definition: String,
    val etymology: String,
    val sampleSentence: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.VOCABULARY
}

data class PhilosophyCardItem(
    override val id: String,
    val dilemmaTitle: String,
    val scenario: String,
    val optionA: String,
    val optionB: String,
    val schoolA: String,
    val schoolB: String,
    val philosophicalInsight: String,
    override val rank: ModuleRank = ModuleRank.RANK_3,
) : MindfulCard {
    override val type = MindfulModuleType.PHILOSOPHY
}

// ── Deep-Dive Datenmodell (Wisch nach links) ──────────────────────────────────

data class DeepDiveContent(
    val title: String,
    val subtitle: String,
    val keyInsight: String,
    val fullContext: String,
    val practicalApplication: String? = null,
    val originOrEtymology: String? = null,
    val relatedTakeaways: List<String> = emptyList(),
)

fun MindfulCard.resolveDeepDive(): DeepDiveContent = when (this) {
    is QuoteCardItem -> DeepDiveContent(
        title = "Philosophischer Tiefgang",
        subtitle = "$author · $contextEra",
        keyInsight = "»$quote«",
        fullContext = "Dieser Gedanke entstammt der Epoche $contextEra. $author hinterfragt hier die menschliche Neigung, sich von äußeren Umständen überwältigen zu lassen. Wahre Gelassenheit entsteht, wenn wir unsere Aufmerksamkeit auf das richten, was in unserer eigenen Kontrolle liegt.",
        practicalApplication = "Tägliche Reflexion: $reflectionPrompt",
        originOrEtymology = "Lebensphilosophie & geistige Klarheit",
        relatedTakeaways = listOf(
            "Urteile bestimmen unser Empfinden, nicht die Dinge selbst.",
            "Mentale Ruhe ist trainierbar wie ein Muskel.",
        ),
    )
    is LanguageCardItem -> DeepDiveContent(
        title = "Sprach- & Grammatik-Deep-Dive",
        subtitle = "$foreignWord · $phonetic",
        keyInsight = "Bedeutung: $nativeTranslation",
        fullContext = buildString {
            append("Russischer Ausdruck ")
            if (dayTitle != null) append("aus Lektion »$dayTitle« (Tag $dayNumber). ") else append("aus dem Alltag. ")
            if (!literalTranslation.isNullOrBlank()) append("\n\n• Wörtlich: „$literalTranslation“")
            if (!note.isNullOrBlank()) append("\n• Grammatik/Kontext: $note")
            append("\n• Betonung & Phonetik: $phonetic. Im Russischen führt die dynamische Betonung zu Akanje (unbetontes 'o' wird [a] gesprochen).")
        },
        practicalApplication = "Typische Verwendung: „$exampleForeign“ ($exampleTranslation)",
        originOrEtymology = "Russischer Sprachraum · Kurs Tag ${dayNumber ?: 1}",
        relatedTakeaways = listOf(
            "Szenario: $dialogueScenario",
            "Antwort: ${dialogueReplies.getOrNull(correctReplyIndex) ?: "Verstanden"}",
        ),
    )
    is BrainPuzzleCardItem -> DeepDiveContent(
        title = "Logik & Beweisführung",
        subtitle = "$category · Kognitives Training",
        keyInsight = "Korrekte Antwort: ${options.getOrNull(correctIndex) ?: ""}",
        fullContext = explanation,
        practicalApplication = "Aktiviertes Gehirnareal: $brainRegionTrained. Schärft die analytische Denkfähigkeit und schützt vor mentaler Trägheit.",
        originOrEtymology = "Kognitive Psychologie & Lateral-Denken",
        relatedTakeaways = listOf(
            "Erste Intuition kritisch hinterfragen.",
            "Aufgaben zerlegen statt vorschnell raten.",
        ),
    )
    is HistoryCardItem -> DeepDiveContent(
        title = "Historischer Deep Dive",
        subtitle = "$eventTitle ($dateLabel)",
        keyInsight = whyItMatters,
        fullContext = description,
        practicalApplication = "Historische Kausalität: Wie vergangene Ereignisse moderne Institutionen, Gesetze und Denkweisen bis heute prägen.",
        originOrEtymology = "Weltgeschichte & Kulturkreis",
        relatedTakeaways = listOf(
            "Wer die Geschichte nicht kennt, versteht die Gegenwart nicht.",
            "Wendepunkt mit langfristigen Folgen.",
        ),
    )
    is MentalModelCardItem -> DeepDiveContent(
        title = "Kritisches Denken & Modell",
        subtitle = modelName,
        keyInsight = category,
        fullContext = explanation,
        practicalApplication = "Alltags-Beispiel: $realLifeExample\n\nAbwehr-Strategie: $actionableDefense",
        originOrEtymology = "Kognitionswissenschaften & Entscheidungsfindung",
        relatedTakeaways = listOf(
            "Denkfallen frühzeitig erkennen.",
            "Zweite-Ordnung-Denken anwenden.",
        ),
    )
    is BreathworkCardItem -> DeepDiveContent(
        title = "Neurologische Wirkungsweise",
        subtitle = title,
        keyInsight = "Parasympathikus-Aktivierung & Cortisol-Senkung",
        fullContext = scientificBenefit,
        practicalApplication = "Einsatz: 4 Sekunden Einatmen, 4 Sekunden Halten, 4 Sekunden Ausatmen, 4 Sekunden Halten. Ideal vor stressigen Aufgaben, Meetings oder zum schnellen Runterfahren.",
        originOrEtymology = "Autonomes Nervensystem & Stressphysiologie",
        relatedTakeaways = listOf(
            "Der Atem ist die direkte Fernbedienung für den Herzschlag.",
            "60 Sekunden reichen für messbare physiologische Effekte.",
        ),
    )
    is ScienceCardItem -> DeepDiveContent(
        title = "Wissenschaftliche Hintergründe",
        subtitle = phenomenon,
        keyInsight = question,
        fullContext = coreExplanation,
        practicalApplication = "Faszinierendes Detail: $fascinatingDetail",
        originOrEtymology = "Naturwissenschaften & empirische Forschung",
        relatedTakeaways = listOf(
            "Naturgesetze wirken universell.",
            "Wissenschaftliches Denken basiert auf Nachprüfbarkeit.",
        ),
    )
    is FinanceCardItem -> DeepDiveContent(
        title = "Finanzielle Bildung & Hebel",
        subtitle = title,
        keyInsight = corePrinciple,
        fullContext = practicalExample,
        practicalApplication = "Goldene Regel: $takeawayRule",
        originOrEtymology = "Finanzökonomie & Vermögensaufbau",
        relatedTakeaways = listOf(
            "Geld folgt festen mathematischen Gesetzen.",
            "Früh anfangen und exponentiellen Zinseszins nutzen.",
        ),
    )
    is GeographyCardItem -> DeepDiveContent(
        title = "Geopolitischer Steckbrief",
        subtitle = question,
        keyInsight = "Antwort: ${options.getOrNull(correctIndex) ?: ""}",
        fullContext = interestingFact,
        practicalApplication = "Kulturelles Wissen über Geografie, Wirtschaft und globale Handelsrouten.",
        originOrEtymology = "Weltatlas & Völkerkunde",
        relatedTakeaways = listOf(
            "Lage und Topografie prägen Kultur und Wirtschaft.",
        ),
    )
    is SpeedMathCardItem -> DeepDiveContent(
        title = "Mathematische Beweisführung",
        subtitle = trickTitle,
        keyInsight = "Shortcut: $formulaShortcut",
        fullContext = explanation,
        practicalApplication = "Übungsaufgabe: $practiceChallenge · Ergebnis: $challengeResult",
        originOrEtymology = "Algebra & Arithmetik-Shortcuts",
        relatedTakeaways = listOf(
            "Zahlen zerlegen macht Rechnen mühelos.",
            "Muster erkennen statt stur multiplizieren.",
        ),
    )
    is ArtCultureCardItem -> DeepDiveContent(
        title = "Kunsthistorische Analyse",
        subtitle = "$masterpieceTitle · $artist",
        keyInsight = "Entstehung & Epoche: $yearAndOrigin",
        fullContext = backStory,
        practicalApplication = "Blick für Symbolik, Komposition und kunstgeschichtliche Meilensteine schärfen.",
        originOrEtymology = "Europäische & globale Kulturgeschichte",
        relatedTakeaways = listOf(
            "Jedes Kunstwerk ist ein Spiegel seiner Epoche.",
        ),
    )
    is VocabularyCardItem -> DeepDiveContent(
        title = "Wortursprung & Etymologie",
        subtitle = "$word ($wordType)",
        keyInsight = definition,
        fullContext = etymology,
        practicalApplication = "Anwendungsbeispiel: „$sampleSentence“",
        originOrEtymology = "Sprachgeschichte & Rhetorik",
        relatedTakeaways = listOf(
            "Präzise Sprache schärft das Denken.",
        ),
    )
    is PhilosophyCardItem -> DeepDiveContent(
        title = "Ethisches Gedankenexperiment",
        subtitle = dilemmaTitle,
        keyInsight = philosophicalInsight,
        fullContext = "$scenario\n\nSchule A ($schoolA): $optionA\n\nSchule B ($schoolB): $optionB",
        practicalApplication = "Wie bewerten wir Handlungen: Nach ihren Konsequenzen (Utilitarismus) oder nach festen moralischen Pflichten (Deontologie)?",
        originOrEtymology = "Moralphilosophie & Ethik",
        relatedTakeaways = listOf(
            "Dilemmata zeigen die Grenzen einfacher Regeln auf.",
            "Schult das moralische Urteilsvermögen.",
        ),
    )
}
