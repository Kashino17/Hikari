"""Grammatik-Muster 1–8: je Szene mit Gefühl, Kernsätze, Bausteine zum Tauschen, freie Sprechaufgabe."""


def L(who, ru, de, how="", ruF=None, deF=None):
    d = {"who": who, "ru": ru, "de": de, "how": how}
    if ruF:
        d["ruF"] = ruF
    if deF:
        d["deF"] = deF
    return d


def C(ru, de, why=""):
    return {"ru": ru, "de": de, "why": why}


def O(ru, de, ruF=None, deF=None):
    d = {"ru": ru, "de": de}
    if ruF:
        d["ruF"] = ruF
    if deF:
        d["deF"] = deF
    return d


PATTERNS = [
    {
        "id": "p01_hochu",
        "week": 1,
        "title": "Ich will … — Wünsche sagen",
        "formula": "Я хоч+у + Grundform",
        "emotion": "Sehnsucht",
        "hook": "Wer sagt, was er will, wird verstanden — und kommt dem anderen näher. Mit diesem Muster sprichst du über Wünsche, Hunger, Heimweh und Pläne.",
        "scene": {
            "title": "Heimweh am Telefon",
            "setting": "Es ist spät am Abend. Du bist weit weg von zu Hause, Anja ruft an und merkt sofort, dass etwas nicht stimmt.",
            "lines": [
                L("P", "Что случ+илось? Ты гр+устный.", "Was ist los? Du bist traurig.", "besorgt, weich", ruF="Что случ+илось? Ты гр+устная."),
                L("U", "Я скуч+аю по д+ому.", "Ich vermisse mein Zuhause.", "leise, müde"),
                L("P", "Чег+о ты х+очешь?", "Was möchtest du?", "sanft nachfragen"),
                L("U", "Я хоч+у дом+ой! Я хоч+у в+идеть м+аму.", "Ich will nach Hause! Ich will meine Mama sehen.", "mit Nachdruck, die Stimme zittert"),
                L("P", "Я теб+я поним+аю. Дав+ай позвон+им м+аме!", "Ich verstehe dich. Lass uns Mama anrufen!", "warm, aufmunternd"),
                L("U", "Дав+ай! Спас+ибо!", "Ja, lass uns! Danke!", "erleichtert"),
            ],
        },
        "core": [
            C("Я хоч+у спать.", "Ich will schlafen.", "Direkt nach я хочу folgt die Grundform des Verbs — ohne 'zu'."),
            C("Я хоч+у говор+ить по-р+усски.", "Ich will Russisch sprechen.", "Mehrere Wörter dürfen folgen; das Verb steht trotzdem in der Grundform."),
            C("Я хоч+у к+офе.", "Ich möchte einen Kaffee.", "Mit einem Nomen steht das Gewünschte im Akkusativ (hier unverändert)."),
        ],
        "explain": "Хотеть (wollen) gehört zu den wichtigsten Verben: я хоч+у, ты х+очешь, он х+очет, мы хот+им, вы хот+ите, он+и хот+ят. Danach kommt direkt die Grundform: Я хочу спать. Wer höflicher klingen will, sagt Я хот+ел бы (m) / Я хот+ела бы (f) = ich hätte gern.",
        "template": {"ru": "Я хоч+у {0}.", "de": "Ich will {0}."},
        "slots": [
            {"label": "Was willst du?", "options": [
                O("спать", "schlafen"), O("есть", "essen"), O("дом+ой", "nach Hause"),
                O("говор+ить по-р+усски", "Russisch sprechen"), O("путеш+ествовать", "reisen"),
                O("отдохн+уть", "mich ausruhen"),
            ]},
        ],
        "free": {
            "prompt": "Sag deinem besten Freund oder deiner besten Freundin, was du dir gerade am meisten wünschst.",
            "model": C("Я хоч+у путеш+ествовать.", "Ich will reisen."),
        },
    },
    {
        "id": "p02_nravitsya",
        "week": 1,
        "title": "Mir gefällt … — Begeisterung zeigen",
        "formula": "Мне нр+авится + Nominativ · Я любл+ю + Grundform",
        "emotion": "Begeisterung",
        "hook": "Wer zeigt, was ihn begeistert, wirkt lebendig — und gibt dem anderen etwas, woran er anknüpfen kann.",
        "scene": {
            "title": "Erstes Date im Café",
            "setting": "Ihr kennt euch kaum. Es kribbelt ein bisschen — und jeder will herausfinden, was der andere mag.",
            "lines": [
                L("P", "Что ты л+юбишь д+елать?", "Was machst du gern?", "neugierig, lächelnd"),
                L("U", "Я любл+ю смотр+еть аним+е. А ты?", "Ich schaue gern Anime. Und du?", "begeistert"),
                L("P", "Я любл+ю м+узыку. Мне нр+авится рок.", "Ich liebe Musik. Mir gefällt Rock.", "stolz"),
                L("U", "Мне с тоб+ой интер+есно.", "Mit dir ist es spannend.", "mit einem kleinen Lächeln"),
                L("P", "Мне т+оже.", "Mir auch.", "leise, froh"),
            ],
        },
        "core": [
            C("Мне нр+авится м+узыка.", "Mir gefällt Musik.", "Die Person steht im Dativ (мне); das, was gefällt, ist das Subjekt."),
            C("Я любл+ю чит+ать.", "Ich lese gern.", "Я люблю + Grundform = etwas gern tun."),
            C("Ты мне нр+авишься.", "Du gefällst mir.", "Ты ist hier Subjekt: 'du gefällst mir' — vorsichtig verliebt."),
        ],
        "explain": "Zwei Wege: Мне нр+авится + Sache (es gefällt mir) und Я любл+ю + Tätigkeit/Sache (ich liebe). Merke: нравится passt sich dem Gefallenden an — Singular нравится, Plural нравятся: Мне нр+авятся ф+ильмы. Die Stärke steigt: нравится → люблю → обожаю.",
        "template": {"ru": "Мне нр+авится {0}.", "de": "Mir gefällt {0}."},
        "slots": [
            {"label": "Was gefällt dir?", "options": [
                O("м+узыка", "die Musik"), O("р+усский яз+ык", "die russische Sprache"),
                O("+этот г+ород", "diese Stadt"), O("твой смех", "dein Lachen"),
                O("+эта п+есня", "dieses Lied"),
            ]},
        ],
        "free": {
            "prompt": "Erzähle, was dir an Russisch am meisten gefällt — oder was du an einem Menschen magst.",
            "model": C("Мне нр+авится р+усский яз+ык.", "Mir gefällt die russische Sprache."),
        },
    },
    {
        "id": "p03_chuvstvuyu",
        "week": 1,
        "title": "Ich fühle mich … — Gefühle benennen",
        "formula": "Я ч+увствую себ+я + Adverb",
        "emotion": "Trost",
        "hook": "Gefühle in Worte zu fassen, macht sie kleiner. Mit diesem Muster sagst du, wie es dir wirklich geht — und der andere kann dich auffangen.",
        "scene": {
            "title": "Ein schwerer Tag",
            "setting": "Du kommst erschöpft nach Hause. Anja fragt nicht nach Zahlen, sondern nach dir.",
            "lines": [
                L("P", "К+ак ты себ+я ч+увствуешь?", "Wie fühlst du dich?", "ruhig, aufmerksam"),
                L("U", "Я ч+увствую себ+я уж+асно. Я +очень уст+ал.", "Ich fühle mich schrecklich. Ich bin sehr müde.", "schwer, ausatmend", ruF="Я ч+увствую себ+я уж+асно. Я +очень уст+ала."),
                L("P", "Не пережив+ай. Я с тоб+ой.", "Mach dir keine Sorgen. Ich bin bei dir.", "beruhigend, warm"),
                L("U", "Спас+ибо. Мне л+учше, когд+а ты р+ядом.", "Danke. Mir geht es besser, wenn du da bist.", "leise, dankbar"),
            ],
        },
        "core": [
            C("Я ч+увствую себ+я хорош+о.", "Ich fühle mich gut.", "Nach чувствую себя steht ein Adverb auf -о (хорошо), kein Adjektiv."),
            C("Я ч+увствую себ+я сч+астливо.", "Ich fühle mich glücklich.", "Auch hier -о: счастливо."),
            C("Я ч+увствую себ+я один+око.", "Ich fühle mich einsam.", "Ein Gefühl, das man aussprechen darf."),
        ],
        "explain": "Чувствовать себя = 'sich fühlen' (wörtlich 'fühlen sich selbst'). Danach steht ein Adverb auf -о: хорошо, плохо, ужасно, спокойно. Alternativ sagst du Мне + Zustand (Мне грустно) — beides drückt Gefühle aus; das erste ist ausführlicher, das zweite kürzer.",
        "template": {"ru": "Я ч+увствую себ+я {0}.", "de": "Ich fühle mich {0}."},
        "slots": [
            {"label": "Wie fühlst du dich?", "options": [
                O("хорош+о", "gut"), O("пл+охо", "schlecht"), O("уж+асно", "schrecklich"),
                O("отл+ично", "super"), O("сч+астливо", "glücklich"), O("один+око", "einsam"),
                O("спок+ойно", "ruhig"),
            ]},
        ],
        "free": {
            "prompt": "Sag ehrlich, wie du dich gerade fühlst.",
            "model": C("Я ч+увствую себ+я хорош+о.", "Ich fühle mich gut."),
        },
    },
    {
        "id": "p04_potomu_chto",
        "week": 1,
        "title": "Weil … — Gründe nennen",
        "formula": "…, потом+у что + Satz",
        "emotion": "Rechtfertigung",
        "hook": "In jedem emotionalen Gespräch kommt irgendwann das Warum. Wer Gründe nennen kann, kann sich erklären — und Streit entschärfen.",
        "scene": {
            "title": "Warum hast du nicht angerufen?",
            "setting": "Anja hat den ganzen Abend gewartet. Sie ist nicht wütend, aber verletzt.",
            "lines": [
                L("P", "Почем+у ты не позвон+ил?", "Warum hast du nicht angerufen?", "enttäuscht, ruhig", ruF="Почем+у ты не позвон+ила?"),
                L("U", "Я не мог говор+ить, потом+у что я раб+отал.", "Ich konnte nicht sprechen, weil ich gearbeitet habe.", "angespannt, erklärend", ruF="Я не могл+а говор+ить, потом+у что я раб+отала."),
                L("P", "Мне об+идно.", "Ich bin gekränkt.", "leise, verletzt"),
                L("U", "Прост+и. Я не хот+ел теб+я об+идеть.", "Verzeih. Ich wollte dich nicht kränken.", "aufrichtig, warm", ruF="Прост+и. Я не хот+ела теб+я об+идеть."),
                L("P", "Ничег+о стр+ашного.", "Nicht schlimm.", "milder werdend"),
            ],
        },
        "core": [
            C("Я д+ома, потом+у что я уст+ал.", "Ich bin zu Hause, weil ich müde bin.", "потому что leitet den Grund ein; davor steht ein Komma."),
            C("Я уч+у р+усский, потом+у что +это интер+есно.", "Ich lerne Russisch, weil das interessant ist.", "Die Reihenfolge ist wie im Deutschen: Aussage, Komma, Grund."),
        ],
        "explain": "Почем+у? (Warum?) beantwortest du mit Потом+у что … (Weil …). Wichtig: Das Komma steht immer davor, und потому что bleibt zusammen: In Gesprächen betonst du die Antwort (потом+у). Kurz geht auch nur Потому что …",
        "template": {"ru": "{0}, потом+у что {1}.", "de": "{0}, weil {1}."},
        "slots": [
            {"label": "Was tust du?", "options": [
                O("Я уч+у р+усский", "Ich lerne Russisch"), O("Я не пойд+у в каф+е", "Ich gehe nicht ins Café"),
                O("Я вст+ану р+ано", "Ich stehe früh auf"),
            ]},
            {"label": "Warum?", "options": [
                O("мне +это нр+авится", "es mir gefällt"),
                O("я уст+ал", "ich müde bin", ruF="я уст+ала"),
                O("мне н+ужно раб+отать", "ich arbeiten muss"),
            ]},
        ],
        "free": {
            "prompt": "Beantworte ehrlich: Warum lernst du Russisch?",
            "model": C("Я уч+у р+усский, потом+у что мне нр+авится р+усский яз+ык.", "Ich lerne Russisch, weil mir die russische Sprache gefällt."),
        },
    },
    {
        "id": "p05_kazhetsya",
        "week": 2,
        "title": "Mir scheint, dass … — Meinung und Zweifel",
        "formula": "Мне к+ажется, что … · Я д+умаю, что …",
        "emotion": "Zweifel",
        "hook": "Tiefe Gespräche leben von Meinungen — und davon, sie vorsichtig oder fest auszusprechen. Die Wahl des Einstiegs verrät schon, wie sicher du bist.",
        "scene": {
            "title": "Ist das eine gute Idee?",
            "setting": "Du erzählst von einem großen Plan. Anja ist nicht sicher, ob sie dich bremsen oder anfeuern soll.",
            "lines": [
                L("U", "Я хоч+у пож+ить в Росс+ии.", "Ich will eine Zeit in Russland leben.", "aufgeregt, hoffnungsvoll"),
                L("P", "Серьёзно? Мне к+ажется, что +это тр+удно.", "Ernsthaft? Mir scheint, das ist schwer.", "zweifelnd, nachdenklich"),
                L("U", "Я д+умаю, что я смог+у.", "Ich denke, ich schaffe das.", "fest, entschlossen"),
                L("P", "По-м+оему, +это хор+ошая ид+ея!", "Ich finde, das ist eine gute Idee!", "überzeugt, lachend"),
            ],
        },
        "core": [
            C("Мне к+ажется, что +это хорош+о.", "Mir scheint, das ist gut.", "Behutsam: nimmt dem Satz die Härte."),
            C("Я д+умаю, что ты прав.", "Ich denke, du hast recht.", "Ein klares, persönliches Urteil."),
            C("По-м+оему, +это тр+удно.", "Ich finde, das ist schwer.", "Kurz und neutral."),
        ],
        "explain": "Meinungen haben Abstufungen: Мне к+ажется, что … (mir scheint — vorsichtig), Я д+умаю, что … (ich denke), Я счит+аю, что … (ich bin überzeugt), По-м+оему … (meiner Meinung nach). Vor что steht immer ein Komma.",
        "template": {"ru": "{0} {1}.", "de": "{0} {1}."},
        "slots": [
            {"label": "Wie vorsichtig?", "options": [
                O("Мне к+ажется, что", "Mir scheint:"), O("Я д+умаю, что", "Ich denke:"),
                O("Я счит+аю, что", "Ich bin überzeugt:"), O("По-м+оему,", "Meiner Meinung nach:"),
            ]},
            {"label": "Was meinst du?", "options": [
                O("+это хор+ошая ид+ея", "das ist eine gute Idee"), O("+это тр+удно", "das ist schwer"),
                O("ты прав", "du hast recht", ruF="ты прав+а"),
            ]},
        ],
        "free": {
            "prompt": "Gib deine Meinung: Wie findest du Russisch — schwer oder leicht?",
            "model": C("Мне к+ажется, что р+усский яз+ык тр+удный, но интер+есный.", "Mir scheint, die russische Sprache ist schwer, aber interessant."),
        },
    },
    {
        "id": "p06_nuzhno",
        "week": 2,
        "title": "Ich muss / ich kann nicht — Druck und Grenzen",
        "formula": "Мне н+ужно + Grundform · Я не мог+у + Grundform",
        "emotion": "Frust",
        "hook": "Nein sagen und Grenzen setzen gehört zu jedem tiefen Gespräch. Mit diesem Muster sagst du, was du musst — und was gerade nicht geht.",
        "scene": {
            "title": "Zu viel zu tun",
            "setting": "Du bist gestresst, Anja möchte dich sehen. Zwischen Verständnis und Enttäuschung liegt nur ein Satz.",
            "lines": [
                L("P", "Дав+ай встр+етимся з+автра в+ечером!", "Lass uns morgen Abend treffen!", "freundlich, hoffnungsvoll"),
                L("U", "Не мог+у. Мне н+ужно раб+отать.", "Ich kann nicht. Ich muss arbeiten.", "gestresst, seufzend"),
                L("P", "Ты всегд+а раб+отаешь!", "Du arbeitest immer!", "enttäuscht"),
                L("U", "Я зн+аю. Мне +очень жаль. Дав+ай в выходн+ые?", "Ich weiß. Es tut mir sehr leid. Wie wäre es am Wochenende?", "weich, bittend"),
                L("P", "Хорош+о. Договор+ились!", "Gut. Abgemacht!", "versöhnt"),
            ],
        },
        "core": [
            C("Мне н+ужно раб+отать.", "Ich muss arbeiten.", "Мне нужно: die Person im Dativ, wörtlich 'mir ist nötig'."),
            C("Я не мог+у говор+ить.", "Ich kann nicht sprechen.", "Не мог+у = 'ich kann nicht'; danach die Grundform."),
            C("Я д+олжен идт+и.", "Ich muss gehen.", "Должен: persönliche Pflicht; Frau: должн+а."),
        ],
        "explain": "Drei Wege für Notwendigkeit: Мне н+ужно / Мне н+адо + Grundform (es ist nötig), Я д+олжен (m) / Я должн+а (f) + Grundform (ich bin verpflichtet). Können: мочь — я мог+у, ты м+ожешь, он м+ожет, мы м+ожем, вы м+ожете, он+и м+огут.",
        "template": {"ru": "{0} {1}.", "de": "{0} {1}."},
        "slots": [
            {"label": "Muss oder kann nicht?", "options": [
                O("Мне н+ужно", "Ich muss"), O("Я не мог+у", "Ich kann nicht"),
                O("Я д+олжен", "Ich muss", ruF="Я должн+а"),
            ]},
            {"label": "Was?", "options": [
                O("раб+отать", "arbeiten"), O("уч+ить р+усский", "Russisch lernen"),
                O("идт+и дом+ой", "nach Hause gehen"), O("спать", "schlafen"),
            ]},
        ],
        "free": {
            "prompt": "Lade jemanden ein — und sage dann ehrlich, warum du heute nicht kannst.",
            "model": C("Не мог+у. Мне н+ужно раб+отать.", "Ich kann nicht. Ich muss arbeiten."),
        },
    },
    {
        "id": "p07_proshloe",
        "week": 2,
        "title": "Gestern war ich … — Erzählen, was passiert ist",
        "formula": "Vergangenheit: Stamm + -л / -ла",
        "emotion": "Peinlichkeit und Stolz",
        "hook": "Geschichten aus deinem Leben verbinden. Mit der Vergangenheit erzählst du, was dich geprägt hat — Peinliches wie Stolzes.",
        "scene": {
            "title": "Das war mir so peinlich",
            "setting": "Du erzählst Anja von einem Tag, an dem alles schiefging — und lachst jetzt darüber.",
            "lines": [
                L("P", "Что случ+илось вчер+а?", "Was ist gestern passiert?", "neugierig"),
                L("U", "Я заб+ыл телеф+он, и я опозд+ал на раб+оту.", "Ich habe mein Handy vergessen, und ich bin zu spät zur Arbeit gekommen.", "peinlich berührt, lachend", ruF="Я заб+ыла телеф+он, и я опозд+ала на раб+оту."),
                L("P", "Ой! И что б+ыло д+альше?", "Oh! Und was war dann?", "gespannt"),
                L("U", "Нач+альник ничег+о не сказ+ал.", "Der Chef hat nichts gesagt.", "erleichtert"),
                L("P", "В+от и хорош+о!", "Na also, gut!", "lachend"),
            ],
        },
        "core": [
            C("Вчер+а я был в каф+е.", "Gestern war ich im Café.", "Mann: был, Frau: была. Die Endung zeigt dein Geschlecht."),
            C("Я сд+елал +это!", "Ich habe es geschafft!", "Frau: Я сд+елала +это!"),
            C("Я смотр+ел ф+ильм.", "Ich habe einen Film geschaut.", "смотр+ел / смотр+ела — Stamm + -л / -ла."),
        ],
        "explain": "Die Vergangenheit hat nur eine Form pro Geschlecht: Infinitivstamm + -л (er), -ла (sie), -ло (es), -ли (wir/sie, alle). Wer spricht, bestimmt die Endung: ein Mann sagt я смотр+ел, eine Frau я смотр+ела. Es gibt keinen Unterschied zwischen 'habe gesehen' und 'sah'.",
        "template": {"ru": "{0} {1}.", "de": "{0} {1}."},
        "slots": [
            {"label": "Wann?", "options": [
                O("Вчер+а", "Gestern"), O("Сег+одня +утром", "Heute Morgen"), O("Вчер+а в+ечером", "Gestern Abend"),
            ]},
            {"label": "Was hast du getan?", "options": [
                O("я был в каф+е", "war ich im Café", ruF="я был+а в каф+е"),
                O("я смотр+ел ф+ильм", "habe ich einen Film geschaut", ruF="я смотр+ела ф+ильм"),
                O("я чит+ал кн+игу", "habe ich ein Buch gelesen", ruF="я чит+ала кн+игу"),
                O("я раб+отал", "habe ich gearbeitet", ruF="я раб+отала"),
                O("я сд+елал +это", "habe ich es geschafft", ruF="я сд+елала +это"),
            ]},
        ],
        "free": {
            "prompt": "Erzähle von deinem Tag: Was hast du gestern gemacht?",
            "model": C("Вчер+а я раб+отал, а в+ечером смотр+ел ф+ильм.", "Gestern habe ich gearbeitet, und abends habe ich einen Film geschaut."),
        },
    },
    {
        "id": "p08_budushchee",
        "week": 2,
        "title": "Ich werde … — Pläne und Vorfreude",
        "formula": "б+уду + Grundform",
        "emotion": "Vorfreude",
        "hook": "Wer von der Zukunft spricht, öffnet Türen: für Einladungen, Pläne und gemeinsame Träume.",
        "scene": {
            "title": "Wir fahren zusammen weg",
            "setting": "Das Wochenende steht an, und Anja hat eine Idee, bei der dir das Herz höher schlägt.",
            "lines": [
                L("P", "Что ты б+удешь д+елать в выходн+ые?", "Was wirst du am Wochenende machen?", "locker, neugierig"),
                L("U", "Я б+уду отдых+ать и чит+ать.", "Ich werde mich ausruhen und lesen.", "gelassen"),
                L("P", "А х+очешь по+ехать со мн+ой в Москв+у?", "Und willst du mit mir nach Moskau fahren?", "aufgeregt, mit Herzklopfen"),
                L("U", "Да! Я +очень р+ад! Когд+а?", "Ja! Ich freue mich sehr! Wann?", "begeistert", ruF="Да! Я +очень р+ада! Когд+а?"),
                L("P", "В субб+оту.", "Am Samstag.", "strahlend"),
                L("U", "Я уж+е жду!", "Ich warte schon!", "lachend, ungeduldig"),
            ],
        },
        "core": [
            C("Я б+уду раб+отать.", "Ich werde arbeiten.", "Zukunft = Form von быть + Grundform."),
            C("Мы б+удем отдых+ать.", "Wir werden uns ausruhen.", "Мы будем: 'wir werden'."),
            C("Я по+еду в Росс+ию.", "Ich werde nach Russland fahren.", "Bei einmaligen Handlungen genügt die 'ich'-Form des vollendeten Verbs."),
        ],
        "explain": "Zukunft mit unvollendeten Verben: я б+уду, ты б+удешь, он б+удет, мы б+удем, вы б+удете, он+и б+удут + Grundform. Bei vollendeten Verben (einmalige, abgeschlossene Handlung) steht die einfache Präsensform mit Zukunftsbedeutung: я по+еду, я сд+елаю.",
        "template": {"ru": "{0} {1}.", "de": "{0} {1}."},
        "slots": [
            {"label": "Wer?", "options": [O("Я б+уду", "Ich werde"), O("Мы б+удем", "Wir werden")]},
            {"label": "Was?", "options": [
                O("изуч+ать р+усский", "Russisch lernen"), O("отдых+ать", "Pause machen"),
                O("раб+отать", "arbeiten"), O("гот+овить", "kochen"),
            ]},
        ],
        "free": {
            "prompt": "Was wirst du in den nächsten vier Wochen für dein Russisch tun?",
            "model": C("Я б+уду уч+ить р+усский к+аждый день.", "Ich werde jeden Tag Russisch lernen."),
        },
    },
]
