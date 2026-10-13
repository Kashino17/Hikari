"""Grammatik-Muster 9–20: Widerspruch, Fragen, Bitten, Träume, Vergleiche, Hilfe, Haben, Erinnerung, Wenn/Falls, Wünsche an andere, Werte."""
from patterns1 import C, L, O

PATTERNS = [
    {
        "id": "p09_ne",
        "week": 2,
        "title": "Nein, das stimmt nicht — Widerspruch und Verneinung",
        "formula": "Я не + Verb · Нет, +это не …",
        "emotion": "Widerspruch",
        "hook": "Tiefe Gespräche brauchen Widerspruch. Wer freundlich Nein sagen kann, muss nicht schweigen — und nicht streiten.",
        "scene": {
            "title": "Das stimmt nicht!",
            "setting": "Ein Missverständnis wird laut. Du fühlst dich ungerecht behandelt, willst aber nicht explodieren.",
            "lines": [
                L("P", "Ты никогд+а не сл+ушаешь!", "Du hörst nie zu!", "vorwurfsvoll"),
                L("U", "Нет, +это непр+авда! Я сл+ушаю.", "Nein, das stimmt nicht! Ich höre zu.", "aufgebracht, aber ruhig"),
                L("P", "Но ты не отвеч+аешь.", "Aber du antwortest nicht.", "müde, leiser"),
                L("U", "Я не хоч+у сп+орить. Я хоч+у пон+ять теб+я.", "Ich will nicht streiten. Ich will dich verstehen.", "ruhiger werdend, weich"),
                L("P", "Хорош+о. Дав+ай говор+ить спок+ойно.", "Gut. Lass uns ruhig reden.", "erleichtert"),
            ],
        },
        "core": [
            C("Я не зн+аю.", "Ich weiß nicht.", "Не steht direkt vor dem Verb, das du verneinst."),
            C("Я ничег+о не зн+аю.", "Ich weiß gar nichts.", "Doppelte Verneinung ist Pflicht: ничего + не."),
            C("Нет, я не хоч+у.", "Nein, ich will nicht.", "Нет antwortet, не verneint das Verb — beides darf zusammenstehen."),
        ],
        "explain": "Не verneint das Wort danach, meist ein Verb: Я не знаю. Нет ist die Antwort 'nein'. Im Russischen verstärken sich Verneinungen statt sich aufzuheben: Я никогд+а не … (ich … nie), Я ничег+о не … (ich … nichts), Я никого не … (ich … niemanden). Das ist nicht falsch, sondern Regel.",
        "template": {"ru": "Я {0}.", "de": "Ich {0}."},
        "slots": [
            {"label": "Was verneinst du?", "options": [
                O("не зн+аю", "weiß nicht"), O("не хоч+у сп+орить", "will nicht streiten"),
                O("не поним+аю", "verstehe nicht"), O("не любл+ю ждать", "warte nicht gern"),
                O("не мог+у прийт+и", "kann nicht kommen"),
            ]},
        ],
        "free": {
            "prompt": "Jemand wirft dir vor: „Du hörst nie zu!“ — widersprich ruhig und freundlich.",
            "model": C("Нет, +это непр+авда! Я сл+ушаю.", "Nein, das stimmt nicht! Ich höre zu."),
        },
    },
    {
        "id": "p10_voprosy",
        "week": 2,
        "title": "Erzähl mir von dir — Fragen stellen",
        "formula": "Где? К+огда? Как? Почем+у? + ты + Verb",
        "emotion": "Neugier",
        "hook": "Wer gute Fragen stellt, schenkt dem anderen Aufmerksamkeit. Das ist der schnellste Weg zu echten Gesprächen.",
        "scene": {
            "title": "Ich will mehr über dich wissen",
            "setting": "Ihr sitzt am Fluss. Alle Smalltalk-Fragen sind durch, jetzt wird es persönlich.",
            "lines": [
                L("U", "Расскаж+и мне о себ+е. Где ты в+ырос?", "Erzähl mir von dir. Wo bist du aufgewachsen?", "neugierig, nah"),
                L("P", "В Москв+е. Я любл+ю м+ой г+ород.", "In Moskau. Ich liebe meine Stadt.", "stolz, warm"),
                L("U", "Что ты д+елаешь в своб+одное вр+емя?", "Was machst du in deiner Freizeit?", "interessiert"),
                L("P", "Я гул+яю и чит+аю. А ты?", "Ich gehe spazieren und lese. Und du?", "locker, zurückfragend"),
                L("U", "Расскаж+и ещё!", "Erzähl mehr!", "gespannt"),
            ],
        },
        "core": [
            C("Где ты живёшь?", "Wo wohnst du?", "Fragewort + ты + Verb: die Wortstellung ändert sich nicht."),
            C("Почем+у ты +учишь р+усский?", "Warum lernst du Russisch?", "Почему fragt nach dem Grund."),
            C("Расскаж+и мне о себ+е.", "Erzähl mir von dir.", "Расскажи ist der Du-Befehl — freundlich-direkt."),
        ],
        "explain": "Russische Fragen ändern die Satzstellung kaum: Fragewort + ты + Verb. Bei Ja/Nein-Fragen gibt es kein Hilfsverb — nur die Melodie steigt auf dem Wort, nach dem du fragst: Ты живёшь в Берл+ине? Die wichtigsten Fragewörter: Кто? Что? Где? К+огда? Как? Почем+у? Ск+олько?",
        "template": {"ru": "{0}", "de": "{0}"},
        "slots": [
            {"label": "Welche Frage?", "options": [
                O("Где ты живёшь?", "Wo wohnst du?"), O("Когд+а ты раб+отаешь?", "Wann arbeitest du?"),
                O("Как ты пров+одишь вр+емя?", "Wie verbringst du deine Zeit?"),
                O("Почем+у ты +учишь р+усский?", "Warum lernst du Russisch?"),
                O("Что ты л+юбишь д+елать?", "Was machst du gern?"),
                O("Отк+уда ты?", "Woher kommst du?"),
            ]},
        ],
        "free": {
            "prompt": "Stelle jemandem zwei Fragen, um ihn besser kennenzulernen.",
            "model": C("Где ты живёшь? Что ты д+елаешь в своб+одное вр+емя?", "Wo wohnst du? Was machst du in deiner Freizeit?"),
        },
    },
    {
        "id": "p11_povelitelnoe",
        "week": 2,
        "title": "Bleib bei mir! — Bitten und Drängen",
        "formula": "Befehl: Stamm + -и (du) · -ите (Sie)",
        "emotion": "Bitten und Drängen",
        "hook": "Bitten sind nicht Befehle — mit der richtigen Form klingen sie nach Nähe, nicht nach Druck.",
        "scene": {
            "title": "Geh noch nicht!",
            "setting": "Bahnhof am Abend. Der Zug wartet, aber keiner von euch will wirklich Abschied nehmen.",
            "lines": [
                L("U", "Не уход+и! Ост+анься ещё немн+ого.", "Geh nicht! Bleib noch ein bisschen.", "bittend, leise"),
                L("P", "Я не мог+у. М+ой п+оезд ск+оро.", "Ich kann nicht. Mein Zug fährt gleich.", "traurig, sanft"),
                L("U", "Тогд+а позвон+и мне, когд+а придёшь.", "Dann ruf mich an, wenn du ankommst.", "weich"),
                L("P", "Хорош+о. Я напиш+у теб+е.", "Gut. Ich schreibe dir.", "tröstend"),
                L("U", "Берег+и себ+я!", "Pass auf dich auf!", "mit Wärme"),
            ],
        },
        "core": [
            C("Позвон+и мне!", "Ruf mich an!", "Du-Befehl: Stamm + и (betont)."),
            C("Подожд+и мен+я!", "Warte auf mich!", "Подожди — Befehl von ждать mit Vorsilbe."),
            C("Не волн+уйся!", "Reg dich nicht auf!", "Verneinter Befehl: не + unvollendetes Verb."),
        ],
        "explain": "Du-Befehl: Stamm der Präsensform + -и (betont: позвон+и) oder -й nach Vokal (дав+ай). An mehrere Personen und beim Siezen kommt -те dazu: позвон+ите. Verneint wird mit dem unvollendeten Verb: Не звон+и! Mit пож+алуйста klingt jeder Befehl freundlich. Bei reflexiven Verben wird -ся zu -сь nach Vokal: Ост+анься.",
        "template": {"ru": "{0}!", "de": "{0}!"},
        "slots": [
            {"label": "Welche Bitte?", "options": [
                O("Позвон+и мне", "Ruf mich an"), O("Напиш+и мне", "Schreib mir"),
                O("Подожд+и мен+я", "Warte auf mich"), O("Скаж+и мне пр+авду", "Sag mir die Wahrheit"),
                O("Приход+и з+автра", "Komm morgen"), O("Не волн+уйся", "Reg dich nicht auf"),
            ]},
        ],
        "free": {
            "prompt": "Bitte jemanden, dich anzurufen — freundlich, aber deutlich.",
            "model": C("Позвон+и мне, пож+алуйста!", "Ruf mich bitte an!"),
        },
    },
    {
        "id": "p12_by",
        "week": 3,
        "title": "Ich würde gern … — Träume und Wünsche",
        "formula": "Я бы + Vergangenheitsform",
        "emotion": "Träumen",
        "hook": "Träume machen Gespräche weit. Mit бы sprichst du darüber, was du tun würdest — unabhängig davon, ob es möglich ist.",
        "scene": {
            "title": "Wovon träumst du?",
            "setting": "Spätabends, ihr lasst die Gedanken treiben. Alles scheint möglich.",
            "lines": [
                L("P", "О чём ты мечт+аешь?", "Wovon träumst du?", "weich, neugierig"),
                L("U", "Я бы хот+ел жить у м+оря.", "Ich würde gern am Meer leben.", "verträumt", ruF="Я бы хот+ела жить у м+оря."),
                L("P", "А что ты б+удешь д+елать там?", "Und was wirst du dort machen?", "lächelnd"),
                L("U", "Я бы чит+ал кн+иги и пил чай.", "Ich würde Bücher lesen und Tee trinken.", "mit ruhiger Stimme", ruF="Я бы чит+ала кн+иги и пил+а чай."),
                L("P", "Звуч+ит прекр+асно!", "Das klingt wunderbar!", "begeistert"),
            ],
        },
        "core": [
            C("Я бы хот+ел отдохн+уть.", "Ich würde mich gern ausruhen.", "Frau: Я бы хот+ела отдохн+уть."),
            C("Я бы с удов+ольствием!", "Ich würde sehr gern!", "Mit удовольствием = mit Vergnügen — eine warme Zusage."),
            C("Я бы пошёл с тоб+ой.", "Ich würde mit dir gehen.", "Frau: Я бы пошл+а с тоб+ой."),
        ],
        "explain": "бы + Vergangenheitsform = Konjunktiv ('würde'): Я бы пошёл (m) / Я бы пошл+а (f) = ich würde gehen. Die Form richtet sich nach deinem Geschlecht. Mit +если бы + Vergangenheit bildest du Bedingungen, die nicht real sind: +Если бы я мог, я бы приех+ал.",
        "template": {"ru": "Я бы {0}.", "de": "Ich würde {0}."},
        "slots": [
            {"label": "Was würdest du tun?", "options": [
                O("хот+ел жить у м+оря", "gern am Meer leben", ruF="хот+ела жить у м+оря"),
                O("чит+ал кн+иги", "Bücher lesen", ruF="чит+ала кн+иги"),
                O("пошёл с тоб+ой", "mit dir gehen", ruF="пошл+а с тоб+ой"),
                O("отдохн+ул", "mich ausruhen", ruF="отдохн+ула"),
            ]},
        ],
        "free": {
            "prompt": "Wovon träumst du? Sag, was du tun würdest, wenn du alle Zeit der Welt hättest.",
            "model": C("Я бы хот+ел путеш+ествовать.", "Ich würde gern reisen."),
        },
    },
    {
        "id": "p13_sravnenie",
        "week": 3,
        "title": "Lieber als … — Vergleichen und Vorlieben",
        "formula": "Мне б+ольше нр+авится …, чем …",
        "emotion": "Spielerisch",
        "hook": "Vergleiche machen Vorlieben sichtbar — und sind die Grundlage für neckische Gespräche zwischen Menschen, die sich mögen.",
        "scene": {
            "title": "Wen magst du lieber?",
            "setting": "Ihr albert herum. Hinter jeder Frage steckt ein Augenzwinkern — und ein bisschen Ernst.",
            "lines": [
                L("P", "Скаж+и пр+авду: ты л+юбишь мен+я б+ольше, чем к+офе?", "Sag die Wahrheit: Liebst du mich mehr als Kaffee?", "spielerisch, mit Augenzwinkern"),
                L("U", "Кон+ечно! Я любл+ю теб+я б+ольше всег+о.", "Natürlich! Ich liebe dich mehr als alles.", "lachend, aber ehrlich"),
                L("P", "А что л+учше: ч+ай +или к+офе?", "Und was ist besser: Tee oder Kaffee?", "neckend"),
                L("U", "Мне б+ольше нр+авится ч+ай.", "Mir gefällt Tee besser.", "gelassen"),
                L("P", "Пред+атель!", "Verräter!", "gespielt empört, lachend"),
            ],
        },
        "core": [
            C("+Это л+учше, чем вчер+а.", "Das ist besser als gestern.", "лучше = besser, чем = als."),
            C("Мне б+ольше нр+авится ч+ай, чем к+офе.", "Mir gefällt Tee mehr als Kaffee.", "больше + нравится: mehr mögen, lieber mögen."),
            C("Ты мой л+учший друг.", "Du bist mein bester Freund.", "лучший = der beste."),
        ],
        "explain": "Komparative: б+ольше (mehr), л+учше (besser), х+уже (schlechter), м+еньше (weniger). Der Vergleichspartner steht nach чем (als): Ч+ай л+учше, чем к+офе. Мне б+ольше нр+авится … heißt 'ich mag lieber'. Der Superlativ лучший (der beste) ist ein Adjektiv und passt sich dem Nomen an: л+учший друг, л+учшая подр+уга.",
        "template": {"ru": "Мне б+ольше нр+авится {0}.", "de": "Mir gefällt {0}."},
        "slots": [
            {"label": "Was gefällt dir lieber?", "options": [
                O("ч+ай, чем к+офе", "Tee mehr als Kaffee"), O("л+ето, чем зим+а", "der Sommer mehr als der Winter"),
                O("м+оре, чем г+ород", "das Meer mehr als die Stadt"), O("р+усский, чем англ+ийский", "Russisch mehr als Englisch"),
            ]},
        ],
        "free": {
            "prompt": "Sag, was dir besser gefällt: Sommer oder Winter, Tee oder Kaffee.",
            "model": C("Мне б+ольше нр+авится л+ето, чем зим+а.", "Mir gefällt der Sommer mehr als der Winter."),
        },
    },
    {
        "id": "p14_nuzhen",
        "week": 3,
        "title": "Ich brauche … — Hilfe und Nähe zulassen",
        "formula": "Мне н+ужен / нужн+а / н+ужно / нужн+ы + Ding",
        "emotion": "Verletzlichkeit",
        "hook": "Zu sagen, was man braucht, ist mutig. Dieses Muster hilft dir dabei — und zeigt nebenbei, wie Russisch Geschlecht und Zahl anpasst.",
        "scene": {
            "title": "Ich brauche dich",
            "setting": "Ein schlechter Moment. Du bist stolz, aber heute geht es nicht allein.",
            "lines": [
                L("U", "Мне нужн+а тво+я п+омощь.", "Ich brauche deine Hilfe.", "leise, ehrlich"),
                L("P", "Что случ+илось?", "Was ist passiert?", "besorgt"),
                L("U", "Я не зн+аю, что д+елать. Мне стр+ашно.", "Ich weiß nicht, was ich tun soll. Ich habe Angst.", "die Stimme bricht fast"),
                L("P", "Я здесь. Скаж+и мне всё.", "Ich bin hier. Sag mir alles.", "ruhig, nah"),
                L("U", "Ты мне +очень нужн+а.", "Ich brauche dich sehr.", "warm, verletzlich"),
            ],
        },
        "core": [
            C("Мне н+ужен врач.", "Ich brauche einen Arzt.", "врач ist männlich → нужен."),
            C("Мне нужн+а п+омощь.", "Ich brauche Hilfe.", "помощь ist weiblich → нужна."),
            C("Мне н+ужно вр+емя.", "Ich brauche Zeit.", "время ist sächlich → нужно."),
        ],
        "explain": "Мне нужен / нужна / нужно / нужны: Das Gebrauchte ist das Subjekt und bestimmt die Form — männlich нужен, weiblich нужна (Betonung wandert!), sächlich нужно, Mehrzahl нужны. Die Person steht im Dativ (мне). Auch Menschen: Ты мне нужен (zu einem Mann) / Ты мне нужна (zu einer Frau) = 'ich brauche dich'.",
        "template": {"ru": "Мне {0}.", "de": "Ich {0}."},
        "slots": [
            {"label": "Was brauchst du?", "options": [
                O("н+ужен врач", "brauche einen Arzt"), O("нужн+а п+омощь", "brauche Hilfe"),
                O("н+ужно вр+емя", "brauche Zeit"), O("нужн+ы друзь+я", "brauche Freunde"),
            ]},
        ],
        "free": {
            "prompt": "Bitte jemanden um Hilfe — und sag klar, was du brauchst.",
            "model": C("Мне нужн+а тво+я п+омощь.", "Ich brauche deine Hilfe."),
        },
    },
    {
        "id": "p15_u_menya",
        "week": 3,
        "title": "Ich habe … / ich habe keine … — Besitz und Mangel",
        "formula": "У мен+я есть + Nominativ · У мен+я нет + Genitiv",
        "emotion": "Einsamkeit und Stolz",
        "hook": "Was jemand hat — und was ihm fehlt — erzählt viel über sein Leben. Mit diesem Muster teilst du beides.",
        "scene": {
            "title": "Neu in der Stadt",
            "setting": "Du bist erst vor Kurzem hergezogen. Anja fragt behutsam, wie du dich eingelebt hast.",
            "lines": [
                L("P", "У теб+я есть друзь+я здесь?", "Hast du hier Freunde?", "freundlich, behutsam"),
                L("U", "Пок+а нет. У мен+я нет вр+емени, и я не зн+аю люд+ей.", "Noch nicht. Ich habe keine Zeit, und ich kenne keine Leute.", "bedrückt"),
                L("P", "Ничег+о! У мен+я есть др+уг. Он теб+е пом+ожет.", "Macht nichts! Ich habe einen Freund. Er hilft dir.", "aufmunternd"),
                L("U", "Пр+авда? У мен+я нет сл+ов. Спас+ибо!", "Wirklich? Mir fehlen die Worte. Danke!", "gerührt"),
            ],
        },
        "core": [
            C("У мен+я есть брат.", "Ich habe einen Bruder.", "У меня есть + Nominativ = haben."),
            C("У мен+я нет вр+емени.", "Ich habe keine Zeit.", "Nach нет steht der Genitiv: времени."),
            C("У мен+я нет д+енег.", "Ich habe kein Geld.", "Genitiv Plural: денег."),
        ],
        "explain": "Haben wird ausgedrückt als 'bei mir ist': У мен+я есть + Nominativ. Fehlt etwas, folgt нет + Genitiv: У мен+я нет вр+емени (nicht Nominativ!). In der Vergangenheit: У мен+я был / был+а / б+ыло + Nominativ, verneint: не б+ыло + Genitiv.",
        "template": {"ru": "У мен+я {0}.", "de": "Ich habe {0}."},
        "slots": [
            {"label": "Was hast du — oder nicht?", "options": [
                O("есть брат", "einen Bruder"), O("есть сестр+а", "eine Schwester"),
                O("нет вр+емени", "keine Zeit"), O("нет д+енег", "kein Geld"), O("есть план", "einen Plan"),
            ]},
        ],
        "free": {
            "prompt": "Erzähle, was du hast — und was dir fehlt.",
            "model": C("У мен+я есть мечт+а, но нет вр+емени.", "Ich habe einen Traum, aber keine Zeit."),
        },
    },
    {
        "id": "p16_pomnyu",
        "week": 3,
        "title": "Ich erinnere mich — Nostalgie und Erinnerungen",
        "formula": "Я п+омню + Akkusativ · Я п+омню, к+ак …",
        "emotion": "Nostalgie",
        "hook": "Erinnerungen verbinden Menschen. Wer sagt, woran er sich erinnert, zeigt, was ihm wichtig war.",
        "scene": {
            "title": "Ein altes Foto",
            "setting": "Beim Aufräumen fällt euch ein Foto in die Hände. Plötzlich seid ihr wieder dort.",
            "lines": [
                L("P", "Смотр+и, +это ст+арое ф+ото!", "Schau, das ist ein altes Foto!", "aufgeregt"),
                L("U", "Я п+омню +этот день! Мы гул+яли весь в+ечер.", "Ich erinnere mich an diesen Tag! Wir sind den ganzen Abend spaziert.", "verträumt, lächelnd"),
                L("P", "А ты п+омнишь, к+ак мы п+ервый раз встр+етились?", "Und erinnerst du dich, wie wir uns zum ersten Mal trafen?", "sanft, neckend"),
                L("U", "Кон+ечно, я не заб+ыл. Ты опозд+ала на час!", "Natürlich habe ich nicht vergessen. Du bist eine Stunde zu spät gekommen!", "lachend", ruF="Кон+ечно, я не заб+ыла. Ты опозд+ала на час!"),
                L("P", "+Это непр+авда!", "Das stimmt nicht!", "gespielt empört"),
            ],
        },
        "core": [
            C("Я п+омню +этот день.", "Ich erinnere mich an diesen Tag.", "помнить + Akkusativ — ohne Präposition ('an')."),
            C("Я п+омню, к+ак мы встр+етились.", "Ich erinnere mich, wie wir uns trafen.", "помню, как + Satz."),
            C("Я не заб+ыл теб+я.", "Ich habe dich nicht vergessen.", "Frau: заб+ыла."),
        ],
        "explain": "Помнить (sich erinnern) braucht keine Präposition: Я п+омню +этот день. Mit как beschreibst du den Moment: Я п+омню, к+ак … Das Gegenteil ist заб+ыть (vergessen, vollendet): Я заб+ыл (m) / Я заб+ыла (f) телеф+он.",
        "template": {"ru": "Я п+омню {0}.", "de": "Ich erinnere mich an {0}."},
        "slots": [
            {"label": "Woran erinnerst du dich?", "options": [
                O("+этот день", "diesen Tag"), O("твой г+олос", "deine Stimme"),
                O("наш п+ервый день", "unseren ersten Tag"), O("т+от в+ечер", "jenen Abend"),
            ]},
        ],
        "free": {
            "prompt": "Erinnere dich an einen Moment mit jemandem, der dir wichtig ist, und sag ihn laut.",
            "model": C("Я п+омню т+от в+ечер.", "Ich erinnere mich an jenen Abend."),
        },
    },
    {
        "id": "p17_kogda",
        "week": 4,
        "title": "Wenn ich dich sehe … — Verliebtheit in Nebensätzen",
        "formula": "Когд+а …, … (Wenn / Als …, …)",
        "emotion": "Verliebtheit",
        "hook": "Nebensätze machen aus kurzen Aussagen echte Geschichten: Wenn das passiert, dann fühle ich das.",
        "scene": {
            "title": "Wenn ich dich sehe",
            "setting": "Ein Spaziergang am Abend. Anja bemerkt, wie du lächelst, und fragt nach dem Grund.",
            "lines": [
                L("P", "Почем+у ты всегд+а улыб+аешься?", "Warum lächelst du immer?", "neckend"),
                L("U", "Когд+а я в+ижу теб+я, я сч+астлив.", "Wenn ich dich sehe, bin ich glücklich.", "mit rotem Kopf, leise", ruF="Когд+а я в+ижу теб+я, я сч+астлива."),
                L("P", "И я. Когд+а ты р+ядом, мне спок+ойно.", "Mir auch. Wenn du da bist, bin ich ruhig.", "warm"),
                L("U", "Когд+а ты ух+одишь, мне гр+устно.", "Wenn du gehst, bin ich traurig.", "leiser"),
            ],
        },
        "core": [
            C("Когд+а я раб+отаю, я сл+ушаю м+узыку.", "Wenn ich arbeite, höre ich Musik.", "Beide Teile stehen im Präsens; dazwischen steht ein Komma."),
            C("Когд+а мне гр+устно, я звон+ю м+аме.", "Wenn ich traurig bin, rufe ich Mama an.", "Мне грустно bleibt auch im когда-Satz unverändert."),
        ],
        "explain": "Когд+а leitet einen Zeitsatz ein (wenn / als). Zwischen den beiden Sätzen steht immer ein Komma. Für Gewohnheiten nimmst du beide Verben im Präsens: Когд+а я в+ижу теб+я, я улыб+аюсь. Erzählst du von früher, stehen beide in der Vergangenheit: Когд+а я был ребёнком …",
        "template": {"ru": "Когд+а {0}, {1}.", "de": "Wenn {0}, {1}."},
        "slots": [
            {"label": "Wann?", "options": [
                O("я в+ижу теб+я", "ich dich sehe"), O("ты р+ядом", "du da bist"),
                O("мне гр+устно", "ich traurig bin"), O("я раб+отаю", "ich arbeite"),
            ]},
            {"label": "Was passiert?", "options": [
                O("я улыб+аюсь", "lächle ich"), O("я сл+ушаю м+узыку", "höre ich Musik"),
                O("я звон+ю м+аме", "rufe ich Mama an"), O("я сч+астлив", "bin ich glücklich", ruF="я сч+астлива"),
            ]},
        ],
        "free": {
            "prompt": "Beende den Satz mit einem echten Gefühl: Wenn ich …, dann …",
            "model": C("Когд+а я говор+ю по-р+усски, я сч+астлив.", "Wenn ich Russisch spreche, bin ich glücklich."),
        },
    },
    {
        "id": "p18_esli",
        "week": 4,
        "title": "Wenn du willst … — Angebote und Hoffnung",
        "formula": "+Если … (то) …",
        "emotion": "Hoffnung",
        "hook": "Mit если baust du Angebote, Hoffnungen und Pläne: Wenn das passiert, dann tue ich das.",
        "scene": {
            "title": "Wenn du willst, komme ich",
            "setting": "Du bietest an zu kommen, obwohl der Tag lang war. Anja zögert, weil sie sich sorgt.",
            "lines": [
                L("U", "+Если ты х+очешь, я прид+у.", "Wenn du willst, komme ich.", "ruhig, verbindlich"),
                L("P", "Пр+авда? Я бо+юсь, что ты уст+анешь.", "Wirklich? Ich fürchte, du wirst müde.", "zögernd, besorgt"),
                L("U", "+Если я уст+ану, я отдохн+у у теб+я.", "Wenn ich müde werde, ruhe ich mich bei dir aus.", "lächelnd"),
                L("P", "Договор+ились! +Если б+удет д+ождь, мы ост+анемся д+ома.", "Abgemacht! Wenn es regnet, bleiben wir zu Hause.", "froh, entspannt"),
            ],
        },
        "core": [
            C("+Если ты х+очешь, я пом+огу.", "Wenn du willst, helfe ich dir.", "если = wenn (Bedingung)."),
            C("+Если у теб+я есть вр+емя, позвон+и мне.", "Wenn du Zeit hast, ruf mich an.", "Im Hauptsatz kann ein Befehl stehen."),
            C("+Если б+удет д+ождь, мы ост+анемся д+ома.", "Wenn es regnet, bleiben wir zu Hause.", "Achtung: Russisch nutzt hier die Zukunft (будет), obwohl Deutsch das Präsens verwendet."),
        ],
        "explain": "+Если = wenn / falls (Bedingung). Wichtig: Der Russe sagt ehrlich, was in der Zukunft passiert, darum steht im если-Satz die Zukunft: +Если б+удет д+ождь, … (wörtlich 'Wenn es Regen geben wird'). Bei vollendeten Verben genügt die Präsensform mit Zukunftsbedeutung (я прид+у, я пом+огу).",
        "template": {"ru": "+Если {0}, {1}.", "de": "Wenn {0}, {1}."},
        "slots": [
            {"label": "Wenn …", "options": [
                O("ты х+очешь", "du willst"), O("у теб+я есть вр+емя", "du Zeit hast"),
                O("б+удет д+ождь", "es regnet"), O("я уст+ану", "ich müde werde"),
            ]},
            {"label": "… dann", "options": [
                O("я прид+у", "komme ich"), O("мы ост+анемся д+ома", "bleiben wir zu Hause"),
                O("я пом+огу", "helfe ich"), O("я позвон+ю", "rufe ich an"),
            ]},
        ],
        "free": {
            "prompt": "Mach jemandem ein Angebot: Wenn du Zeit hast oder wenn du willst, dann …",
            "model": C("+Если у теб+я есть вр+емя, дав+ай встр+етимся.", "Wenn du Zeit hast, lass uns treffen."),
        },
    },
    {
        "id": "p19_chtoby",
        "week": 4,
        "title": "Ich will, dass es dir gut geht — Wünsche für andere",
        "formula": "Я хоч+у, чт+обы ты + Vergangenheitsform",
        "emotion": "Liebe und Sorge",
        "hook": "Der schönste Wunsch gilt dem anderen. Mit чтобы sagst du, was du dir für ihn oder sie wünschst.",
        "scene": {
            "title": "Ich mache mir Sorgen um dich",
            "setting": "Anja arbeitet seit Wochen zu viel. Heute sagst du, was du dir für sie wünschst.",
            "lines": [
                L("U", "Ты уст+ала. Ты мн+ого раб+отаешь.", "Du bist müde. Du arbeitest viel.", "besorgt"),
                L("P", "Я зн+аю. Но мне н+ужно раб+отать.", "Ich weiß. Aber ich muss arbeiten.", "seufzend"),
                L("U", "Я хоч+у, чт+обы ты отдохн+ула.", "Ich will, dass du dich ausruhst.", "sanft, bestimmt"),
                L("P", "Ты прав. Я отдохн+у з+автра.", "Du hast recht. Ich ruhe mich morgen aus.", "nachgebend, lächelnd", ruF="Ты прав+а. Я отдохн+у з+автра."),
            ],
        },
        "core": [
            C("Я хоч+у, чт+обы ты был сч+астлив.", "Ich will, dass du glücklich bist. (zu einem Mann)", "чтобы + Vergangenheitsform ohne echte Vergangenheit."),
            C("Я хоч+у, чт+обы ты был+а сч+астлива.", "Ich will, dass du glücklich bist. (zu einer Frau)", "Das Geschlecht der angesprochenen Person bestimmt die Endung."),
            C("Я хоч+у, чт+обы ты позвон+ил.", "Ich will, dass du anrufst. (zu einem Mann)", "Frau: позвон+ила."),
        ],
        "explain": "Wenn das Subjekt wechselt (ich will — du sollst), nimmst du чт+обы + Vergangenheitsform: Я хоч+у, чт+обы ты отдохн+ул (m) / отдохн+ула (f). Die Vergangenheitsform steht hier für Wunsch, nicht für Zeit. Bleibt das Subjekt gleich, nutzt du die Grundform: Я хоч+у отдохн+уть.",
        "template": {"ru": "Я хоч+у, чт+обы ты {0}.", "de": "Ich will, dass du {0}."},
        "slots": [
            {"label": "Was wünschst du ihm oder ihr?", "options": [
                O("был сч+астлив", "glücklich bist (zu einem Mann)"), O("был+а сч+астлива", "glücklich bist (zu einer Frau)"),
                O("позвон+ил", "anrufst (zu einem Mann)"), O("позвон+ила", "anrufst (zu einer Frau)"),
                O("отдохн+ул", "dich ausruhst (zu einem Mann)"), O("отдохн+ула", "dich ausruhst (zu einer Frau)"),
            ]},
        ],
        "free": {
            "prompt": "Sag jemandem, den du magst, was du dir für ihn oder sie wünschst.",
            "model": C("Я хоч+у, чт+обы ты был сч+астлив.", "Ich will, dass du glücklich bist."),
        },
    },
    {
        "id": "p20_vazhno",
        "week": 4,
        "title": "Für mich ist wichtig … — Werte und Überzeugungen",
        "formula": "Для мен+я в+ажно + Grundform · Для мен+я важн+а / в+ажен + Nomen",
        "emotion": "Überzeugung",
        "hook": "Wer seine Werte aussprechen kann, führt Gespräche in die Tiefe — und zeigt, wer er wirklich ist.",
        "scene": {
            "title": "Was ist dir wichtig?",
            "setting": "Später Abend, Kerzenlicht. Ihr habt über Banales geredet. Jetzt wird es persönlich.",
            "lines": [
                L("P", "Что для теб+я в+ажно в ж+изни?", "Was ist dir wichtig im Leben?", "ernst, interessiert"),
                L("U", "Для мен+я важн+а семь+я. И ч+естность.", "Für mich ist die Familie wichtig. Und Ehrlichkeit.", "ruhig, überzeugt"),
                L("P", "Мне т+оже. А ещё для мен+я в+ажно вр+емя.", "Mir auch. Und für mich ist auch Zeit wichtig.", "nachdenklich"),
                L("U", "Дав+ай б+удем б+ольше говор+ить.", "Lass uns mehr reden.", "weich, ernst"),
            ],
        },
        "core": [
            C("Для мен+я важн+а семь+я.", "Für mich ist die Familie wichtig.", "семья ist weiblich → важна."),
            C("Для мен+я в+ажно вр+емя.", "Für mich ist Zeit wichtig.", "время ist sächlich → важно."),
            C("Для мен+я в+ажно быть с тоб+ой.", "Für mich ist es wichtig, bei dir zu sein.", "Vor einer Grundform steht в+ажно (sächlich)."),
        ],
        "explain": "Для мен+я = für mich (для + Genitiv: для теб+я, для н+его). Важно heißt 'wichtig'. Vor Nomen passt es sich an: в+ажен (m), важн+а (f), в+ажно (n), важн+ы (pl). Vor einer Grundform steht immer в+ажно: Для мен+я в+ажно говор+ить пр+авду.",
        "template": {"ru": "Для мен+я в+ажно {0}.", "de": "Für mich ist es wichtig, {0}."},
        "slots": [
            {"label": "Was ist dir wichtig?", "options": [
                O("говор+ить пр+авду", "die Wahrheit zu sagen"), O("быть с семьёй", "bei der Familie zu sein"),
                O("уч+иться к+аждый день", "jeden Tag zu lernen"), O("сл+ушать друг др+уга", "einander zuzuhören"),
            ]},
        ],
        "free": {
            "prompt": "Sag, was dir im Leben am wichtigsten ist.",
            "model": C("Для мен+я важн+а семь+я.", "Für mich ist die Familie wichtig."),
        },
    },
]
