"""Deepdive-Inhalte Teil 2: Beruf, Familie, Gefühle, Verabreden, Meinung, Abschied."""
from part1 import E, V

ENTRIES = {
    "d06_vrach": {
        "register": "neutral",
        "summary": "Viele Berufe haben nur eine männliche Form, auch für Frauen: врач (Arzt/Ärztin), инжен+ер, дир+ектор. Das Geschlecht zeigt das Verb: Она врач, она пришл+а.",
        "variants": [
            V("Я студ+ент / студ+ентка.", "Ich bin Student / Studentin.", "Für Studierende gibt es die weibliche Endung -ка."),
            V("Я программ+ист / программ+истка.", "Ich bin Programmierer / Programmiererin.", "Mit weiblichem -ка möglich, im Beruf oft aber männlich genutzt."),
            V("Я уч+итель / уч+ительница.", "Ich bin Lehrer / Lehrerin.", "Beides üblich; die weibliche Form ist normal."),
            V("Я врач.", "Ich bin Arzt / Ärztin.", "Für Frauen gilt dieselbe Form: Она врач."),
        ],
        "examples": [
            E("Я врач.", "Ich bin Arzt / Ärztin.", "Beruf steht ohne Artikel und ohne 'bin' — das 'sein' fehlt im Präsens."),
            E("Он студ+ент, а он+а студ+ентка.", "Er ist Student, sie Studentin.", "Ein schöner Gegensatz: -ент / -ентка."),
            E("Мо+я м+ама — врач.", "Meine Mama ist Ärztin.", "Мо+я (weiblich), врач bleibt männlich — und das ist völlig korrekt."),
        ],
        "gender": {
            "title": "Berufe: männliche Form, auch für Frauen",
            "text": "Врач, инженер, программист, директор gelten für Frauen genauso — und das Verb zeigt es: Она врач, она пришла. Wo eine weibliche Endung existiert (студентка, учительница), ist sie normal und höflich.",
        },
        "grammar": "Im Präsens braucht Russisch kein 'sein': Я врач = 'Ich Arzt'. Der Bindestrich (мама — врач) ersetzt das Verb in schriftlichen Aussagen.",
    },
    "d07_mama": {
        "register": "herzlich · familiär",
        "summary": "Familienwörter tragen im Russischen viel Wärme. Aus м+ама wird м+амочка, aus п+апа п+апочка — und im Ruf verkürzt man gern: Мам! Пап!",
        "variants": [
            V("М+амочка", "Mamilein / liebe Mama", "Zärtlich, mit viel Gefühl."),
            V("Мам+уля", "Mutti", "Liebevoll-verspielt."),
            V("П+апочка", "Papilein", "Zärtliche Form von папа."),
            V("Дорог+ая м+ама!", "Liebe Mama!", "Briefanrede: дорог+ая = weiblich, дорог+ой = männlich."),
            V("Люб+имая м+ама", "Geliebte Mama", "Liebevoll, besonders in Karten und Nachrichten."),
        ],
        "examples": [
            E("Мам, я теб+я любл+ю.", "Mama, ich hab dich lieb.", "Beim direkten Ansprechen verkürzt man: Мам! Тебя = Akkusativ von ты. Я тебя люблю ist der Satz für echte Liebe."),
            E("Пап, спас+ибо за всё!", "Papa, danke für alles!", "За всё = 'für alles'. Emotional und kurz."),
            E("Мо+я м+амочка, я скуч+аю.", "Meine liebe Mama, ich vermisse dich.", "Скуч+аю heißt 'ich vermisse' (und 'ich langweile mich')."),
        ],
        "gender": {
            "title": "Kosenamen: männlich und weiblich",
            "text": "Viele Zärtlichkeitsformen haben zwei Formen: Дорог+ой (m) / Дорог+ая (f) = liebe(r), Люб+имый (m) / Люб+имая (f) = geliebte(r). Ob du 'мой' (mein) oder 'моя' (meine) sagst, richtet sich nach dem Wort dahinter: мой друг, моя м+ама.",
            "forms": [
                V("Дорог+ой", "Lieber (zu einem Mann)", "Männliche Form."),
                V("Дорог+ая", "Liebe (zu einer Frau)", "Weibliche Form."),
                V("Люб+имый", "Geliebter", "Männlich, sehr zärtlich."),
                V("Люб+имая", "Geliebte", "Weiblich, sehr zärtlich."),
            ],
        },
        "grammar": None,
    },
    "d07_drug": {
        "register": "neutral",
        "summary": "Друг ist ein 'Freund' im Sinne von Kumpel, подруга die 'Freundin' im Sinne von Kumpelin. Für feste Beziehungen sagt man etwas anderes: п+арень (Freund) und д+евушка (Freundin).",
        "variants": [
            V("Подр+уга", "Freundin (Kumpelin)", "Eine weibliche Freundin — nicht der feste Freund."),
            V("Л+учший друг / Л+учшая подр+уга", "bester Freund / beste Freundin", "Л+учший (m) / л+учшая (f)."),
            V("П+арень", "(fester) Freund", "Umgangssprachlich: der Freund in einer Beziehung."),
            V("Д+евушка", "(feste) Freundin", "Auch: junge Frau. In einer Beziehung bedeutet es 'Freundin'."),
            V("Молод+ой челов+ек", "Freund (höflich, wörtlich 'junger Mensch')", "Höflich-neutral für 'mein Freund'."),
        ],
        "examples": [
            E("+Это мой друг, +Алекс.", "Das ist mein Freund Alex.", "Мой (m) zu друг. Kumpel, nicht Beziehung."),
            E("+Это мо+я д+евушка.", "Das ist meine Freundin (feste Freundin).", "Мо+я (f) zu д+евушка. So klärst du die Beziehung."),
            E("У теб+я есть д+евушка?", "Hast du eine Freundin?", "У теб+я есть = 'Hast du'. Bei Frauen: У теб+я есть п+арень?"),
        ],
        "gender": {
            "title": "друг / подруга ist nicht gleich Freund / Freundin",
            "text": "Друг und подруга sind Freunde im Sinne von Kumpel. Wer sich verliebt oder eine Beziehung hat, sagt п+арень (Freund) und д+евушка (Freundin). Das ist wichtig, damit du nichts Falsches andeutest. Passend dazu: мой / моя, мой друг, моя подруга.",
            "forms": [
                V("друг / подр+уга", "Freund / Freundin (platonisch)", "Beste Freunde."),
                V("п+арень / д+евушка", "Freund / Freundin (Beziehung)", "Partner."),
            ],
        },
        "grammar": None,
    },
    "d07_ty_zhenat": {
        "register": "neutral",
        "summary": "Verheiratet heißt für Männer жен+ат ('hat eine Frau genommen'), für Frauen з+амужем ('hinter einem Mann'). Das ist kein Zufall, sondern altes Sprachbild — und es gilt bis heute.",
        "variants": [
            V("Я жен+ат.", "Ich bin verheiratet (Mann).", "Nur für Männer."),
            V("Я з+амужем.", "Ich bin verheiratet (Frau).", "Nur für Frauen."),
            V("Я не жен+ат / Я не з+амужем.", "Ich bin nicht verheiratet.", "Verneint mit не."),
            V("Я х+олост.", "Ich bin ledig (Mann).", "Frau: Я не з+амужем."),
            V("Ты в отнош+ениях?", "Bist du in einer Beziehung?", "Neutral für beide Geschlechter — lockerer, direkter."),
        ],
        "examples": [
            E("Ты жен+ат? — Нет, я не жен+ат.", "Bist du verheiratet? — Nein, ich bin nicht verheiratet.", "Frage an einen Mann."),
            E("Ты з+амужем? — Да, я з+амужем.", "Bist du verheiratet? — Ja, ich bin verheiratet.", "Frage an eine Frau."),
            E("У теб+я есть п+арень?", "Hast du einen Freund?", "Frage an eine Frau; an einen Mann: У теб+я есть д+евушка?"),
        ],
        "gender": {
            "title": "Verheiratet: Mann und Frau sagen es unterschiedlich",
            "text": "Mann: жен+ат (Plural: жен+аты = 'wir sind verheiratet'). Frau: з+амужем. Wer einen Mann fragt, wählt жен+ат; wer eine Frau fragt, з+амужем. Unter Gleichgeschlechtlichen nutzt man lieber den neutralen Satz в отнош+ениях.",
            "forms": [
                V("Мы жен+аты.", "Wir sind verheiratet.", "Plural für ein Ehepaar."),
            ],
        },
        "grammar": None,
    },
    "d08_mne_nravitsya_muzyka": {
        "register": "neutral · persönlich",
        "summary": "Zwei Wege für 'mögen': Мне нр+авится (es gefällt mir) und Я любл+ю (ich liebe). Beide klingen unterschiedlich stark — und bei Menschen verändert sich die Bedeutung stark.",
        "variants": [
            V("Мне нр+авится…", "Mir gefällt…", "Mild, ehrlich. Das Ding ist das Subjekt: Мне нр+авится м+узыка."),
            V("Я любл+ю…", "Ich liebe…", "Stark; Я любл+ю + Akkusativ oder Infinitiv."),
            V("Я обож+аю…", "Ich vergöttere… / Ich liebe es total.", "Intensiv, begeistert."),
            V("Ты мне нр+авишься.", "Du gefällst mir.", "Verliebt-vorsichtig: ein Kompliment mit Herzklopfen."),
            V("Я теб+я любл+ю.", "Ich liebe dich.", "Nur für echte Liebe — der große Satz."),
            V("Я в теб+я влюблён / влюблен+а.", "Ich bin in dich verliebt (m / f).", "Männlich влюблён, weiblich влюблен+а."),
        ],
        "examples": [
            E("Мне нр+авится р+усский яз+ык.", "Mir gefällt die russische Sprache.", "Нравится richtet sich nach dem Gefallenden: Singular нравится, Plural нравятся."),
            E("Мне нр+авятся ф+ильмы.", "Mir gefallen Filme.", "Plural → нр+авятся. Das Grammatik-Rätsel: Das Ding ist das Subjekt."),
            E("Я любл+ю чит+ать.", "Ich lese gern.", "Я любл+ю + Infinitiv = gern tun."),
        ],
        "gender": {
            "title": "Verliebt: männlich und weiblich",
            "text": "Влюблён (Mann) und влюблен+а (Frau) sind Kurzformen: Я влюблён / Я влюблен+а. In ein Gegenüber verliebt sein: Я в теб+я влюблён. Bei Frauen springt die Betonung ans Ende (влюблен+а) — ein Klang, der verliebt wirkt.",
            "forms": [
                V("Я влюблён.", "Ich bin verliebt (Mann).", "Männlich, -ён."),
                V("Я влюблен+а.", "Ich bin verliebt (Frau).", "Weiblich, -а mit Betonung am Ende."),
            ],
        },
        "grammar": "Любить + Akkusativ (Я люблю музыку) — der Liebende ist Subjekt. Нравиться + Nominativ (Мне нравится музыка) — das Gefallende ist Subjekt, die Person steht im Dativ (мне).",
    },
    "d09_ya_hochu_kofe": {
        "register": "neutral → höflich",
        "summary": "Я хоч+у ('ich will') klingt direkt. Beim Bestellen und bei Wünschen wirkt Я хот+ел бы ('ich hätte gern') höflicher — mit Geschlechtsform: хот+ел (m), хот+ела (f).",
        "variants": [
            V("Я хот+ел бы к+офе.", "Ich hätte gern einen Kaffee (Mann).", "Höflich, eine Spur zurückhaltender."),
            V("Я хот+ела бы к+офе.", "Ich hätte gern einen Kaffee (Frau).", "Frau: хот+ела."),
            V("Мне к+офе, пож+алуйста.", "Für mich einen Kaffee, bitte.", "Wörtlich 'Mir Kaffee' — im Restaurant sehr natürlich und kurz."),
            V("Д+айте, пож+алуйста, к+офе.", "Geben Sie mir bitte einen Kaffee.", "Klarer Befehl mit Bitte — gut an der Kasse."),
            V("Принес+ите, пож+алуйста, к+офе.", "Bringen Sie bitte einen Kaffee.", "Beim Bedienen im Café."),
        ],
        "examples": [
            E("Я хоч+у чай.", "Ich möchte einen Tee.", "Я хоч+у + Akkusativ — direkt, im Alltag normal."),
            E("Я хот+ела бы чай.", "Ich hätte gern einen Tee.", "Mit бы wird es höflich-wünschend."),
            E("Мне чай, пож+алуйста.", "Mir einen Tee, bitte.", "Ohne Verb, im Dativ: typisch russisch."),
        ],
        "gender": {
            "title": "хот+ел / хот+ела",
            "text": "Я хот+ел бы (Mann) / Я хот+ела бы (Frau) — das бы bleibt gleich. Bei 'ich will' im Präsens ändert sich nichts: Я хоч+у gilt für alle.",
        },
        "grammar": "Хотеть ist unregelmäßig: я хоч+у, ты х+очешь, он х+очет, мы хот+им, вы хот+ите, он+и хот+ят. Merke: nur die Einzahl hat -ч-, die Mehrzahl -т-.",
    },
    "d09_mozhno_chay": {
        "register": "höflich",
        "summary": "М+ожно ('man kann / darf man') ist der einfachste Weg, um etwas zu bitten. Bei Getränken steht oft der Genitiv: М+ожно ч+аю? ('etwas Tee?').",
        "variants": [
            V("М+ожно ч+аю?", "Kann ich (etwas) Tee haben?", "Genitiv wie 'etwas davon' — sehr natürlich."),
            V("М+ожно мне чай?", "Darf ich einen Tee haben?", "Mit мне klingt es persönlicher."),
            V("Мне, пож+алуйста, чай.", "Mir bitte einen Tee.", "Im Restaurant der Klassiker."),
            V("М+ожно вод+ы?", "Kann ich Wasser haben?", "Auch hier Genitiv: вод+а → вод+ы."),
        ],
        "examples": [
            E("М+ожно чай, пож+алуйста?", "Einen Tee, bitte?", "Der Standardsatz."),
            E("М+ожно счёт, пож+алуйста?", "Die Rechnung, bitte?", "Wörtlich 'Kann man die Rechnung?'."),
        ],
        "grammar": "М+ожно + Nomen im Akkusativ: Можно чай? Bei Mengen oder 'etwas davon' nimmt man oft den Genitiv: Можно чаю? Можно воды?",
    },
    "d10_teplo": {
        "register": "neutral",
        "summary": "Wetter und Empfindungen haben dieselben Wörter: Сег+одня х+олодно ('Heute ist es kalt') und Мне х+олодно ('Mir ist kalt'). Beim Fühlen steht die Person im Dativ.",
        "variants": [
            V("Мне х+олодно.", "Mir ist kalt.", "Person im Dativ (мне) + Zustandswort."),
            V("Мне ж+арко.", "Mir ist heiß.", "Wie холодно: Zustand."),
            V("Прохл+адно.", "Es ist kühl.", "Mild-kalt, angenehm kühl."),
            V("Д+ушно.", "Es ist stickig / schwül.", "Wenn die Luft schwer ist."),
            V("Мор+оз!", "Frost! / Es ist eisig!", "Sehr kalt, oft bei Minusgraden."),
        ],
        "examples": [
            E("Сег+одня х+олодно.", "Heute ist es kalt.", "Ohne Person: allgemein, Wetter."),
            E("Мне х+олодно. Дай к+уртку.", "Mir ist kalt. Gib mir die Jacke.", "Мне + Zustand, danach der Befehl дай (gib!)."),
            E("Вам не ж+арко?", "Ist Ihnen nicht heiß?", "Sie-Form: Вам (Dativ von вы)."),
        ],
        "grammar": "Zustandswörter wie холодно, жарко, тепло, скучно, весело haben keine Geschlechtsform. Die Person steht im Dativ: мне, тебе, ему, ей, вам.",
    },
    "d11_davay_vstretimsya": {
        "register": "locker",
        "summary": "Дав+ай + 'wir'-Form des Verbs heißt 'Lass uns …'. Eine lockere, einladende Art, etwas vorzuschlagen: Дав+ай встр+етимся!",
        "variants": [
            V("Дав+ай пойдём в каф+е!", "Lass uns ins Café gehen!", "пойдём = 'wir gehen (los)'."),
            V("М+ожет б+ыть, встр+етимся?", "Vielleicht treffen wir uns?", "Vorsichtiger, höflicher Vorschlag."),
            V("Дав+айте встр+етимся!", "Lassen Sie uns treffen!", "Förmliche Version (Sie)."),
            V("Предлаг+аю встр+етиться.", "Ich schlage vor, uns zu treffen.", "Sehr förmlich."),
            V("Пойдём!", "Gehen wir!", "Kurz, eine Aufforderung."),
        ],
        "examples": [
            E("Дав+ай встр+етимся з+автра!", "Lass uns morgen treffen!", "Встр+етимся ist die 'wir'-Form (vollendet)."),
            E("Дав+ай пойдём гул+ять!", "Lass uns spazieren gehen!", "Гул+ять = spazieren, bummeln."),
            E("Дав+ай созвон+имся!", "Lass uns telefonieren!", "Созвон+имся = 'wir telefonieren' — typisch unter Freunden."),
        ],
        "grammar": "Давай + 'wir'-Form (vollendete Zukunft): встретимся, пойдём, созвонимся. Die Sie-/Mehrzahl-Form heißt Давайте.",
    },
    "d11_ne_mogu": {
        "register": "höflich",
        "summary": "Absagen bringen Respekt: Statt hartem Не мог+у reicht ein Zusatz — К сожал+ению ('leider') — und der Satz klingt freundlich. Wer beschäftigt ist, sagt з+анят (m) / занят+а (f).",
        "variants": [
            V("К сожал+ению, не мог+у.", "Leider kann ich nicht.", "Höflich und klar."),
            V("Я бы с удов+ольствием, но не мог+у.", "Ich würde gern, aber ich kann nicht.", "Sehr warm: zeigt, dass du willst."),
            V("Я з+анят.", "Ich habe keine Zeit (Mann).", "Frau: Я занят+а."),
            V("Я занят+а.", "Ich habe keine Zeit (Frau).", "Frau: занят+а — Betonung am Ende."),
            V("М+ожет, в друг+ой раз?", "Vielleicht ein anderes Mal?", "Freundlicher Ausweg."),
        ],
        "examples": [
            E("К сожал+ению, не мог+у. Я з+анят.", "Leider kann ich nicht. Ich habe keine Zeit.", "Kombination aus Höflichkeit und Grund."),
            E("Я бы с удов+ольствием, но я занят+а.", "Ich würde gern, aber ich habe keine Zeit.", "Frau spricht: занят+а."),
        ],
        "gender": {
            "title": "з+анят / занят+а",
            "text": "Beschäftigt: Mann з+анят, Frau занят+а (Betonung wandert), Mehrzahl занят+ы. Ähnlich: р+ад / р+ада, прав / права.",
        },
        "grammar": "Я бы + Verb in Vergangenheitsform = Konjunktiv: Я бы пошёл (Mann) / Я бы пошл+а (Frau) = 'ich würde gehen'.",
    },
    "d12_ya_dumayu": {
        "register": "neutral",
        "summary": "Meinungen gibt es in mehreren Tönen: von behutsam (Мне кажется) bis klar (Я считаю). Wer sie kennt, kann in Gesprächen differenziert antworten.",
        "variants": [
            V("Мне к+ажется, что…", "Mir scheint, dass…", "Behutsam, nimmt die Härte raus."),
            V("По-м+оему…", "Meiner Meinung nach…", "Standard, kurz."),
            V("Я счит+аю, что…", "Ich finde / ich halte…", "Klar und fest."),
            V("На мой взгляд…", "Aus meiner Sicht…", "Förmlich, gebildet."),
            V("Как по мне…", "Wenn du mich fragst…", "Locker, spielerisch."),
        ],
        "examples": [
            E("Мне к+ажется, что ты прав.", "Mir scheint, du hast recht.", "Что leitet den Nebensatz ein — davor steht immer ein Komma."),
            E("Я счит+аю, что +это хорош+о.", "Ich finde, das ist gut.", "Счит+аю = 'ich halte dafür'. Stärker als думаю."),
            E("По-м+оему, +это хор+ошая ид+ея.", "Ich finde, das ist eine gute Idee.", "По-м+оему ist ein fester Einstieg ohne Komma-Pflicht."),
        ],
        "grammar": "Vor что (dass) steht immer ein Komma: Я думаю, что… Мне к+ажется, что… Das Komma ist im Russischen Pflicht — und markiert für den Sprecher die Pause.",
    },
    "d12_pravda": {
        "register": "locker · emotional",
        "summary": "Reaktionen zeigen Emotion. Russen reagieren gern ausdrucksstark — mit Fragen, die Staunen oder Zweifel zeigen. Die Stärke steigt von Пр+авда? zu Ничег+о себ+е!",
        "variants": [
            V("Пр+авда?", "Wirklich?", "Neutral, neugierig."),
            V("Серьёзно?", "Im Ernst?", "Ein wenig ungläubig."),
            V("Д+а л+адно!", "Echt jetzt? / Nein, wirklich?", "Locker, überrascht-lachend."),
            V("Ничег+о себ+е!", "Das ist ja was! / Wow!", "Überrascht, beeindruckt."),
            V("Не м+ожет б+ыть!", "Das kann nicht sein!", "Starke Verwunderung."),
            V("Зд+орово!", "Toll!", "Begeistert."),
        ],
        "examples": [
            E("Я был в Росс+ии! — Серьёзно?", "Ich war in Russland! — Im Ernst?", "Ein Gesprächsmuster: Aussage, dann Reaktion."),
            E("Мне тр+идцать лет. — Д+а л+адно!", "Ich bin dreißig. — Echt?", "Д+а л+адно! drückt freundliche Skepsis aus."),
            E("Я в+ыиграл! — Ничег+о себ+е!", "Ich habe gewonnen! — Wow!", "Ничег+о себ+е zeigt Anerkennung."),
        ],
        "grammar": None,
    },
    "d12_ty_prav": {
        "register": "neutral",
        "summary": "Прав (m), прав+а (f), прав+ы (Mehrzahl) = 'recht haben'. Die Kurzform richtet sich nach der angesprochenen Person: Ты прав (zu einem Mann) / Ты прав+а (zu einer Frau).",
        "variants": [
            V("Ты прав+а.", "Du hast recht (zu einer Frau).", "Weiblich: Betonung am Ende."),
            V("Вы прав+ы.", "Sie haben recht.", "Sie-Form: Mehrzahl -ы."),
            V("Я согл+асен.", "Ich stimme zu (Mann).", "Frau: согл+асна."),
            V("+Именно!", "Genau!", "Betont und zustimmend."),
            V("Т+очно!", "Stimmt genau!", "Locker."),
        ],
        "examples": [
            E("Ты прав!", "Du hast recht! (zu einem Mann)", "Männlich — einfach прав."),
            E("Ты прав+а!", "Du hast recht! (zu einer Frau)", "Weiblich — Betonung am Ende."),
            E("Мы прав+ы.", "Wir haben recht.", "Plural -ы."),
        ],
        "gender": {
            "title": "Kurzformen: прав / права / правы",
            "text": "Kurzadjektive passen sich dem Subjekt an: прав (m), права (f), правы (pl). Auch: рад / рада / рады (froh), занят / занята / заняты (beschäftigt), согласен / согласна / согласны (einverstanden).",
        },
        "grammar": None,
    },
    "d13_ya_rad": {
        "register": "neutral · herzlich",
        "summary": "Р+ад (m) / р+ада (f) = 'froh'. Wer sich freut, sagt es klar — und mit dem Gegenüber: Я р+ад теб+я в+идеть! ('Ich freue mich, dich zu sehen!')",
        "variants": [
            V("Я так р+ад / р+ада за теб+я!", "Ich freue mich so für dich!", "Warm — Mitfreude."),
            V("Я сч+астлив / сч+астлива.", "Ich bin glücklich (m / f).", "Tiefer als рад."),
            V("Я в вост+орге!", "Ich bin begeistert!", "Sehr stark, laut."),
            V("Как р+адостно!", "Wie schön / wie erfreulich!", "Ausruf des Gefühls."),
        ],
        "examples": [
            E("Я р+ад теб+я в+идеть!", "Ich freue mich, dich zu sehen! (Mann)", "Vor einem Infinitiv steht рад — kurz und warm."),
            E("Я р+ада теб+я в+идеть!", "Ich freue mich, dich zu sehen! (Frau)", "Frau: р+ада."),
            E("Я так р+ад за теб+я!", "Ich freue mich so für dich!", "За теб+я = 'für dich'."),
        ],
        "gender": {
            "title": "рад / рада / рады",
            "text": "Ein Mann sagt Я р+ад, eine Frau Я р+ада, eine Gruppe Мы р+ады. Genauso сч+астлив / сч+астлива / сч+астливы.",
        },
        "grammar": None,
    },
    "d13_molodets": {
        "register": "locker · Lob",
        "summary": "Lob kann im Russischen sehr herzlich klingen. Молод+ец! gilt für Männer und Frauen — doch es gibt auch geschlechtsspezifische Komplimente: крас+авица (Frau), крас+авчик (Mann).",
        "variants": [
            V("Молод+ец!", "Gut gemacht! / Bravo!", "Gilt für Männer und Frauen."),
            V("+Умница!", "Du Kluge! / Gut gemacht! (Frau, Kind)", "Eher für Frauen und Kinder."),
            V("Крас+авица!", "Du Schöne! / Toll gemacht! (Frau)", "Komplimentiert Frauen — 'bravo, du Schöne'."),
            V("Крас+авчик!", "Du Schöner! / Gut gemacht! (Mann)", "Lockeres Lob unter Männern."),
            V("Крас+авец!", "Gutaussehender Mann", "Beschreibt, wie ein Mann aussieht."),
            V("Крас+ивая / Крас+ивый", "schön (f / m)", "Крас+ивая — weiblich, крас+ивый — männlich."),
        ],
        "examples": [
            E("Молод+ец! Ты сд+елал +это!", "Bravo! Du hast es geschafft!", "Сд+елал (m) / сд+елала (f)."),
            E("Крас+авица! Как крас+иво!", "Du Schöne! Wie schön!", "Ein Doppel-Kompliment — Wirkung und Ausruf."),
            E("Как+ой крас+авчик!", "Was für ein Schöner!", "Какой + männlich. Bei Frauen: Как+ая крас+авица!"),
        ],
        "gender": {
            "title": "Komplimente: für Frauen und für Männer",
            "text": "Крас+авица (f) ↔ крас+авчик (m, locker) / крас+авец (m, Aussehen). Das Adjektiv: крас+ивая (f), крас+ивый (m), крас+ивое (n). Молод+ец passt zu beiden. Und Vorsicht: Красотка (f) klingt keck und manchmal frech — nicht überall passend.",
            "forms": [
                V("Крас+авица!", "Du Schöne! (zu einer Frau)", "Weiblich."),
                V("Крас+авчик!", "Du Schöner! (zu einem Mann)", "Männlich, locker."),
                V("Молод+ец!", "Bravo! (für alle)", "Neutral."),
            ],
        },
        "grammar": None,
    },
    "d13_izvini": {
        "register": "persönlich · emotional",
        "summary": "Entschuldigungen haben eine Skala: von Извин+и (leicht) bis zu einem Satz, der Verantwortung übernimmt. Wer sich entschuldigt, bleibt nahbar — besonders in Beziehungen.",
        "variants": [
            V("Извин+и!", "Entschuldige!", "Kleine Fehler, locker."),
            V("Прост+и мен+я!", "Verzeih mir!", "Persönlich, echte Schuld."),
            V("+Это мо+я вин+а.", "Das ist meine Schuld.", "Verantwortung übernehmen."),
            V("Я не хот+ел / хот+ела.", "Ich wollte das nicht (m / f).", "Erklärt, dass es nicht beabsichtigt war."),
            V("Мне +очень жаль.", "Es tut mir sehr leid.", "Aufrichtiges Bedauern."),
        ],
        "examples": [
            E("Прост+и мен+я, пож+алуйста.", "Verzeih mir bitte.", "Меня = Akkusativ von я (mich)."),
            E("Я не хот+ел теб+я об+идеть.", "Ich wollte dich nicht kränken (Mann).", "Обид+еть = 'kränken'. Frau: хот+ела."),
            E("+Это мо+я вин+а. Мне +очень жаль.", "Das ist meine Schuld. Es tut mir sehr leid.", "Zwei Sätze, die Ernst zeigen."),
        ],
        "gender": {
            "title": "хот+ел / хот+ела",
            "text": "Ich wollte nicht: Mann Я не хот+ел, Frau Я не хот+ела. Im Streit ist das Geschlecht der Form wichtig: Sie zeigt, wer spricht.",
        },
        "grammar": None,
    },
    "d13_mne_skuchno": {
        "register": "persönlich · Gefühle",
        "summary": "Gefühle werden im Russischen oft mit мне + Zustandswort ausgedrückt: Мне ск+учно, Мне гр+устно. Die Person steht im Dativ, das Gefühl ist ein Wort ohne Geschlechtsform.",
        "variants": [
            V("Мне гр+устно.", "Mir ist traurig / Ich bin traurig.", "Leise Traurigkeit."),
            V("Мне стр+ашно.", "Ich habe Angst.", "Furcht, auch 'gruselig'."),
            V("Мне об+идно.", "Ich bin gekränkt.", "Verletzt, weil jemand unfair war."),
            V("Мне ст+ыдно.", "Ich schäme mich.", "Peinlich, beschämt."),
            V("Мне один+око.", "Ich fühle mich einsam.", "Tiefe Einsamkeit."),
            V("Мне б+ольно.", "Es tut mir weh.", "Körperlich und seelisch."),
        ],
        "examples": [
            E("Мне гр+устно. Мне теб+я не хват+ает.", "Ich bin traurig. Du fehlst mir.", "Мне тебя не хватает = 'Du fehlst mir'."),
            E("Мне стр+ашно. Пож+алуйста, ост+анься!", "Ich habe Angst. Bleib bitte!", "Ост+анься = 'bleib!' (Befehl, du-Form)."),
            E("Мне об+идно, что ты не позвон+ил.", "Es kränkt mich, dass du nicht angerufen hast.", "Что leitet den Grund ein."),
        ],
        "grammar": "Мне + Zustandswort (скучно, грустно, страшно, обидно, стыдно, одиноко, больно). Für andere Personen: Ему / Ей / Тебе / Вам скучно.",
    },
    "d13_ne_perezhivay": {
        "register": "tröstend · nah",
        "summary": "Trost braucht Wärme. Не пережив+ай! ('Mach dir keine Sorgen!') ist der Anfang; mit weiteren Sätzen zeigst du: Ich bin da.",
        "variants": [
            V("Не волн+уйся!", "Reg dich nicht auf / Mach dir keine Sorgen!", "Beruhigend."),
            V("Успок+ойся.", "Beruhige dich.", "Ruhig, ein wenig streng."),
            V("Всё б+удет хорош+о.", "Alles wird gut.", "Tröstend und hoffnungsvoll."),
            V("Я с тоб+ой.", "Ich bin bei dir.", "Zeigt Nähe und Unterstützung."),
            V("Я теб+я поним+аю.", "Ich verstehe dich.", "Empathie."),
        ],
        "examples": [
            E("Не пережив+ай! Всё б+удет хорош+о.", "Mach dir keine Sorgen! Alles wird gut.", "Befehl mit не + Imperativ."),
            E("Я с тоб+ой. Я теб+я поним+аю.", "Ich bin bei dir. Ich verstehe dich.", "Тоб+ой = Instrumental von ты ('mit dir')."),
            E("Не волн+уйся, я р+ядом.", "Reg dich nicht auf, ich bin da.", "Ря+дом = 'nebenan, in der Nähe'."),
        ],
        "grammar": "Verneinter Befehl: Не + unvollendetes Verb im Imperativ: Не переживай! Не волнуйся! Reflexive Verben enden im Befehl auf -йся/-ись: Успокойся! Не волнуйся!",
    },
    "d14_ya_budu_skuchat": {
        "register": "emotional · nah",
        "summary": "Скуч+ать по + Dativ heißt 'vermissen'. 'Ich werde dich vermissen': Я б+уду скуч+ать по теб+е. Wörtlich: 'Ich werde nach dir langweilen/sehnen.'",
        "variants": [
            V("Я скуч+аю по теб+е.", "Ich vermisse dich.", "Jetzt, in diesem Moment."),
            V("Я уж+е скуч+аю.", "Ich vermisse dich schon.", "Zart und verliebt."),
            V("Мне теб+я не хват+ает.", "Du fehlst mir.", "Wörtlich 'Mir fehlt dich'. Sehr nah."),
            V("Я б+уду скуч+ать по теб+е.", "Ich werde dich vermissen.", "Beim Abschied."),
            V("Ск+оро ув+идимся.", "Wir sehen uns bald.", "Zuversichtlicher Abschluss."),
        ],
        "examples": [
            E("Я б+уду скуч+ать по теб+е.", "Ich werde dich vermissen.", "Б+уду + Infinitiv = Zukunft (unvollendet)."),
            E("Я уж+е скуч+аю по теб+е.", "Ich vermisse dich schon.", "Уж+е = 'schon'."),
            E("Мне теб+я не хват+ает.", "Du fehlst mir.", "Теб+я = Genitiv von ты; хват+ает = 'es reicht / es fehlt'."),
        ],
        "grammar": "Zukunft von unvollendeten Verben = буду + Infinitiv: Я буду скучать, ты будешь работать. Скучать по + Dativ: по тебе, по маме, по дому.",
    },
    "d14_mne_pora": {
        "register": "locker",
        "summary": "Мне пор+а ('Mir ist es Zeit') ist der freundlichste Aufbruchssatz. Er sagt: Ich muss gehen — aber nett.",
        "variants": [
            V("Мне пор+а идт+и.", "Ich muss gehen.", "Mit Infinitiv: 'Mir ist Zeit zu gehen'."),
            V("Мне н+адо идт+и.", "Ich muss gehen.", "Н+адо ist eine Pflicht, etwas strenger."),
            V("Я пойд+у.", "Ich gehe jetzt.", "Kurz und freundlich."),
            V("Я побег+у.", "Ich muss los / flitze los.", "Eilig, locker."),
            V("Мне пор+а, до свид+ания!", "Ich muss los, auf Wiedersehen!", "Mit Gruß abgeschlossen."),
        ],
        "examples": [
            E("Мне пор+а. До встр+ечи!", "Ich muss los. Bis dann!", "Aufbruch + Gruß."),
            E("Мне н+адо идт+и. Пок+а!", "Ich muss gehen. Tschüss!", "Н+адо + Infinitiv."),
        ],
        "grammar": "Мне пора / надо / нужно + Infinitiv. Alle drei drücken Notwendigkeit aus, nur der Ton ist unterschiedlich: пора (es ist Zeit), надо (ich muss), нужно (es ist nötig).",
    },
    "d14_spokoynoy_nochi": {
        "register": "herzlich",
        "summary": "Спок+ойной н+очи! ist im Genitiv — ein Wunsch ohne Verb: '(Ich wünsche dir) eine ruhige Nacht'. So bildest du viele Wünsche: Хор+ошего дня! Сл+адких снов!",
        "variants": [
            V("Сл+адких снов!", "Süße Träume!", "Zärtlich, vertraut."),
            V("При+ятных снов!", "Angenehme Träume!", "Freundlich-neutral."),
            V("Д+оброй н+очи!", "Gute Nacht!", "Auch als Begrüßung in der Nacht."),
            V("Хор+ошего дня!", "Schönen Tag!", "Zum Abschied tagsüber."),
        ],
        "examples": [
            E("Спок+ойной н+очи! Сл+адких снов!", "Gute Nacht! Süße Träume!", "Zwei Wünsche im Genitiv."),
            E("Хор+ошего дня! До з+автра!", "Schönen Tag! Bis morgen!", "Wunsch + Abschied."),
        ],
        "grammar": "Wünsche stehen im Genitiv, weil darunter gedacht ist: '(Ich wünsche dir) ...': спокойной ночи, хорошего дня, всего хорошего, удачи, счастья.",
    },
}
