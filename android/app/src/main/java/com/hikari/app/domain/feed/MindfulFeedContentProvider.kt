package com.hikari.app.domain.feed

import com.hikari.app.domain.russian.RuCourseIndex
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuSettings
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.domain.russian.RussianProgressStore
import java.util.ArrayDeque
import java.util.Calendar

object MindfulFeedContentProvider {

    fun getDailyCards(
        calendar: Calendar = Calendar.getInstance(),
        enabledModules: Set<MindfulModuleType>,
        language: LearningLanguage,
        moduleRanks: Map<MindfulModuleType, ModuleRank> = emptyMap(),
        russianRepo: RussianCourseRepository? = null,
        russianProgress: RussianProgressStore? = null,
    ): List<MindfulCard> {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        // Ermittle für jedes Modul den effektiven Rang: Stufe 3 (Top-Fokus), Stufe 2 (Erhöht), Stufe 1 (Standard)
        fun rankFor(module: MindfulModuleType): ModuleRank {
            return moduleRanks[module] ?: when (module) {
                MindfulModuleType.LANGUAGE -> ModuleRank.RANK_3 // Standard: Stufe 3 (Top-Fokus)
                MindfulModuleType.BRAIN_PUZZLE, MindfulModuleType.QUOTE -> ModuleRank.RANK_2 // Erhöht
                else -> ModuleRank.RANK_1 // Standard
            }
        }

        // Karten pro Modul generieren (entsprechend der Frequenz des Rangs)
        val moduleBuckets = mutableMapOf<MindfulModuleType, MutableList<MindfulCard>>()

        for (module in enabledModules) {
            val rank = rankFor(module)
            val bucket = mutableListOf<MindfulCard>()

            when (module) {
                MindfulModuleType.QUOTE -> {
                    // Zitate: max. 2 / Tag (Anti-Dopamin-Limit)
                    val count = if (rank == ModuleRank.RANK_1) 1 else 2
                    for (i in 0 until count) {
                        val qIndex = (dayOfYear * 2 + i) % QUOTE_POOL.size
                        bucket.add(QUOTE_POOL[qIndex].copy(id = "quote-${i + 1}-$dayOfYear", rank = rank))
                    }
                }
                MindfulModuleType.BRAIN_PUZZLE -> {
                    // Rätsel: max. 2 / Tag (Anti-Dopamin-Limit)
                    val count = if (rank == ModuleRank.RANK_1) 1 else 2
                    for (i in 0 until count) {
                        val pIndex = (dayOfYear * 2 + i) % PUZZLE_POOL.size
                        bucket.add(PUZZLE_POOL[pIndex].copy(id = "puzzle-${i + 1}-$dayOfYear", rank = rank))
                    }
                }
                MindfulModuleType.LANGUAGE -> {
                    // Sprachen: Stufe 3 (Top-Fokus) = 6-8x täglich, Stufe 2 = 3-4x, Stufe 1 = 1-2x
                    val count = when (rank) {
                        ModuleRank.RANK_3 -> 7
                        ModuleRank.RANK_2 -> 4
                        ModuleRank.RANK_1 -> 2
                    }

                    if (language == LearningLanguage.RUSSIAN && russianRepo != null && russianProgress != null) {
                        // Echte Anbindung an das russische Lernsystem & den Kursfortschritt
                        val realCards = generateRussianCards(
                            repo = russianRepo,
                            store = russianProgress,
                            count = count,
                            dayOfYear = dayOfYear,
                            rank = rank,
                        )
                        bucket.addAll(realCards)
                    } else {
                        // Fallback für andere Sprachen
                        val pool = LANGUAGE_POOLS[language] ?: LANGUAGE_POOLS[LearningLanguage.RUSSIAN]!!
                        for (i in 0 until count) {
                            val lIndex = (dayOfYear * 3 + i) % pool.size
                            bucket.add(pool[lIndex].copy(id = "lang-$dayOfYear-${language.code}-${i + 1}", rank = rank))
                        }
                    }
                }
                MindfulModuleType.HISTORY -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(HISTORY_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % HISTORY_POOL.size
                        bucket.add(HISTORY_POOL[idx].copy(id = "hist-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.MENTAL_MODEL -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(MENTAL_MODEL_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % MENTAL_MODEL_POOL.size
                        bucket.add(MENTAL_MODEL_POOL[idx].copy(id = "model-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.BREATHWORK -> {
                    bucket.add(
                        BreathworkCardItem(
                            id = "breath-$dayOfYear",
                            title = "Box Breathing (4-4-4-4)",
                            scientificBenefit = "Aktiviert den Parasympathikus, senkt nachweislich den Cortisolspiegel im Blut und steigert den mentalen Fokus innerhalb von 60 Sekunden.",
                            rank = rank,
                        )
                    )
                }
                MindfulModuleType.SCIENCE -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(SCIENCE_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % SCIENCE_POOL.size
                        bucket.add(SCIENCE_POOL[idx].copy(id = "sci-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.FINANCE -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(FINANCE_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % FINANCE_POOL.size
                        bucket.add(FINANCE_POOL[idx].copy(id = "fin-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.GEOGRAPHY -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(GEOGRAPHY_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % GEOGRAPHY_POOL.size
                        bucket.add(GEOGRAPHY_POOL[idx].copy(id = "geo-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.SPEED_MATH -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(SPEED_MATH_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % SPEED_MATH_POOL.size
                        bucket.add(SPEED_MATH_POOL[idx].copy(id = "math-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.ART_CULTURE -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(ART_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % ART_POOL.size
                        bucket.add(ART_POOL[idx].copy(id = "art-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.VOCABULARY -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(VOCABULARY_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % VOCABULARY_POOL.size
                        bucket.add(VOCABULARY_POOL[idx].copy(id = "vocab-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
                MindfulModuleType.PHILOSOPHY -> {
                    val count = if (rank == ModuleRank.RANK_3) 3.coerceAtMost(PHILOSOPHY_POOL.size) else if (rank == ModuleRank.RANK_2) 2 else 1
                    for (i in 0 until count) {
                        val idx = (dayOfYear * 2 + i) % PHILOSOPHY_POOL.size
                        bucket.add(PHILOSOPHY_POOL[idx].copy(id = "phil-$dayOfYear-${i + 1}", rank = rank))
                    }
                }
            }
            moduleBuckets[module] = bucket
        }

        // ── Dynamisches Interleaving & Ranking-Priorisierung ────────────────────────
        // Stufe 3 (Top-Fokus): Höchste Frequenz, taucht alle 2-3 Karten auf und wird nie verdrängt!
        val topFocusCards = mutableListOf<MindfulCard>()
        val otherCards = mutableListOf<MindfulCard>()

        for (module in enabledModules) {
            val cards = moduleBuckets[module].orEmpty()
            if (rankFor(module) == ModuleRank.RANK_3) {
                topFocusCards.addAll(cards)
            } else {
                otherCards.addAll(cards)
            }
        }

        val topFocusQueue = ArrayDeque(topFocusCards)
        val otherQueue = ArrayDeque(otherCards)
        val result = mutableListOf<MindfulCard>()

        // 1. Erste Karte im Feed ist IMMER Top-Fokus (wenn vorhanden)!
        if (topFocusQueue.isNotEmpty()) {
            result.add(topFocusQueue.removeFirst())
        } else if (otherQueue.isNotEmpty()) {
            result.add(otherQueue.removeFirst())
        }

        // 2. Webe Top-Fokus-Karten nahtlos alle 2 reguläre Karten ein:
        while (topFocusQueue.isNotEmpty() || otherQueue.isNotEmpty()) {
            repeat(2) {
                if (otherQueue.isNotEmpty()) {
                    result.add(otherQueue.removeFirst())
                }
            }
            if (topFocusQueue.isNotEmpty()) {
                result.add(topFocusQueue.removeFirst())
            }
        }

        return result
    }

    private fun generateRussianCards(
        repo: RussianCourseRepository,
        store: RussianProgressStore,
        count: Int,
        dayOfYear: Int,
        rank: ModuleRank,
    ): List<LanguageCardItem> {
        val progress = store.state.value
        val settings = progress.settings
        val unlockedDay = progress.unlockedDay(14)
        val courseDay = repo.index.day(unlockedDay) ?: repo.index.days.first()
        val dayPhrases = repo.index.dayPhrases(courseDay, settings)
        val todayEpoch = store.today()
        val dueIds = RuSrs.dueIds(progress.cards, todayEpoch)
        val duePhrases = dueIds.mapNotNull { repo.index.phrase(it, settings) }
        val dialogPairs = repo.index.dialog(courseDay, settings)
        val allLearned = repo.index.phrasesUpTo(unlockedDay, settings)

        val cards = mutableListOf<LanguageCardItem>()

        // 1. Primäre Tagesvokabel (Fokus des Tages mit Audio)
        if (dayPhrases.isNotEmpty()) {
            val p0 = dayPhrases[dayOfYear % dayPhrases.size]
            cards.add(
                LanguageCardItem(
                    id = "ru-day-$unlockedDay-${p0.id}",
                    language = LearningLanguage.RUSSIAN,
                    foreignWord = p0.display,
                    nativeTranslation = p0.de,
                    phonetic = RussianPhonetics.transliterateFlat(p0.ru),
                    exampleForeign = p0.ru,
                    exampleTranslation = p0.de,
                    dialogueScenario = "Tag ${courseDay.day}: ${courseDay.title}",
                    dialoguePartner = courseDay.dialog.partner,
                    dialoguePrompt = "Höre dir das russische Original an und sprich es nach.",
                    dialogueReplies = listOf("Verstanden!", "Nochmal hören", "Weiter"),
                    correctReplyIndex = 0,
                    rank = rank,
                    audioRes = p0.audio,
                    dayNumber = courseDay.day,
                    dayTitle = courseDay.title,
                    isDueReview = false,
                    currentStreak = progress.streak,
                    totalXp = progress.xp,
                    phraseId = p0.id,
                    note = p0.note,
                    literalTranslation = p0.lit,
                )
            )
        }

        // 2. SRS Wiederholungskarte (wenn fällig, sonst 2. Tagesphrase)
        if (cards.size < count) {
            val reviewPhrase = duePhrases.firstOrNull() ?: dayPhrases.getOrNull(1)
            if (reviewPhrase != null) {
                val isDue = duePhrases.isNotEmpty()
                val originDay = repo.index.dayOfItem[reviewPhrase.id] ?: courseDay.day
                cards.add(
                    LanguageCardItem(
                        id = "ru-review-$unlockedDay-${reviewPhrase.id}",
                        language = LearningLanguage.RUSSIAN,
                        foreignWord = reviewPhrase.display,
                        nativeTranslation = reviewPhrase.de,
                        phonetic = RussianPhonetics.transliterateFlat(reviewPhrase.ru),
                        exampleForeign = reviewPhrase.ru,
                        exampleTranslation = reviewPhrase.de,
                        dialogueScenario = if (isDue) "SRS-Wiederholung (Tag $originDay)" else "Wortschatz-Erweiterung",
                        dialoguePartner = "System",
                        dialoguePrompt = if (isDue) "Erinnerst du dich an die Bedeutung und Aussprache?" else "Sprich die Phrase laut nach.",
                        dialogueReplies = listOf("Ich kenne es!", "Aussprache prüfen", "Lösung anzeigen"),
                        correctReplyIndex = 0,
                        rank = rank,
                        audioRes = reviewPhrase.audio,
                        dayNumber = originDay,
                        dayTitle = repo.index.day(originDay)?.title ?: courseDay.title,
                        isDueReview = isDue,
                        currentStreak = progress.streak,
                        totalXp = progress.xp,
                        phraseId = reviewPhrase.id,
                        note = reviewPhrase.note,
                        literalTranslation = reviewPhrase.lit,
                    )
                )
            }
        }

        // 3. Aussprache & Sprech-Challenge
        if (cards.size < count && dayPhrases.size > 2) {
            val pSpeak = dayPhrases[(dayOfYear + 2) % dayPhrases.size]
            cards.add(
                LanguageCardItem(
                    id = "ru-speak-$unlockedDay-${pSpeak.id}",
                    language = LearningLanguage.RUSSIAN,
                    foreignWord = pSpeak.display,
                    nativeTranslation = pSpeak.de,
                    phonetic = RussianPhonetics.transliterateFlat(pSpeak.ru),
                    exampleForeign = pSpeak.ru,
                    exampleTranslation = pSpeak.de,
                    dialogueScenario = "Sprech- & Aussprache-Fokus",
                    dialoguePartner = courseDay.dialog.partner,
                    dialoguePrompt = "Teste deine Aussprache mit der KI-Sprachkontrolle oder vergleiche deine Stimme.",
                    dialogueReplies = listOf("Aussprache geprüft", "Nochmal üben", "Weiter"),
                    correctReplyIndex = 0,
                    rank = rank,
                    audioRes = pSpeak.audio,
                    dayNumber = courseDay.day,
                    dayTitle = courseDay.title,
                    isDueReview = false,
                    currentStreak = progress.streak,
                    totalXp = progress.xp,
                    phraseId = pSpeak.id,
                    note = pSpeak.note,
                    literalTranslation = pSpeak.lit,
                )
            )
        }

        // 4. Interaktiver Mini-Dialog des Tages
        if (cards.size < count && dialogPairs.size >= 2) {
            val partnerLine = dialogPairs[0].second
            val myLine = dialogPairs.getOrNull(1)?.second ?: dayPhrases.last()
            val distractors = allLearned.filter { it.id != myLine.id }.shuffled().take(2)
            val allOptions = (distractors + myLine).shuffled()
            val correctIdx = allOptions.indexOfFirst { it.id == myLine.id }.coerceAtLeast(0)

            cards.add(
                LanguageCardItem(
                    id = "ru-dlg-$unlockedDay-${partnerLine.id}",
                    language = LearningLanguage.RUSSIAN,
                    foreignWord = partnerLine.display,
                    nativeTranslation = partnerLine.de,
                    phonetic = RussianPhonetics.transliterateFlat(partnerLine.ru),
                    exampleForeign = myLine.ru,
                    exampleTranslation = myLine.de,
                    dialogueScenario = "${courseDay.dialog.title} (${courseDay.dialog.scene})",
                    dialoguePartner = courseDay.dialog.partner,
                    dialoguePrompt = "${courseDay.dialog.partner}: »${partnerLine.display}« — Wie antwortest du?",
                    dialogueReplies = allOptions.map { it.display },
                    correctReplyIndex = correctIdx,
                    rank = rank,
                    audioRes = partnerLine.audio,
                    dayNumber = courseDay.day,
                    dayTitle = courseDay.title,
                    isDueReview = false,
                    currentStreak = progress.streak,
                    totalXp = progress.xp,
                    phraseId = partnerLine.id,
                    note = partnerLine.note,
                    literalTranslation = partnerLine.lit,
                )
            )
        }

        // 5..N. Weitere Phrasen des Tages bis count erreicht ist
        var phraseIndex = 3
        while (cards.size < count && dayPhrases.isNotEmpty()) {
            val pExtra = dayPhrases[(dayOfYear + phraseIndex) % dayPhrases.size]
            cards.add(
                LanguageCardItem(
                    id = "ru-extra-$unlockedDay-${pExtra.id}-$phraseIndex",
                    language = LearningLanguage.RUSSIAN,
                    foreignWord = pExtra.display,
                    nativeTranslation = pExtra.de,
                    phonetic = RussianPhonetics.transliterateFlat(pExtra.ru),
                    exampleForeign = pExtra.ru,
                    exampleTranslation = pExtra.de,
                    dialogueScenario = "Wortschatz-Meisterung",
                    dialoguePartner = courseDay.dialog.partner,
                    dialoguePrompt = "Höre dir die Betonung an und sprich mit.",
                    dialogueReplies = listOf("Verstanden", "Nochmal sprechen", "Weiter"),
                    correctReplyIndex = 0,
                    rank = rank,
                    audioRes = pExtra.audio,
                    dayNumber = courseDay.day,
                    dayTitle = courseDay.title,
                    isDueReview = false,
                    currentStreak = progress.streak,
                    totalXp = progress.xp,
                    phraseId = pExtra.id,
                    note = pExtra.note,
                    literalTranslation = pExtra.lit,
                )
            )
            phraseIndex++
        }

        return cards
    }

    // ── POOLS ───────────────────────────────────────────────────────────────────

    private val QUOTE_POOL = listOf(
        QuoteCardItem(
            id = "q-1",
            quote = "Wir leiden öfter in der Vorstellung als in der Wirklichkeit.",
            author = "Lucius Annaeus Seneca",
            contextEra = "Römische Stoa • ca. 60 n. Chr.",
            reflectionPrompt = "Welche Sorge in deinem Kopf hat sich rückblickend als völlig unbegründet herausgestellt?",
        ),
        QuoteCardItem(
            id = "q-2",
            quote = "Nicht die Dinge selbst beunruhigen die Menschen, sondern ihre Urteile über die Dinge.",
            author = "Epiktet",
            contextEra = "Griechische Stoa • ca. 100 n. Chr.",
            reflectionPrompt = "Kannst du die heutige Herausforderung nicht als Problem, sondern als Übungsfeld sehen?",
        ),
        QuoteCardItem(
            id = "q-3",
            quote = "Wer ein Warum zum Leben hat, erträgt fast jedes Wie.",
            author = "Friedrich Nietzsche",
            contextEra = "Klassische Philosophie • 1888",
            reflectionPrompt = "Was ist das übergeordnete Ziel, das deinen heutigen Anstrengungen Sinn verleiht?",
        ),
        QuoteCardItem(
            id = "q-4",
            quote = "Eine Reise von tausend Meilen beginnt mit einem einzigen Schritt.",
            author = "Laozi (Lao-Tse)",
            contextEra = "Daoismus • 6. Jh. v. Chr.",
            reflectionPrompt = "Welchen kleinsten Schritt kannst du heute tun, den du schon viel zu lange aufschiebst?",
        ),
        QuoteCardItem(
            id = "q-5",
            quote = "Das Geheimnis des Wandels besteht darin, deine ganze Energie darauf zu konzentrieren, Neues aufzubauen, statt Altes zu bekämpfen.",
            author = "Dan Millman (Sokrates-Prinzip)",
            contextEra = "Moderne Philosophie",
            reflectionPrompt = "Worauf richtest du deine Energie heute: auf Frustration oder auf eine konkrete Verbesserung?",
        ),
        QuoteCardItem(
            id = "q-6",
            quote = "Zwischen Reiz und Reaktion liegt ein Raum. In diesem Raum liegt unsere Macht zur Wahl unserer Reaktion.",
            author = "Viktor E. Frankl",
            contextEra = "Logotherapie & Existenzanalyse",
            reflectionPrompt = "Atme heute einmal tief durch, bevor du auf Kritik oder Stress impulsiv antwortest.",
        ),
    )

    private val PUZZLE_POOL = listOf(
        BrainPuzzleCardItem(
            id = "bp-1",
            title = "Dual-Color Stroop-Test",
            category = "Inhibitorische Kontrolle & Präfrontaler Kortex",
            question = "Welche FARBE hat die Schrift des Worts „BLAU“ hier: \n\n[ B L A U ] (in roter Farbe dargestellt)",
            options = listOf("Blau", "Rot", "Schwarz"),
            correctIndex = 1,
            explanation = "Richtig! Die Schriftfarbe ist ROT. Dein Gehirn liest automatisch das Wort 'Blau' und muss den Impuls bewusst unterdrücken. Genau dieser Widerstand trainiert die Impulskontrolle im präfrontalen Kortex.",
            brainRegionTrained = "Präfrontaler Kortex (Exekutive Funktionen)",
        ),
        BrainPuzzleCardItem(
            id = "bp-2",
            title = "Muster-Erkennung & Logik",
            category = "Deduktives Denken & Arbeitsgedächtnis",
            question = "Welche Zahl folgt in dieser Reihe: 2, 6, 12, 20, 30, ?",
            options = listOf("38", "40", "42", "44"),
            correctIndex = 2,
            explanation = "Richtig! Die Differenz wächst jedes Mal um +2 (+4, +6, +8, +10, +12). 30 + 12 = 42. Alternativ: 1x2, 2x3, 3x4, 4x5, 5x6, 6x7 = 42!",
            brainRegionTrained = "Parietallappen & Mathematische Abstraktion",
        ),
        BrainPuzzleCardItem(
            id = "bp-3",
            title = "Laterales Denken",
            category = "Kreative Problemlösung",
            question = "Ein Mann schaut ein Porträt an und sagt: 'Ich habe keine Brüder und keine Schwestern. Doch der Vater dieses Mannes ist meines Vaters Sohn.' Wen zeigt das Porträt?",
            options = listOf("Ihn selbst", "Seinen Sohn", "Seinen Vater", "Seinen Neffen"),
            correctIndex = 1,
            explanation = "Richtig! 'Meines Vaters Sohn' ohne Geschwister ist er selbst. 'Der Vater dieses Mannes [auf dem Bild] ist er selbst'. Also zeigt das Porträt seinen Sohn!",
            brainRegionTrained = "Temporallappen (Sprachliches Verstehen & Perspektivwechsel)",
        ),
        BrainPuzzleCardItem(
            id = "bp-4",
            title = "Wort-Assoziations-Matrix",
            category = "Semantisches Gedächtnis",
            question = "Welches Wort passt zu allen dreien: \n• KERN \n• FALL \n• APFEL",
            options = listOf("Baum", "Schnee", "Schwerkraft", "Kuchen"),
            correctIndex = 1,
            explanation = "Richtig! SCHNEE: Schneekern (Gletscher), Schneefall, Schneeapfel. Diese semantische Suche stärkt die Vernetzung beider Gehirnhälften.",
            brainRegionTrained = "Assoziativer Kortex & Wortabruf",
        ),
    )

    private val LANGUAGE_POOLS = mapOf(
        LearningLanguage.RUSSIAN to listOf(
            LanguageCardItem(
                id = "ru-1",
                language = LearningLanguage.RUSSIAN,
                foreignWord = "Здравствуйте (Sdrástwuyte)",
                nativeTranslation = "Guten Tag / Hallo (höflich)",
                phonetic = "[zdrast-vuj-tje]",
                exampleForeign = "Здравствуйте, как ваши дела?",
                exampleTranslation = "Guten Tag, wie geht es Ihnen?",
                dialogueScenario = "Im Moskauer Café",
                dialoguePartner = "Anna",
                dialoguePrompt = "Anna: „Здравствуйте! Что будете заказывать?“ (Was möchten Sie bestellen?)",
                dialogueReplies = listOf("Кофе, пожалуйста. (Einen Kaffee, bitte.)", "До свидания! (Auf Wiedersehen!)"),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "ru-2",
                language = LearningLanguage.RUSSIAN,
                foreignWord = "Спасибо большое (Spasíba bal'shóye)",
                nativeTranslation = "Vielen Dank",
                phonetic = "[spa-si-ba bal-sho-je]",
                exampleForeign = "Спасибо большое за помощь!",
                exampleTranslation = "Vielen Dank für die Hilfe!",
                dialogueScenario = "Nach dem Bezahlen",
                dialoguePartner = "Kellner",
                dialoguePrompt = "Kellner bringt die Rechnung: „Вот ваш счёт.“ (Hier ist Ihre Rechnung.)",
                dialogueReplies = listOf("Спасибо большое. (Vielen Dank.)", "Нет, я не знаю. (Nein, ich weiß nicht.)"),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "ru-3",
                language = LearningLanguage.RUSSIAN,
                foreignWord = "Извините, где метро? (Iswiníti, gde metrό?)",
                nativeTranslation = "Entschuldigen Sie, wo ist die Metro?",
                phonetic = "[iz-vi-ni-tje g-dje me-tro]",
                exampleForeign = "Извините, пожалуйста, где здесь метро?",
                exampleTranslation = "Entschuldigen Sie bitte, wo ist hier die Metro?",
                dialogueScenario = "Orientierung in der Stadt",
                dialoguePartner = "Passant",
                dialoguePrompt = "Passant: „Прямо и направо.“ (Geradeaus und rechts.)",
                dialogueReplies = listOf("Большое спасибо! (Vielen Dank!)", "Меня зовут Иван. (Ich heiße Ivan.)"),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "ru-4",
                language = LearningLanguage.RUSSIAN,
                foreignWord = "Приятного аппетита (Priyátnawa appetíta)",
                nativeTranslation = "Guten Appetit",
                phonetic = "[pri-jat-na-va a-pe-ti-ta]",
                exampleForeign = "Приятного аппетита всем за столом!",
                exampleTranslation = "Guten Appetit allen am Tisch!",
                dialogueScenario = "Am Esstisch",
                dialoguePartner = "Gastgeber",
                dialoguePrompt = "Gastgeber serviert das Essen: „Угощайтесь!“ (Bedient euch!)",
                dialogueReplies = listOf("Спасибо, приятного аппетита! (Danke, guten Appetit!)", "Спокойной ночи! (Gute Nacht!)"),
                correctReplyIndex = 0,
            ),
        ),
        LearningLanguage.ENGLISH to listOf(
            LanguageCardItem(
                id = "en-1",
                language = LearningLanguage.ENGLISH,
                foreignWord = "Resilience",
                nativeTranslation = "Widerstandskraft / Durchhaltevermögen",
                phonetic = "[rɪˈzɪl.jəns]",
                exampleForeign = "True resilience is staying calm amidst chaos.",
                exampleTranslation = "Wahre Widerstandskraft bedeutet, inmitten des Chaos ruhig zu bleiben.",
                dialogueScenario = "Im Business-Meeting",
                dialoguePartner = "David",
                dialoguePrompt = "David: „The deadline was moved up by two days. How should we handle this?“",
                dialogueReplies = listOf("Let's prioritize the core tasks and stay focused.", "I think we should just give up immediately."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "en-2",
                language = LearningLanguage.ENGLISH,
                foreignWord = "Serendipity",
                nativeTranslation = "Glücklicher Zufall / Überraschende Entdeckung",
                phonetic = "[ˌser.ənˈdɪp.ə.ti]",
                exampleForeign = "Meeting my business partner on that flight was pure serendipity.",
                exampleTranslation = "Meinen Geschäftspartner auf jenem Flug zu treffen, war purer glücklicher Zufall.",
                dialogueScenario = "Freunde im Gespräch",
                dialoguePartner = "Emma",
                dialoguePrompt = "Emma: „How did you find this incredible hidden café?“",
                dialogueReplies = listOf("Total serendipity! I took a wrong turn and stumbled upon it.", "No, I haven't seen it yet."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "en-3",
                language = LearningLanguage.ENGLISH,
                foreignWord = "Ubiquitous",
                nativeTranslation = "Allgegenwärtig / Überall anzutreffen",
                phonetic = "[juːˈbɪk.wɪ.təs]",
                exampleForeign = "Smartphones have become ubiquitous in modern society.",
                exampleTranslation = "Smartphones sind in der modernen Gesellschaft allgegenwärtig geworden.",
                dialogueScenario = "Technologie-Diskussion",
                dialoguePartner = "Sarah",
                dialoguePrompt = "Sarah: „Why is cloud computing so essential today?“",
                dialogueReplies = listOf("Because cloud services are ubiquitous and allow instant access.", "Because it is completely obsolete."),
                correctReplyIndex = 0,
            ),
        ),
        LearningLanguage.SPANISH to listOf(
            LanguageCardItem(
                id = "es-1",
                language = LearningLanguage.SPANISH,
                foreignWord = "¿Cuánto cuesta? (Kuanto kuesta)",
                nativeTranslation = "Wie viel kostet das?",
                phonetic = "[kwan-to kwes-ta]",
                exampleForeign = "¿Cuánto cuesta este libro de arte?",
                exampleTranslation = "Wie viel kostet dieses Kunstbuch?",
                dialogueScenario = "Auf dem Markt in Barcelona",
                dialoguePartner = "Carlos",
                dialoguePrompt = "Carlos: „¡Hola! Todo está fresco hoy.“ (Hallo! Alles ist frisch heute.)",
                dialogueReplies = listOf("¿Cuánto cuesta un kilo de manzanas?", "No me gusta dormir."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "es-2",
                language = LearningLanguage.SPANISH,
                foreignWord = "Muchas gracias / De nada",
                nativeTranslation = "Vielen Dank / Gern geschehen",
                phonetic = "[mu-tshas gra-thjas / de na-da]",
                exampleForeign = "Muchas gracias por tu valiosa ayuda.",
                exampleTranslation = "Vielen Dank für deine wertvolle Hilfe.",
                dialogueScenario = "Im Tapas-Restaurant",
                dialoguePartner = "Camarero",
                dialoguePrompt = "Camarero bringt die Spezialität: „¡Buen provecho!“",
                dialogueReplies = listOf("¡Muchas gracias, se ve delicioso!", "¿Dónde está el aeropuerto?"),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "es-3",
                language = LearningLanguage.SPANISH,
                foreignWord = "¿Dónde está la estación?",
                nativeTranslation = "Wo ist der Bahnhof?",
                phonetic = "[don-de es-ta la es-ta-thjon]",
                exampleForeign = "¿Disculpe, dónde está la estación central?",
                exampleTranslation = "Entschuldigen Sie, wo ist der Hauptbahnhof?",
                dialogueScenario = "Unterwegs in Madrid",
                dialoguePartner = "Policía",
                dialoguePrompt = "Policía: „¿Le puedo ayudar en algo?“",
                dialogueReplies = listOf("Sí, ¿dónde está la estación de metro más cercana?", "Tengo diez años."),
                correctReplyIndex = 0,
            ),
        ),
        LearningLanguage.JAPANESE to listOf(
            LanguageCardItem(
                id = "ja-1",
                language = LearningLanguage.JAPANESE,
                foreignWord = "乾杯 (Kanpai)",
                nativeTranslation = "Prost! / Zum Wohl!",
                phonetic = "[kam-pai]",
                exampleForeign = "皆で乾杯しましょう！ (Minna de kanpai shimashou!)",
                exampleTranslation = "Lasst uns alle anstoßen!",
                dialogueScenario = "Im Izakaya in Tokio",
                dialoguePartner = "Kenji",
                dialoguePrompt = "Kenji hebt das Glas: „お疲れ様でした！“ (Gute Arbeit heute!)",
                dialogueReplies = listOf("乾杯！ (Kanpai!)", "おはようございます (Guten Morgen)"),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "ja-2",
                language = LearningLanguage.JAPANESE,
                foreignWord = "ありがとうございます (Arigatou gozaimasu)",
                nativeTranslation = "Vielen herzlichen Dank (höflich)",
                phonetic = "[a-ri-ga-to-o go-zai-mas]",
                exampleForeign = "親切にしていただき、ありがとうございます。",
                exampleTranslation = "Vielen Dank für Ihre Freundlichkeit.",
                dialogueScenario = "Im Konbini (Convenience Store)",
                dialoguePartner = "Kassierer",
                dialoguePrompt = "Kassierer überreicht die Quittung: „ありがとうございました。“",
                dialogueReplies = listOf("どうも、ありがとうございます！", "さようなら、明日！"),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "ja-3",
                language = LearningLanguage.JAPANESE,
                foreignWord = "すみません (Sumimasen)",
                nativeTranslation = "Entschuldigung / Verzeihung / Bitte",
                phonetic = "[su-mi-ma-sen]",
                exampleForeign = "すみません、駅はどこですか？",
                exampleTranslation = "Entschuldigung, wo ist der Bahnhof?",
                dialogueScenario = "In der U-Bahn-Station Shinjuku",
                dialoguePartner = "Stationsbeamter",
                dialoguePrompt = "Beamter: „何かお困りですか？“ (Kann ich Ihnen helfen?)",
                dialogueReplies = listOf("すみません、JR線への乗り換えはどちらですか？", "おやすみなさい。"),
                correctReplyIndex = 0,
            ),
        ),
        LearningLanguage.FRENCH to listOf(
            LanguageCardItem(
                id = "fr-1",
                language = LearningLanguage.FRENCH,
                foreignWord = "S'il vous plaît",
                nativeTranslation = "Bitte (höflich)",
                phonetic = "[sil vu ple]",
                exampleForeign = "Un café et un croissant, s'il vous plaît.",
                exampleTranslation = "Ein Kaffee und ein Croissant, bitte.",
                dialogueScenario = "In einer Pariser Bäckerei",
                dialoguePartner = "Boulanger",
                dialoguePrompt = "Boulanger: „Bonjour! Que désirez-vous?“",
                dialogueReplies = listOf("Une baguette tradition, s'il vous plaît.", "Au revoir, monsieur."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "fr-2",
                language = LearningLanguage.FRENCH,
                foreignWord = "Merci beaucoup / De rien",
                nativeTranslation = "Vielen Dank / Keine Ursache",
                phonetic = "[mɛʁ.si bo.ku / də ʁjɛ̃]",
                exampleForeign = "Merci beaucoup pour votre accueil chaleureux.",
                exampleTranslation = "Vielen Dank für Ihren herzlichen Empfang.",
                dialogueScenario = "Im Bistro",
                dialoguePartner = "Serveur",
                dialoguePrompt = "Serveur serviert das Dessert: „Voilà votre crème brûlée.“",
                dialogueReplies = listOf("Merci beaucoup, c'est parfait!", "Je ne sais pas."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "fr-3",
                language = LearningLanguage.FRENCH,
                foreignWord = "Où est la gare?",
                nativeTranslation = "Wo ist der Bahnhof?",
                phonetic = "[u ɛ la ɡaʁ]",
                exampleForeign = "Pardon madame, où est la gare Saint-Lazare?",
                exampleTranslation = "Verzeihung Madame, wo ist der Bahnhof Saint-Lazare?",
                dialogueScenario = "Unterwegs in Paris",
                dialoguePartner = "Passante",
                dialoguePrompt = "Passante: „Vous cherchez une direction?“",
                dialogueReplies = listOf("Oui, où est la station de métro la plus proche?", "J'aime les pommes."),
                correctReplyIndex = 0,
            ),
        ),
        LearningLanguage.ITALIAN to listOf(
            LanguageCardItem(
                id = "it-1",
                language = LearningLanguage.ITALIAN,
                foreignWord = "La dolce vita",
                nativeTranslation = "Das süße Leben / Die Kunst des Genießens",
                phonetic = "[la dol-tsche vi-ta]",
                exampleForeign = "Prenditi del tempo per apprezzare la dolce vita.",
                exampleTranslation = "Nimm dir Zeit, das süße Leben zu genießen.",
                dialogueScenario = "An der Piazza in Rom",
                dialoguePartner = "Marco",
                dialoguePrompt = "Marco: „Prendiamo un caffè al sole?“",
                dialogueReplies = listOf("Volentieri, godiamoci il sole!", "No, fa troppo freddo a mezzogiorno."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "it-2",
                language = LearningLanguage.ITALIAN,
                foreignWord = "Buongiorno, un caffè per favore",
                nativeTranslation = "Guten Tag, einen Espresso bitte",
                phonetic = "[bwon-djor-no, un kaf-fe per fa-vo-re]",
                exampleForeign = "Buongiorno! Un espresso doppio al banco, per favore.",
                exampleTranslation = "Guten Morgen! Einen doppelten Espresso an der Bar, bitte.",
                dialogueScenario = "In einer Bar in Florenz",
                dialoguePartner = "Barista",
                dialoguePrompt = "Barista: „Cosa posso portarle?“",
                dialogueReplies = listOf("Un caffè macchiato e un cornetto, grazie!", "Buonanotte."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "it-3",
                language = LearningLanguage.ITALIAN,
                foreignWord = "Dov'è il centro storico?",
                nativeTranslation = "Wo ist die Altstadt / das Zentrum?",
                phonetic = "[do-ve il tschen-tro sto-ri-ko]",
                exampleForeign = "Scusi, dov'è il Duomo?",
                exampleTranslation = "Entschuldigen Sie, wo ist der Dom?",
                dialogueScenario = "Orientierung in Venedig",
                dialoguePartner = "Gondoliere",
                dialoguePrompt = "Gondoliere: „Serve aiuto con la strada?“",
                dialogueReplies = listOf("Sì, dov'è Piazza San Marco, per favore?", "Non ho sonno."),
                correctReplyIndex = 0,
            ),
        ),
        LearningLanguage.GERMAN_ADVANCED to listOf(
            LanguageCardItem(
                id = "de-1",
                language = LearningLanguage.GERMAN_ADVANCED,
                foreignWord = "Eklektisch (Adjektiv)",
                nativeTranslation = "Aus Verschiedenem das Beste auswählend",
                phonetic = "[ɛˈklɛktɪʃ]",
                exampleForeign = "Sein Musikgeschmack ist eklektisch: von Barock bis Synthwave.",
                exampleTranslation = "Er kombiniert gezielt Elemente verschiedener Stile zu einem neuen Ganzen.",
                dialogueScenario = "Fachgespräch über Architektur",
                dialoguePartner = "Professor",
                dialoguePrompt = "Professor: „Wie würden Sie den Stil dieses Gebäudes treffend beschreiben?“",
                dialogueReplies = listOf("Als äußerst eklektisch, da Gotik und Moderne harmonieren.", "Das Gebäude ist einfach nur groß."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "de-2",
                language = LearningLanguage.GERMAN_ADVANCED,
                foreignWord = "Ephemer (Adjektiv)",
                nativeTranslation = "Flüchtig, nur einen Tag dauernd, vergänglich",
                phonetic = "[eˈfeːmɐ]",
                exampleForeign = "Trends in den sozialen Medien sind oft ephemer und schnell vergessen.",
                exampleTranslation = "Wahres Wissen überdauert, während bloße Trends verblassen.",
                dialogueScenario = "Diskussion über Kunst",
                dialoguePartner = "Kritikerin",
                dialoguePrompt = "Kritikerin: „Was zeichnet diese Street-Art-Installation aus?“",
                dialogueReplies = listOf("Ihr ephemerer Charakter verleiht ihr besondere Dringlichkeit.", "Dass man sie kaufen kann."),
                correctReplyIndex = 0,
            ),
            LanguageCardItem(
                id = "de-3",
                language = LearningLanguage.GERMAN_ADVANCED,
                foreignWord = "Serenität (Substantiv, feminin)",
                nativeTranslation = "Innere Heiterkeit, vollkommene Gemütsruhe",
                phonetic = "[zeʁeniˈtɛːt]",
                exampleForeign = "Trotz des Sturms um ihn herum bewahrte er eine unerschütterliche Serenität.",
                exampleTranslation = "Stoische Gelassenheit angesichts unkontrollierbarer äußerer Umstände.",
                dialogueScenario = "Führungs-Coaching",
                dialoguePartner = "Mentor",
                dialoguePrompt = "Mentor: „Wie begegnest du unvorhergesehenen Krisensituationen?“",
                dialogueReplies = listOf("Mit bewusster Serenität und klarem Fokus auf das Machbare.", "Mit Panik und Beschuldigungen."),
                correctReplyIndex = 0,
            ),
        ),
    )

    private val HISTORY_POOL = listOf(
        HistoryCardItem(
            id = "h-1",
            dateLabel = "Heute vor 69 Jahren (1957)",
            eventTitle = "Der Start von Sputnik 1",
            description = "Die Menschheit betritt erstmals das Weltraumzeitalter: Die Sowjetunion schießt den ersten künstlichen Satelliten in die Erdumlaufbahn.",
            whyItMatters = "Dieser Moment leitete das Raumfahrtzeitalter ein, führte zur Gründung der NASA und schuf die Grundlagen für heutige GPS- und Satellitentechnologie.",
        ),
        HistoryCardItem(
            id = "h-2",
            dateLabel = "Historischer Wendepunkt",
            eventTitle = "Die Entschlüsselung des Rosetta-Steins (1822)",
            description = "Jean-François Champollion gelingt der Durchbruch: Nach über 1.000 Jahren Schweigen können altägyptische Hieroglyphen wieder gelesen werden.",
            whyItMatters = "Es bewies, dass Hieroglyphen phonetische Laute darstellten, und öffnete das Tor zu 3.000 Jahren verloren geglaubter menschlicher Zivilisationsgeschichte.",
        ),
        HistoryCardItem(
            id = "h-3",
            dateLabel = "Meilenstein der Medizin",
            eventTitle = "Die Entdeckung des Penicillins (1928)",
            description = "Alexander Fleming bemerkt in einer vergessenen Petrischale, dass der Schimmelpilz Penicillium notatum das Wachstum von Staphylokokken stoppt.",
            whyItMatters = "Die Geburt der Antibiotika rettete bis heute schätzungsweise über 200 Millionen Menschen das Leben und veränderte die moderne Medizin für immer.",
        ),
    )

    private val MENTAL_MODEL_POOL = listOf(
        MentalModelCardItem(
            id = "mm-1",
            modelName = "Bestätigungsfehler (Confirmation Bias)",
            category = "Erkenntnistheorie & Kognition",
            explanation = "Die unbewusste Tendenz, nur jene Informationen wahrzunehmen und zu behalten, die unsere bereits bestehende Meinung bestätigen – während Gegenbeweise ignoriert werden.",
            realLifeExample = "Wer glaubt, dass eine bestimmte Aktie steigt, liest nur optimistische Analystenberichte und übergeht kritische Warnungen.",
            actionableDefense = "Suche aktiv nach den stärksten Gegenargumenten zu deiner eigenen These (Steel-Manning), bevor du eine Entscheidung triffst.",
        ),
        MentalModelCardItem(
            id = "mm-2",
            modelName = "Sunk Cost Fallacy (Kostenfalle)",
            category = "Entscheidungsfindung",
            explanation = "Man hält an einer schlechten Sache fest, nur weil man bereits viel Zeit, Geld oder Mühe investiert hat – obwohl die Zukunftsaussichten negativ sind.",
            realLifeExample = "Einen schlechten Film bis zum Ende schauen oder an einem gescheiterten Projekt festhalten, weil man schon Monate investiert hat.",
            actionableDefense = "Frage dich: 'Wenn ich heute völlig neu und unbelastet entscheiden müsste – würde ich jetzt einsteigen?' Wenn nein: Aussteigen!",
        ),
        MentalModelCardItem(
            id = "mm-3",
            modelName = "Hanlon's Razor (Hanlons Gesetz)",
            category = "Soziale Interaktion & Gelassenheit",
            explanation = "Schreibe niemals der Bosheit zu, was durch bloße Unachtsamkeit, Überlastung oder Unwissenheit hinreichend erklärt werden kann.",
            realLifeExample = "Jemand antwortet dir 2 Tage nicht auf WhatsApp. Ist er absichtlich respektlos? Meistens war er einfach nur gestresst oder abgelenkt.",
            actionableDefense = "Nimm Dinge seltener persönlich. Es erspart dir 90 % allen unnötigen zwischenmenschlichen Ärgers.",
        ),
    )

    private val SCIENCE_POOL = listOf(
        ScienceCardItem(
            id = "sci-1",
            phenomenon = "Neuroplastizität",
            question = "Kann sich das erwachsene Gehirn noch physisch verändern?",
            coreExplanation = "Ja! Jedes Mal, wenn du etwas Neues lernst oder eine Gewohnheit änderst, bilden Synapsen neue physische Verästelungen. Das Gehirn ist wie ein Muskel verformbar bis ins hohe Alter.",
            fascinatingDetail = "Londoner Taxifahrer haben nach dem Auswendiglernen des Stadtplans ('The Knowledge') einen messbar vergrößerten Hippocampus.",
        ),
        ScienceCardItem(
            id = "sci-2",
            phenomenon = "Gravitationswellen",
            question = "Können wir den Raum selbst zittern hören?",
            coreExplanation = "Wenn zwei gigantische schwarze Löcher im All kollidieren, stauchen und dehnen sie die Raumzeit selbst wie Wellen auf einem Teich. 2015 gelang mit LIGO der erste direkte Nachweis.",
            fascinatingDetail = "Die gemessene Verschiebung der LIGO-Laserarme betrug ein Tausendstel des Durchmessers eines einzigen Protons!",
        ),
        ScienceCardItem(
            id = "sci-3",
            phenomenon = "Die Mitochondrien-Symbiose",
            question = "Woher stammt die Energie in all deinen Zellen?",
            coreExplanation = "Vor rund 1,5 Milliarden Jahren verschluckte eine Urzelle ein Bakterium, verdautes es aber nicht, sondern lebte mit ihm in Symbiose. Daraus wurden unsere Zellkraftwerke (Mitochondrien).",
            fascinatingDetail = "Mitochondrien besitzen bis heute ihre eigene, separate DNA – und werden ausschließlich über die Mutterlinie vererbt.",
        ),
    )

    private val FINANCE_POOL = listOf(
        FinanceCardItem(
            id = "fin-1",
            title = "Die 72er-Regel (Zinseszins-Magie)",
            corePrinciple = "Teile die Zahl 72 durch deinen jährlichen Zinssatz. Das Ergebnis ist die Anzahl der Jahre, nach denen sich dein Kapital verdoppelt!",
            practicalExample = "Bei 7 % jährlicher Rendite: 72 ÷ 7 ≈ 10,2 Jahre. Aus 10.000 € werden in 10 Jahren 20.000 €, in 20 Jahren 40.000 € – ganz ohne Nachzahlung.",
            takeawayRule = "Zeit im Markt schlägt Timing des Marktes. Je früher du beginnst, desto exponentieller arbeitet die Zinseszins-Kurve für dich.",
        ),
        FinanceCardItem(
            id = "fin-2",
            title = "Der Notgroschen (3–6 Monatsausgaben)",
            corePrinciple = "Liquidität schützt vor Schuldenfallen und emotionalen Fehlentscheidungen in Krisenzeiten.",
            practicalExample = "Wenn deine monatlichen Fixkosten 1.500 € betragen, gehören 4.500 € bis 9.000 € auf ein hochverzinstes Tagesgeldkonto – nicht in volatile Aktien.",
            takeawayRule = "Der Notgroschen ist keine Renditeanlage, sondern eine psychologische Friedensversicherung gegen Stress.",
        ),
        FinanceCardItem(
            id = "fin-3",
            title = "Breite Diversifikation (Welt-Portfolio)",
            corePrinciple = "Wer in einzelne Aktien wettet, geht unsystematisches Risiko ein. Ein breit gestreuter All-World-ETF partizipiert an der gesamten globalen Wertschöpfung.",
            practicalExample = "Über 1.500 Unternehmen aus Industrie- und Schwellenländern federn Einbrüche einzelner Branchen zuverlässig ab.",
            takeawayRule = "Suche nicht nach der Nadel im Heuhaufen. Kaufe einfach den gesamten Heuhaufen!",
        ),
    )

    private val GEOGRAPHY_POOL = listOf(
        GeographyCardItem(
            id = "geo-1",
            question = "Welches Land der Erde hat die meisten natürlichen Seen?",
            options = listOf("Russland", "Kanada", "Finnland", "Brasilien"),
            correctIndex = 1,
            interestingFact = "Kanada besitzt über 60 % aller natürlichen Seen der Erde (über 2 Millionen Seen). Mehr als der gesamte Rest der Welt zusammen!",
        ),
        GeographyCardItem(
            id = "geo-2",
            question = "Welcher dieser Staaten ist flächenmäßig der größte Binnenstaat der Welt (ohne Meereszugang)?",
            options = listOf("Mongolei", "Kasachstan", "Tschad", "Bolivien"),
            correctIndex = 1,
            interestingFact = "Kasachstan ist mit über 2,7 Millionen Quadratkilometern der mit Abstand größte Binnenstaat der Erde und das neuntgrößte Land der Welt.",
        ),
        GeographyCardItem(
            id = "geo-3",
            question = "In welcher Stadt liegt der tiefste natürliche Punkt auf dem Festland der Erde (-430 m)?",
            options = listOf("Totes Meer (Jordanien/Israel)", "Death Valley (USA)", "Assalsee (Dschibuti)", "Turpan-Senke (China)"),
            correctIndex = 0,
            interestingFact = "Das Ufer des Toten Meeres liegt über 430 Meter unter dem Meeresspiegel. Der extrem hohe Salzgehalt (ca. 33 %) lässt Menschen mühelos treiben.",
        ),
    )

    private val SPEED_MATH_POOL = listOf(
        SpeedMathCardItem(
            id = "math-1",
            trickTitle = "Zweistellige Zahlen mit 11 multiplizieren",
            formulaShortcut = "Die beiden Ziffern addieren und die Summe in die Mitte schreiben!",
            explanation = "Beispiel: 35 × 11. Addiere 3 + 5 = 8. Schiebe die 8 zwischen 3 und 5 -> 385!",
            practiceChallenge = "Was ist 43 × 11?",
            challengeResult = "473 (da 4 + 3 = 7)",
        ),
        SpeedMathCardItem(
            id = "math-2",
            trickTitle = "Quadrieren von Zahlen, die auf 5 enden",
            formulaShortcut = "Erste Ziffer × (nächste Ziffer) rechnen und '25' anhängen!",
            explanation = "Beispiel: 65². Nimm die 6, rechne 6 × 7 = 42. Hänge 25 an -> 4225!",
            practiceChallenge = "Was ist 35²?",
            challengeResult = "1225 (da 3 × 4 = 12, dann 25)",
        ),
        SpeedMathCardItem(
            id = "math-3",
            trickTitle = "Schnell 15 % Trinkgeld im Kopf berechnen",
            formulaShortcut = "10 % berechnen (Komma um eins nach links) und die Hälfte davon addieren!",
            explanation = "Beispiel: 48,00 € Rechnung. 10 % sind 4,80 €. Die Hälfte davon sind 2,40 €. 4,80 + 2,40 = 7,20 € Trinkgeld!",
            practiceChallenge = "Was sind 15 % von 60,00 €?",
            challengeResult = "9,00 € (6,00 € + 3,00 €)",
        ),
    )

    private val ART_POOL = listOf(
        ArtCultureCardItem(
            id = "art-1",
            masterpieceTitle = "Die Schule von Athen",
            artist = "Raffael (Raffaello Sanzio)",
            yearAndOrigin = "1509–1511 • Apostolischer Palast, Rom",
            backStory = "Im Zentrum stehen Platon (zeigt zum Himmel, Ideenlehre) und Aristoteles (zeigt zur Erde, Empirie). Raffael vereinte das antike Denken mit der humanistischen Renaissance.",
        ),
        ArtCultureCardItem(
            id = "art-2",
            masterpieceTitle = "Sternennacht",
            artist = "Vincent van Gogh",
            yearAndOrigin = "1889 • Saint-Rémy-de-Provence",
            backStory = "Gemalt aus dem Fenster seiner Zelle in der Heilanstalt. Van Gogh visualisierte die turbulente Himmelsströmung mit einer mathematischen Präzision, die modernen Strömungsmodellen gleicht.",
        ),
        ArtCultureCardItem(
            id = "art-3",
            masterpieceTitle = "Die große Welle vor Kanagawa",
            artist = "Katsushika Hokusai",
            yearAndOrigin = "ca. 1831 • Edo-Zeit, Japan",
            backStory = "Dieser Farbholzschnitt verbindet traditionelle japanische Komposition mit dem damals neuartigen Pigment 'Preußisch Blau' aus Europa. Der heilige Berg Fuji ruht friedlich im tosenden Wellental.",
        ),
    )

    private val VOCABULARY_POOL = listOf(
        VocabularyCardItem(
            id = "voc-1",
            word = "Serendipität (Substantiv, feminin)",
            wordType = "Fremdwort aus dem Englischen / Persischen",
            definition = "Eine zufällige Beobachtung von etwas, das man ursprünglich gar nicht gesucht hat, die sich als fruchtbare Entdeckung erweist.",
            etymology = "Zurückgehend auf das persische Märchen 'Die drei Prinzen von Serendip'. Bekanntestes Beispiel: Die Entdeckung des Penicillins durch Alexander Fleming.",
            sampleSentence = "Dass wir uns auf der Konferenz trafen, war reine Serendipität und legte den Grundstein unseres Erfolgs.",
        ),
        VocabularyCardItem(
            id = "voc-2",
            word = "Resilienz (Substantiv, feminin)",
            wordType = "Aus dem Lateinischen 'resilire' (zurückspringen)",
            definition = "Die psychische Widerstandskraft, Krisen, Rückschläge und Traumata zu bewältigen und gestärkt daraus hervorzugehen.",
            etymology = "Ursprünglich aus der Materialkunde (ein Stoff, der nach starker Verformung in seine Ausgangsgestalt zurückkehrt).",
            sampleSentence = "Emotionale Resilienz ist keine angeborene Eigenschaft, sondern eine trainierbare Geistesgewohnheit.",
        ),
        VocabularyCardItem(
            id = "voc-3",
            word = "Kakophonie (Substantiv, feminin)",
            wordType = "Aus dem Altgriechischen 'kakos' (schlecht) & 'phone' (Laut/Klang)",
            definition = "Ein unangenehmer, schriller Missklang aus widersprüchlichen Geräuschen oder unvereinbaren Meinungen.",
            etymology = "Gegenteil von Euphemismus oder Harmonie.",
            sampleSentence = "In der heutigen Kakophonie aus Push-Nachrichten und Social-Media-Lärm wird gezielte Stille zum wertvollsten Luxus.",
        ),
    )

    private val PHILOSOPHY_POOL = listOf(
        PhilosophyCardItem(
            id = "phil-1",
            dilemmaTitle = "Das Schiff des Theseus",
            scenario = "Ein Schiff aus Holz segelt über die Meere. Nach und nach wird jedes morsche Holzbrett durch ein nagelneues ersetzt, bis kein einziges Originalteil mehr vorhanden ist.",
            optionA = "Es ist noch dasselbe Schiff (Identität liegt in Kontinuität & Form)",
            optionB = "Es ist ein völlig neues Schiff (Identität liegt in der Materie)",
            schoolA = "Strukturalismus & Kontinuitätstheorie",
            schoolB = "Materialistischer Reduktionismus",
            philosophicalInsight = "Fast alle Zellen deines eigenen Körpers erneuern sich alle 7 bis 10 Jahre. Bist du heute noch dieselbe Person wie vor 10 Jahren? Was macht deine Identität wirklich aus?",
        ),
        PhilosophyCardItem(
            id = "phil-2",
            dilemmaTitle = "Das Trolley-Problem",
            scenario = "Eine führerlose Bahn rollt auf 5 Gleisarbeiter zu. Du kannst eine Weiche umlegen, wodurch die Bahn auf ein Nebengleis fährt – dort arbeitet jedoch 1 Gleisarbeiter.",
            optionA = "Weiche umlegen (1 Leben opfern, um 5 Leben zu retten)",
            optionB = "Nicht eingreifen (Man darf kein unbeteiligtes Leben aktiv opfern)",
            schoolA = "Utilitarismus (Das größte Wohl für die größte Zahl)",
            schoolB = "Deontologie / Pflichtenethik (Kant: Der Mensch ist Zweck, kein Mittel)",
            philosophicalInsight = "Entscheidungen im echten Leben (z. B. Algorithmen für autonomes Fahren oder Triage im Krankenhaus) verlangen täglich den Spagat zwischen utilitaristischer Schadensminimierung und moralischen Grundrechten.",
        ),
        PhilosophyCardItem(
            id = "phil-3",
            dilemmaTitle = "Der Schleier des Nichtwissens",
            scenario = "Stelle dir vor, du sollst die Gesetze einer gerechten Gesellschaft entwerfen – weißt aber vorher nicht, ob du darin reich, arm, gesund, krank oder begabt sein wirst.",
            optionA = "Maximale Chancengleichheit & starkes soziales Sicherheitsnetz",
            optionB = "Minimalstaat mit absolutem Vorrang für individuelle Freiheit",
            schoolA = "John Rawls' Theorie der Gerechtigkeit",
            schoolB = "Libertarismus (Robert Nozick)",
            philosophicalInsight = "Wahre Fairness entsteht, wenn die Mächtigen sich vorstellen müssen, morgen die Schwächsten im System zu sein.",
        ),
    )

    fun getAllMasterCards(): List<MindfulCard> {
        val list = mutableListOf<MindfulCard>()
        list.addAll(QUOTE_POOL)
        list.addAll(PUZZLE_POOL)
        LANGUAGE_POOLS.values.forEach { list.addAll(it) }
        list.addAll(HISTORY_POOL)
        list.addAll(MENTAL_MODEL_POOL)
        list.add(
            BreathworkCardItem(
                id = "breath-1",
                title = "Box Breathing (4-4-4-4)",
                scientificBenefit = "Aktiviert den Parasympathikus, senkt nachweislich den Cortisolspiegel im Blut und steigert den mentalen Fokus innerhalb von 60 Sekunden.",
            )
        )
        list.addAll(SCIENCE_POOL)
        list.addAll(FINANCE_POOL)
        list.addAll(GEOGRAPHY_POOL)
        list.addAll(SPEED_MATH_POOL)
        list.addAll(ART_POOL)
        list.addAll(VOCABULARY_POOL)
        list.addAll(PHILOSOPHY_POOL)
        return list
    }

    fun findCardById(id: String): MindfulCard? {
        val all = getAllMasterCards()
        // 1. Exact match
        all.find { it.id == id }?.let { return it }

        // 2. Prefix match (z.B. "quote-1-123" -> match "quote-1")
        val parts = id.split("-")
        if (parts.size >= 2) {
            val prefix = "${parts[0]}-${parts[1]}"
            all.find { it.id == prefix || it.id.startsWith(prefix) }?.let { return it }
        }
        return null
    }
}

