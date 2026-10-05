package com.hikari.app.domain.feed

import java.util.Calendar

object MindfulFeedContentProvider {

    fun getDailyCards(
        calendar: Calendar = Calendar.getInstance(),
        enabledModules: Set<MindfulModuleType>,
        language: LearningLanguage,
    ): List<MindfulCard> {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val cards = mutableListOf<MindfulCard>()

        // 1. ZITATE & LEBENSWEISHEITEN (Exakt max 2 / Tag)
        if (MindfulModuleType.QUOTE in enabledModules) {
            val q1Index = (dayOfYear * 2) % QUOTE_POOL.size
            val q2Index = (dayOfYear * 2 + 1) % QUOTE_POOL.size
            cards.add(QUOTE_POOL[q1Index].copy(id = "quote-1-$dayOfYear"))
            cards.add(QUOTE_POOL[q2Index].copy(id = "quote-2-$dayOfYear"))
        }

        // 2. GEHIRNJOGGING & KOGNITION (Exakt max 2 / Tag)
        if (MindfulModuleType.BRAIN_PUZZLE in enabledModules) {
            val p1Index = (dayOfYear * 2) % PUZZLE_POOL.size
            val p2Index = (dayOfYear * 2 + 1) % PUZZLE_POOL.size
            cards.add(PUZZLE_POOL[p1Index].copy(id = "puzzle-1-$dayOfYear"))
            cards.add(PUZZLE_POOL[p2Index].copy(id = "puzzle-2-$dayOfYear"))
        }

        // 3. SPRACHEN LERNEN (Interaktiv nach ausgewählter Sprache)
        if (MindfulModuleType.LANGUAGE in enabledModules) {
            val langPool = LANGUAGE_POOLS[language] ?: LANGUAGE_POOLS[LearningLanguage.RUSSIAN]!!
            val langIndex = dayOfYear % langPool.size
            cards.add(langPool[langIndex].copy(id = "lang-$dayOfYear-${language.code}"))
        }

        // 4. HEUTE IN DER GESCHICHTE
        if (MindfulModuleType.HISTORY in enabledModules) {
            val hIndex = dayOfYear % HISTORY_POOL.size
            cards.add(HISTORY_POOL[hIndex].copy(id = "hist-$dayOfYear"))
        }

        // 5. KRITISCHES DENKEN & DENKFEHLER
        if (MindfulModuleType.MENTAL_MODEL in enabledModules) {
            val mIndex = dayOfYear % MENTAL_MODEL_POOL.size
            cards.add(MENTAL_MODEL_POOL[mIndex].copy(id = "model-$dayOfYear"))
        }

        // 6. 1-MINUTEN-ATEMÜBUNG (Box Breathing)
        if (MindfulModuleType.BREATHWORK in enabledModules) {
            cards.add(
                BreathworkCardItem(
                    id = "breath-$dayOfYear",
                    title = "Box Breathing (4-4-4-4)",
                    scientificBenefit = "Aktiviert den Parasympathikus, senkt nachweislich den Cortisolspiegel im Blut und steigert den mentalen Fokus innerhalb von 60 Sekunden.",
                )
            )
        }

        // 7. WISSENSCHAFTS-HAPPEN
        if (MindfulModuleType.SCIENCE in enabledModules) {
            val sIndex = dayOfYear % SCIENCE_POOL.size
            cards.add(SCIENCE_POOL[sIndex].copy(id = "sci-$dayOfYear"))
        }

        // 8. FINANZIELLE BILDUNG & LIFE SKILLS
        if (MindfulModuleType.FINANCE in enabledModules) {
            val fIndex = dayOfYear % FINANCE_POOL.size
            cards.add(FINANCE_POOL[fIndex].copy(id = "fin-$dayOfYear"))
        }

        // 9. WELTATLAS & GEOGRAFIE-QUIZ
        if (MindfulModuleType.GEOGRAPHY in enabledModules) {
            val gIndex = dayOfYear % GEOGRAPHY_POOL.size
            cards.add(GEOGRAPHY_POOL[gIndex].copy(id = "geo-$dayOfYear"))
        }

        // 10. KOPFRECHNEN & MATHE-TRICKS
        if (MindfulModuleType.SPEED_MATH in enabledModules) {
            val smIndex = dayOfYear % SPEED_MATH_POOL.size
            cards.add(SPEED_MATH_POOL[smIndex].copy(id = "math-$dayOfYear"))
        }

        // 11. KUNST & KULTUR
        if (MindfulModuleType.ART_CULTURE in enabledModules) {
            val aIndex = dayOfYear % ART_POOL.size
            cards.add(ART_POOL[aIndex].copy(id = "art-$dayOfYear"))
        }

        // 12. WORTSCHATZ-MEISTER (Wort des Tages)
        if (MindfulModuleType.VOCABULARY in enabledModules) {
            val vIndex = dayOfYear % VOCABULARY_POOL.size
            cards.add(VOCABULARY_POOL[vIndex].copy(id = "vocab-$dayOfYear"))
        }

        // 13. PHILOSOPHISCHES DILEMMA
        if (MindfulModuleType.PHILOSOPHY in enabledModules) {
            val pIndex = dayOfYear % PHILOSOPHY_POOL.size
            cards.add(PHILOSOPHY_POOL[pIndex].copy(id = "phil-$dayOfYear"))
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
            question = "Welche FARBE hat die Schrift des Worts „BLAU“ hier: \n\n🔴 [ B L A U ]",
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
    )

    private val SCIENCE_POOL = listOf(
        ScienceCardItem(
            id = "sci-1",
            phenomenon = "Neuroplastizität",
            question = "Kann sich das erwachsene Gehirn noch physisch verändern?",
            coreExplanation = "Ja! Jedes Mal, wenn du etwas Neues lernst oder eine Gewohnheit änderst, bilden Synapsen neue physische Verästelungen. Das Gehirn ist wie ein Muskel verformbar bis ins hohe Alter.",
            fascinatingDetail = "Londoner Taxifahrer haben nach dem Auswendiglernen des Stadtplans ('The Knowledge') einen messbar vergrößerten Hippocampus.",
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
    )

    private val GEOGRAPHY_POOL = listOf(
        GeographyCardItem(
            id = "geo-1",
            question = "Welches Land der Erde hat die meisten natürlichen Seen?",
            options = listOf("Russland", "Kanada", "Finnland", "Brasilien"),
            correctIndex = 1,
            interestingFact = "Kanada besitzt über 60 % aller natürlichen Seen der Erde (über 2 Millionen Seen). Mehr als der gesamte Rest der Welt zusammen!",
        ),
    )

    private val SPEED_MATH_POOL = listOf(
        SpeedMathCardItem(
            id = "math-1",
            trickTitle = "Zweistellige Zahlen mit 11 multiplizieren",
            formulaShortcut = "Die beiden Ziffern addieren und die Summe in die Mitte schreiben!",
            explanation = "Beispiel: 35 × 11. Addiere 3 + 5 = 8. Schiebe die 8 zwischen 3 und 5 ➔ 385!",
            practiceChallenge = "Was ist 43 × 11?",
            challengeResult = "473 (da 4 + 3 = 7)",
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
    )
}
