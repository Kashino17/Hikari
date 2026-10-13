"""Deepdive-Inhalte Teil 3: Alltag, Restaurant, Zeit, Gefühle, Wünsche, Abschied, Einladungen."""
from part1 import E, V

ENTRIES = {
    "d01_do_svidaniya": {
        "register": "höflich · förmlich",
        "summary": "До свид+ания ('bis zum Wiedersehen') ist der höfliche Standard-Abschied. Unter Freunden reicht Пок+а; wer Wärme zeigen will, wählt Всег+о хор+ошего oder До ск+орого.",
        "variants": [
            V("Пок+а!", "Tschüss!", "Nur unter Freunden und Bekannten."),
            V("Всег+о д+обрего!", "Alles Gute! (höflich)", "Freundlich-förmlich, auch am Telefon."),
            V("До встр+ечи!", "Bis zum Treffen!", "Wenn ihr euch bald wiederseht."),
            V("До ск+орого!", "Bis bald!", "Herzlich und vertraut."),
            V("Счастл+иво!", "Mach's gut!", "Locker, wie 'bleib gesund' — beliebt unter Freunden."),
        ],
        "examples": [
            E("До свид+ания! Хор+ошего дня!", "Auf Wiedersehen! Einen schönen Tag noch!", "Förmlicher Abschied mit Wunsch (Genitiv)."),
            E("Пок+а, до з+автра!", "Tschüss, bis morgen!", "До + Genitiv = 'bis (zu)'."),
            E("Всег+о д+обрего, до ск+орого!", "Alles Gute, bis bald!", "Zwei warme Formeln hintereinander."),
        ],
        "grammar": "До + Genitiv heißt 'bis': до свид+ания, до встр+ечи, до з+автра, до в+ечера. Die Wörter nach до stehen im Genitiv.",
    },
    "d02_ochen_priyatno": {
        "register": "neutral · höflich",
        "summary": "Beim Kennenlernen sagst du +Очень при+ятно. Wer wärmer klingen will, ergänzt Р+ад познак+омиться — mit Geschlechtsform: р+ад (m), р+ада (f).",
        "variants": [
            V("Р+ад познак+омиться!", "Ich freue mich, Sie kennenzulernen. (Mann)", "Etwas gehobener als Очень приятно."),
            V("Р+ада познак+омиться!", "Ich freue mich, Sie kennenzulernen. (Frau)", "Weibliche Form."),
            V("Мне т+оже.", "Mir auch.", "Standard-Antwort auf Очень приятно."),
            V("Вза+имно!", "Gleichfalls!", "Kurz, höflich, gern im Berufsleben."),
            V("+Очень рад теб+я в+идеть!", "Ich freue mich sehr, dich zu sehen! (Mann)", "Wenn ihr euch schon kennt."),
        ],
        "examples": [
            E("Мен+я зов+ут Кад+ир. +Очень при+ятно!", "Ich heiße Kadir. Freut mich!", "Namen und Höflichkeit in einem Atemzug."),
            E("Р+ада познак+омиться. — Вза+имно!", "Freut mich, Sie kennenzulernen. — Gleichfalls!", "Ein höflicher Mini-Dialog."),
        ],
        "gender": {
            "title": "р+ад / р+ада",
            "text": "Wer sagt Р+ад познак+омиться, ist ein Mann; eine Frau sagt Р+ада познак+омиться. Das Gegenüber ändert nichts an deiner Form — nur dein eigenes Geschlecht zählt.",
        },
        "grammar": "Рад ist ein Kurzadjektiv und richtet sich nach dem Sprecher: рад / рада / рады. Danach folgt eine Grundform: Рад познакомиться, Рада тебя видеть.",
    },
    "d02_a_ty": {
        "register": "neutral",
        "summary": "А ты? gibt die Frage zurück: 'Und du?' Die kleinen Wörter и, а und но klingen ähnlich, sagen aber Verschiedenes: и verbindet, а stellt gegenüber, но widerspricht.",
        "variants": [
            V("А вы?", "Und Sie?", "Höfliche Variante."),
            V("А у теб+я?", "Und bei dir?", "Passt zu Как у тебя дела?"),
            V("И ты?", "Du auch?", "Frage nach Übereinstimmung."),
            V("А как у вас?", "Und wie ist es bei Ihnen?", "Höflich, freundlich."),
        ],
        "examples": [
            E("Я из Герм+ании, а ты?", "Ich komme aus Deutschland, und du?", "А stellt zwei Personen gegenüber."),
            E("Я жив+у в Берл+ине, и я раб+отаю там.", "Ich wohne in Berlin, und ich arbeite dort.", "И verbindet zwei Dinge, die zusammenpassen."),
            E("Я хоч+у, но не мог+у.", "Ich will, aber ich kann nicht.", "Но zeigt einen Widerspruch."),
        ],
        "grammar": "и = und (verbindend), а = und/aber (Gegenüberstellung, Kontrast), но = aber (Einwand). Merke: А ты? gibt immer die Frage zurück.",
    },
    "d03_chto_novogo": {
        "register": "locker",
        "summary": "Что н+ового? ist die Frage nach Neuigkeiten. Die Antwort sagt viel über die Stimmung: Von Ничег+о н+ового bis Мн+ого всег+о.",
        "variants": [
            V("Как усп+ехи?", "Wie läuft's (mit dem, woran du arbeitest)?", "Fragt nach Fortschritten, z. B. beim Lernen."),
            V("Что сл+ышно?", "Was gibt's?", "Locker, wie 'was hört man?'."),
            V("Ничег+о н+ового.", "Nichts Neues.", "Normale, ruhige Antwort."),
            V("Всё по-ст+арому.", "Alles beim Alten.", "Gelassene Antwort."),
            V("Мн+ого всег+о!", "Viel los!", "Wenn viel passiert ist."),
        ],
        "examples": [
            E("Что н+ового? — Ничег+о н+ового. А у теб+я?", "Was gibt's Neues? — Nichts Neues. Und bei dir?", "Genitiv nach что: н+ового."),
            E("Как усп+ехи? — Хорош+о, я уч+усь.", "Wie läuft's? — Gut, ich lerne.", "Ideal für Lernende."),
        ],
        "grammar": "Nach что und ничег+о steht der Genitiv: что н+ового, ничег+о н+ового ('was an Neuem'). Ähnlich: что интер+есного? (was Interessantes?)",
    },
    "d04_skolko_stoit": {
        "register": "neutral",
        "summary": "Preise gehören zu jedem Alltag. Wichtig: Nach Zahlen steht рубль, рубля oder рублей — dieselbe Regel wie bei год, года, лет.",
        "variants": [
            V("Ск+олько +это ст+оит?", "Wie viel kostet das?", "Mit +это klingt es natürlicher."),
            V("Как+ая цен+а?", "Was ist der Preis?", "Etwas gehobener."),
            V("Почём?", "Wie viel (pro Stück / Kilo)?", "Locker, auf dem Markt."),
            V("Ск+олько с мен+я?", "Wie viel schulde ich?", "Beim Bezahlen."),
            V("+Это д+орого.", "Das ist teuer.", "Zum Verhandeln oder Kommentieren."),
        ],
        "examples": [
            E("Ск+олько ст+оит? — Сто рубл+ей.", "Wie viel kostet das? — Hundert Rubel.", "Ab fünf steht рублей (Genitiv Plural)."),
            E("Два рубл+я, три рубл+я, пять рубл+ей.", "Zwei, drei, fünf Rubel.", "1 рубль, 2–4 рубля, ab 5 рублей."),
            E("+Это д+орого. М+ожно деш+евле?", "Das ist teuer. Geht es günstiger?", "Дешев+ле ist der Komparativ von дёшево."),
        ],
        "grammar": "Zahl + Nomen: 1 → Nominativ Singular (рубль), 2–4 → Genitiv Singular (рубля), ab 5 → Genitiv Plural (рублей). Dasselbe Muster gilt für год, часа, часов.",
    },
    "d05_ya_govoryu_nemnogo": {
        "register": "neutral",
        "summary": "Wie gut du Russisch sprichst, kannst du fein abstufen — vom Anfänger bis zum Fließenden. Das hilft, das Gespräch auf dein Niveau zu bringen.",
        "variants": [
            V("Я т+олько уч+усь.", "Ich lerne erst.", "Bescheiden und freundlich."),
            V("Я говор+ю немн+ого по-р+усски.", "Ich spreche ein bisschen Russisch.", "Neutral."),
            V("Я поним+аю л+учше, чем говор+ю.", "Ich verstehe besser, als ich spreche.", "Ehrlich — typisch für Lernende."),
            V("Я говор+ю своб+одно.", "Ich spreche fließend.", "Wenn du es kannst."),
        ],
        "examples": [
            E("Я говор+ю немн+ого по-р+усски. Говор+ите м+едленнее!", "Ich spreche ein bisschen Russisch. Sprechen Sie langsamer!", "Natürlich und höflich."),
            E("Я поним+аю л+учше, чем говор+ю.", "Ich verstehe besser, als ich spreche.", "Чем = als. л+учше = besser."),
        ],
        "grammar": "по-р+усски (mit Bindestrich) bedeutet 'auf Russisch' und ist ein Adverb; formeller sagst du на р+усском яз+ыке. Beide sind richtig.",
    },
    "d05_kak_skazat": {
        "register": "neutral",
        "summary": "Wörter zu erfragen ist die wichtigste Lernstrategie. Diese Fragen sind dein Werkzeugkasten: wie sagt man, wie schreibt man, wie spricht man aus.",
        "variants": [
            V("Как +это назыв+ается?", "Wie heißt das?", "Du zeigst auf einen Gegenstand."),
            V("Как п+ишется?", "Wie schreibt man das?", "Zur Schreibweise."),
            V("Как произн+осится?", "Wie spricht man das aus?", "Zur Aussprache."),
            V("Что +это зн+ачит?", "Was bedeutet das?", "Zur Bedeutung."),
            V("Как сказ+ать …?", "Wie sagt man …?", "Das Allzweck-Muster."),
        ],
        "examples": [
            E("Как сказ+ать «ч+ай» по-нем+ецки?", "Wie sagt man »Tee« auf Deutsch?", "So fragst du ein Gegenüber, das Deutsch lernt — und lernst selbst mit."),
            E("Как +это назыв+ается по-р+усски?", "Wie heißt das auf Russisch?", "Gegenstand zeigen, fragen."),
        ],
        "grammar": "Die Formen auf -ся (называется, пишется, произносится) sind das Passiv: 'wird genannt', 'wird geschrieben', 'wird ausgesprochen'. Dieses Muster nutzt du in vielen Alltagsfragen.",
    },
    "d05_konechno": {
        "register": "neutral",
        "summary": "Zustimmung gibt es in Graden: von absolut sicher bis eher unwahrscheinlich. Wer die Skala kennt, kann Gewissheit genau ausdrücken.",
        "variants": [
            V("Безусл+овно!", "Unbedingt! / Ohne Zweifel!", "Förmlich, sehr sicher."),
            V("Разум+еется!", "Selbstverständlich!", "Gehoben, höflich."),
            V("Нав+ерное.", "Wahrscheinlich.", "Wahrscheinlicher als nicht."),
            V("М+ожет быть.", "Vielleicht.", "Offen."),
            V("Вряд ли.", "Eher nicht.", "Unwahrscheinlich."),
        ],
        "examples": [
            E("Ты придёшь? — Кон+ечно!", "Kommst du? — Natürlich!", "Eine klare Zusage."),
            E("Ты придёшь? — Нав+ерное.", "Kommst du? — Wahrscheinlich.", "Offene Zusage."),
            E("Ты придёшь? — Вряд ли.", "Kommst du? — Eher nicht.", "Höfliche Absage."),
        ],
        "grammar": None,
    },
    "d06_kem_ty_rabotaesh": {
        "register": "neutral",
        "summary": "Kem ty rabotaesh? hat einen grammatischen Schatz: den Instrumental. Nach работать steht der Beruf im Instrumental: Я раб+отаю врач+ом (wörtlich 'ich arbeite als Arzt').",
        "variants": [
            V("Кто ты по проф+ессии?", "Was bist du von Beruf?", "Fragt direkt nach dem Beruf."),
            V("Чем ты заним+аешься?", "Was machst du (beruflich)?", "Offener, auch für Studium oder Hobbys."),
            V("Я раб+отаю врач+ом.", "Ich arbeite als Arzt.", "Instrumental auf -ом."),
            V("Я раб+отаю уч+ителем.", "Ich arbeite als Lehrer.", "Instrumental auf -ем."),
            V("Я раб+отаю уч+ительницей.", "Ich arbeite als Lehrerin.", "Weibliche Form im Instrumental: -ей."),
        ],
        "examples": [
            E("Кем ты раб+отаешь? — Я раб+отаю программ+истом.", "Als was arbeitest du? — Ich arbeite als Programmierer.", "Кем ist der Instrumental von кто."),
            E("Я раб+отаю врач+ом.", "Ich arbeite als Arzt.", "Auch eine Frau sagt врачом."),
        ],
        "gender": {
            "title": "Beruf im Instrumental",
            "text": "Männliche Berufe enden im Instrumental auf -ом/-ем (врач → врачом, учитель → учителем), weibliche auf -ой/-ей (учительница → учительницей). Viele Frauen nutzen die männliche Form: Я работаю врачом — das ist völlig normal.",
        },
        "grammar": "Instrumental = Wer? Womit? Als wer? Endungen: -ом/-ем (m, n), -ой/-ей (f), -ами/-ями (Mehrzahl). Nach работать und быть steht der Beruf im Instrumental.",
    },
    "d06_eto_interesno": {
        "register": "neutral",
        "summary": "+Это интер+есно ('das ist interessant') und Мне интер+есно ('ich bin interessiert') klingen ähnlich, sagen aber Verschiedenes: das erste beschreibt die Sache, das zweite dein Gefühl.",
        "variants": [
            V("Мне интер+есно.", "Ich bin interessiert / Mich interessiert es.", "Dein Gefühl, mit Dativ мне."),
            V("+Это увлек+ательно!", "Das ist fesselnd!", "Stärker als интересно."),
            V("+Это ск+учно.", "Das ist langweilig.", "Umgekehrt."),
            V("+Это +очень интер+есно!", "Das ist sehr interessant!", "Mit очень verstärkt."),
        ],
        "examples": [
            E("+Это интер+есно! Расскаж+и ещё.", "Das ist interessant! Erzähl mehr.", "Schönes Echo auf die Erzählung des anderen."),
            E("Мне интер+есно, как +это раб+отает.", "Mich interessiert, wie das funktioniert.", "Мне + Zustand zeigt dein Interesse."),
        ],
        "grammar": "+Это интер+есно beschreibt die Sache (+это = das). Мне интер+есно beschreibt dein Gefühl (Dativ мне). Dieselbe Idee gilt für скучно, весело, страшно.",
    },
    "d07_u_menya_est_brat": {
        "register": "neutral",
        "summary": "Familienwörter werden schnell vielfältig: ст+арший und мл+адший zeigen, wer älter oder jünger ist. Das Adjektiv richtet sich nach dem Geschlecht des Wortes.",
        "variants": [
            V("У мен+я есть ст+арший брат.", "Ich habe einen älteren Bruder.", "ст+арший = älterer (m)."),
            V("У мен+я есть ст+аршая сестр+а.", "Ich habe eine ältere Schwester.", "ст+аршая = ältere (f)."),
            V("У мен+я есть мл+адший брат.", "Ich habe einen jüngeren Bruder.", "мл+адший = jüngerer."),
            V("У мен+я есть дво+юродный брат.", "Ich habe einen Cousin.", "Wörtlich 'doppelt-geborener Bruder'."),
        ],
        "examples": [
            E("У мен+я есть ст+арший брат и мл+адшая сестр+а.", "Ich habe einen älteren Bruder und eine jüngere Schwester.", "Брат ist männlich → старший. Сестра ist weiblich → младшая."),
            E("Мо+я ст+аршая сестр+а раб+отает в Берл+ине.", "Meine ältere Schwester arbeitet in Berlin.", "Alles in weiblicher Form: моя, старшая, сестра."),
        ],
        "grammar": "Adjektive passen sich an: ст+арший брат (m), ст+аршая сестр+а (f), ст+арший реб+ёнок (m, Kind). Endungen -ый/-ий (m), -ая (f), -ое (n).",
    },
    "d08_klassno": {
        "register": "locker · jung",
        "summary": "Begeisterung hat viele Stufen und Töne. Кл+ассно, Кр+уто und Прик+ольно klingen jung und locker; Прекр+асно und Замеч+ательно sind gehobener.",
        "variants": [
            V("Кл+ассно!", "Klasse!", "Locker, überall."),
            V("Кр+уто!", "Krass! / Cool!", "Jugendsprache."),
            V("Прик+ольно!", "Witzig! / Cool!", "Sehr jung, spielerisch."),
            V("Прекр+асно!", "Wunderbar!", "Gehoben, höflich."),
            V("Потряс+ающе!", "Atemberaubend!", "Starke Begeisterung."),
        ],
        "examples": [
            E("Мы идём в кин+о! — Кр+уто!", "Wir gehen ins Kino! — Cool!", "Kurz und begeistert."),
            E("+Это прекр+асная ид+ея!", "Das ist eine wunderbare Idee!", "Прекрасная ist ein Adjektiv (weiblich, passt zu идея)."),
        ],
        "grammar": None,
    },
    "d09_vkusno": {
        "register": "neutral · herzlich",
        "summary": "Wer isst, verdient ein Kompliment. Das macht Gastgeber glücklich — und es gibt sehr schöne Ausdrücke dafür.",
        "variants": [
            V("+Очень вк+усно!", "Sehr lecker!", "Der Klassiker."),
            V("Объед+ение!", "Ein Gedicht! / Köstlich!", "Sehr begeistert, fast schwärmerisch."),
            V("П+альчики обл+ижешь!", "Zum Finger-Ablecken!", "Eine bildhafte Redewendung — lecker!"),
            V("Спас+ибо, б+ыло +очень вк+усно.", "Danke, es war sehr lecker.", "Beim Abschied vom Essen."),
            V("Невк+усно.", "Nicht lecker.", "Höflich lieber vermeiden."),
        ],
        "examples": [
            E("Вк+усно! Кто +это пригот+овил?", "Lecker! Wer hat das gekocht?", "Ein Kompliment mit Neugier."),
            E("Объед+ение! Спас+ибо!", "Köstlich! Danke!", "Doppelt freundlich."),
        ],
        "grammar": None,
    },
    "d09_priyatnogo_appetita": {
        "register": "höflich · warm",
        "summary": "Bei Tisch gibt es feste Formeln. Wer sie kennt, wirkt sofort höflich und herzlich — besonders bei Einladungen.",
        "variants": [
            V("При+ятного аппет+ита!", "Guten Appetit!", "Zu allen am Tisch."),
            V("Спас+ибо, вза+имно!", "Danke, gleichfalls!", "Die höfliche Antwort."),
            V("Н+а здор+овье!", "Gern geschehen! (nach dem Essen)", "Antwort auf Спасибо за обед."),
            V("Будь как д+ома!", "Fühl dich wie zu Hause!", "Gastgeber-Satz."),
        ],
        "examples": [
            E("При+ятного аппет+ита! — Спас+ибо, вза+имно!", "Guten Appetit! — Danke, gleichfalls!", "Ein kleines Ritual."),
            E("Спас+ибо за +ужин, б+ыло вк+усно!", "Danke für das Abendessen, es war lecker!", "За + Akkusativ = 'für'."),
        ],
        "grammar": "Wünsche stehen im Genitiv: приятного аппетита, здоровья. Dahinter steht die Idee '(ich wünsche dir) …'.",
    },
    "d09_za_zdorovye": {
        "register": "festlich",
        "summary": "Toasts sind in Russland wichtig und werden mit Gefühl gesprochen. Jede Runde hat ihr Thema: Gesundheit, Begegnung, Liebe, Frieden.",
        "variants": [
            V("За встр+ечу!", "Auf das Wiedersehen!", "Auf eure Begegnung."),
            V("За люб+овь!", "Auf die Liebe!", "Sehr emotional."),
            V("Б+удем здор+овы!", "Bleiben wir gesund!", "Wörtlich 'Lasst uns gesund sein!'"),
            V("В+аше здор+овье!", "Auf Ihr Wohl!", "Förmlich, mit Respekt."),
            V("За вас!", "Auf Sie / euch!", "Kurz und herzlich."),
        ],
        "examples": [
            E("За здор+овье! За встр+ечу!", "Auf die Gesundheit! Auf das Wiedersehen!", "Mehrere Toasts nacheinander."),
            E("За теб+я! Б+удем здор+овы!", "Auf dich! Bleiben wir gesund!", "Persönlich und warm."),
        ],
        "grammar": "За + Akkusativ = 'auf, für': за здоровье, за встречу, за тебя. Der Toast sagt, worauf getrunken wird.",
    },
    "d09_schyot": {
        "register": "höflich · praktisch",
        "summary": "Am Tisch fertig? Mit diesen Sätzen bezahlst du wie ein Muttersprachler — ob zusammen, getrennt oder mit Karte.",
        "variants": [
            V("М+ожно счёт?", "Kann ich die Rechnung haben?", "Höflich, mit М+ожно."),
            V("Принес+ите, пож+алуйста, счёт.", "Bringen Sie bitte die Rechnung.", "Etwas förmlicher."),
            V("Отд+ельно, пож+алуйста.", "Getrennt, bitte.", "Wenn jeder für sich bezahlt."),
            V("Вм+есте.", "Zusammen.", "Eine gemeinsame Rechnung."),
            V("М+ожно к+артой?", "Kann ich mit Karte zahlen?", "Karten sind überall üblich."),
            V("Сд+ачи не н+адо.", "Stimmt so (kein Wechselgeld).", "Trinkgeld geben."),
        ],
        "examples": [
            E("М+ожно счёт? — Вм+есте +или отд+ельно?", "Kann ich die Rechnung haben? — Zusammen oder getrennt?", "Eine typische Rückfrage."),
            E("Отд+ельно. М+ожно к+артой?", "Getrennt. Kann ich mit Karte zahlen?", "Schnell und klar."),
        ],
        "grammar": "Картой ist der Instrumental von карта ('mit der Karte'): bezahlen mit = Instrumental ohne Präposition.",
    },
    "d10_kotoryy_chas": {
        "register": "neutral",
        "summary": "Die Uhrzeit ist ein Alltagsklassiker. Wichtig: час, часа, часов — dieselbe Regel wie bei год, года, лет.",
        "variants": [
            V("Ск+олько вр+емени?", "Wie viel Uhr ist es? (wörtlich 'Wie viel Zeit?')", "Sehr üblich im Alltag."),
            V("Вы не подск+ажете, кот+орый час?", "Können Sie mir sagen, wie spät es ist?", "Höflich, an Fremde."),
            V("Сейч+ас три час+а.", "Es ist drei Uhr.", "Nach 2–4 steht часа."),
            V("Сейч+ас пять час+ов.", "Es ist fünf Uhr.", "Ab 5 steht часов."),
            V("Сейч+ас од+ин час.", "Es ist ein Uhr.", "Eins: час."),
        ],
        "examples": [
            E("Кот+орый час? — Сейч+ас пять час+ов.", "Wie spät ist es? — Es ist fünf Uhr.", "Часов nach Zahlen ab 5."),
            E("Вы не подск+ажете, кот+орый час?", "Können Sie mir sagen, wie spät es ist?", "Подсказать = 'einen Hinweis geben' — sehr höflich."),
        ],
        "grammar": "Zahlen 2–4 + Nomen → Genitiv Singular (часа), ab 5 → Genitiv Plural (часов). Das ist dieselbe Regel wie bei рубль, год, минута.",
    },
    "d10_kakaya_pogoda": {
        "register": "neutral",
        "summary": "Das Wetter ist ein Standard-Gesprächsthema. Russen sagen oft, was passiert (идёт дождь) oder wie es ist (х+олодно).",
        "variants": [
            V("Хор+ошая пог+ода.", "Schönes Wetter.", "Positives Wetter."),
            V("Плох+ая пог+ода.", "Schlechtes Wetter.", "Negatives Wetter."),
            V("Идёт снег.", "Es schneit.", "Wörtlich 'Der Schnee geht'."),
            V("Св+етит с+олнце.", "Die Sonne scheint.", "Das л in солнце wird nicht gesprochen."),
            V("Сег+одня п+асмурно.", "Heute ist es bedeckt.", "Graue Wolken."),
        ],
        "examples": [
            E("Как+ая сег+одня пог+ода? — Сег+одня х+олодно и идёт снег.", "Wie ist das Wetter heute? — Heute ist es kalt und es schneit.", "Mit и zwei Beobachtungen verbinden."),
            E("Хор+ошая пог+ода! Дав+ай гул+ять!", "Schönes Wetter! Lass uns spazieren gehen!", "Das Wetter als Anlass."),
        ],
        "grammar": "Идёт дождь / идёт снег: Die Verben gehen mit Niederschlag (идти). Wettersätze ohne Subjekt: Хол+одно, Тепл+о, Жарко.",
    },
    "d11_vo_skolko": {
        "register": "neutral",
        "summary": "Um Zeiten zu vereinbaren, nutzt du в + Zahl (в семь), +около + Genitiv ('gegen') oder р+овно ('punkt').",
        "variants": [
            V("В семь.", "Um sieben.", "Standard."),
            V("Р+овно в семь.", "Punkt sieben.", "Genau."),
            V("+Около сем+и.", "Gegen sieben.", "Ungefähr, mit Genitiv."),
            V("К сем+и.", "Bis sieben / zu sieben.", "Wenn du bis dahin da sein sollst."),
        ],
        "examples": [
            E("Во ск+олько встр+етимся? — В семь.", "Um wie viel Uhr treffen wir uns? — Um sieben.", "Во сколько = 'um wie viel (Uhr)'."),
            E("Я б+уду р+овно в семь.", "Ich bin punkt sieben da.", "Б+уду = Zukunft von быть ('ich werde sein')."),
        ],
        "grammar": "В + Akkusativ der Uhrzeit: в семь, в восемь. +Около + Genitiv: около семи. К + Dativ: к семи ('bis'). Dieselben Präpositionen zeigen Zeit und Ort.",
    },
    "d11_dogovorilis": {
        "register": "locker",
        "summary": "Abmachungen werden mit kurzen, kräftigen Ausrufen besiegelt. Je nach Ton klingen sie freundlich, entschlossen oder festlich.",
        "variants": [
            V("Идёт!", "Abgemacht! / Gilt!", "Locker."),
            V("По рук+ам!", "Handschlag drauf!", "Sinnbild des Versprechens."),
            V("Решен+о!", "Beschlossen!", "Entschieden."),
            V("Отл+ичная ид+ея!", "Tolle Idee!", "Zustimmung zum Plan."),
            V("Почем+у бы и нет?", "Warum eigentlich nicht?", "Offen, spielerisch."),
        ],
        "examples": [
            E("Дав+ай встр+етимся з+автра! — Договор+ились!", "Lass uns morgen treffen! — Abgemacht!", "Договорились = 'wir haben uns geeinigt'."),
            E("Пойдём в каф+е? — Почем+у бы и нет?", "Gehen wir ins Café? — Warum nicht?", "Eine einladende Zustimmung."),
        ],
        "grammar": "Договор+ились ist die Vergangenheit (Mehrzahl): 'wir haben uns geeinigt'. Das ли markiert die Mehrzahl und gilt für alle Geschlechter.",
    },
    "d12_pochemu": {
        "register": "neutral",
        "summary": "Russisch hat drei Fragewörter für 'warum': Почем+у (allgemein, Grund), Зач+ем (wozu, Ziel), Отчег+о (aus welchem Anlass, oft emotional).",
        "variants": [
            V("Зач+ем?", "Wozu? / Weshalb?", "Fragt nach dem Zweck."),
            V("Отчег+о?", "Weshalb? / Wieso?", "Fragt nach dem Anlass, oft gefühlsbetont."),
            V("Почем+у?", "Warum?", "Der Standard."),
            V("Из-за чег+о?", "Wegen was?", "Fragt, was der Auslöser war."),
        ],
        "examples": [
            E("Почем+у ты гр+устный?", "Warum bist du traurig?", "Neutrale Frage nach dem Grund."),
            E("Зач+ем ты +это д+елаешь?", "Wozu machst du das?", "Fragt nach dem Ziel."),
            E("Отчег+о ты пл+ачешь?", "Weshalb weinst du?", "Fühlbarer, nah."),
        ],
        "grammar": "Почему → Ursache (потому что). Зачем → Ziel (чтобы). Отчего → Anlass (оттого что). So wählst du die Frage, die zur Antwort passt.",
    },
    "d12_nikogda_ne_byl": {
        "register": "neutral",
        "summary": "Erfahrungen erzählt man mit никогд+а не (nie), ещё не (noch nicht) und уж+е (schon). Die Vergangenheit richtet sich nach deinem Geschlecht: был (m) / была (f).",
        "variants": [
            V("Я ещё не был в Росс+ии.", "Ich war noch nicht in Russland. (Mann)", "Noch nicht, aber vielleicht bald."),
            V("Я ещё не был+а в Росс+ии.", "Ich war noch nicht in Russland. (Frau)", "Weibliche Form."),
            V("Я уж+е был в Москв+е.", "Ich war schon in Moskau. (Mann)", "Schon passiert."),
            V("Ты когд+а-нибудь был в Росс+ии?", "Warst du schon mal in Russland? (zu einem Mann)", "Frage nach Erfahrung."),
        ],
        "examples": [
            E("Я никогд+а не был в Росс+ии.", "Ich war noch nie in Russland.", "Doppelte Verneinung: никогда + не."),
            E("Ты когд+а-нибудь был в Берл+ине?", "Warst du schon mal in Berlin?", "Когда-нибудь = jemals."),
        ],
        "gender": {
            "title": "был / была",
            "text": "Die Vergangenheit von быть: Mann был, Frau была (Betonung am Ende: был+а), Mehrzahl были. Wer fragt, richtet das Verb nach dem Gegenüber: Ты был (zu einem Mann) / Ты была (zu einer Frau).",
        },
        "grammar": "Ort: в + Präpositiv: в Росс+ии, в Берл+ине. Verneinung: никогд+а + не (doppelt, Pflicht).",
    },
    "d13_nichego_strashnogo": {
        "register": "locker · tröstend",
        "summary": "Wenn jemand sich entschuldigt oder etwas schiefgeht, hilft ein kurzer, warmer Satz. Diese Antworten nehmen Druck raus — und machen Gespräche leicht.",
        "variants": [
            V("Всё в пор+ядке.", "Alles in Ordnung.", "Beruhigend, neutral."),
            V("Не стр+ашно.", "Nicht schlimm.", "Kurz."),
            V("Быв+ает.", "Kann passieren.", "Locker und verständnisvoll."),
            V("Ерунд+а!", "Quatsch! / Halb so wild!", "Sehr locker, unter Freunden."),
            V("Н+е за что.", "Keine Ursache.", "Auf Entschuldigung oder Dank."),
        ],
        "examples": [
            E("Извин+и! — Ерунд+а, быв+ает.", "Entschuldige! — Halb so wild, kann passieren.", "Zwei lockere Antworten."),
            E("Всё в пор+ядке? — Да, всё в пор+ядке.", "Alles in Ordnung? — Ja, alles in Ordnung.", "Frage und Bestätigung."),
        ],
        "grammar": None,
    },
    "d13_mne_zhal": {
        "register": "persönlich · mitfühlend",
        "summary": "Mitgefühl zeigst du mit wenigen, ehrlichen Sätzen. Dabei unterscheiden sich Männer und Frauen in der Anrede: Б+едный (zu einem Mann) / Б+едная (zu einer Frau).",
        "variants": [
            V("Мне +очень жаль.", "Es tut mir sehr leid.", "Aufrichtiges Bedauern."),
            V("Соч+увствую.", "Ich fühle mit dir. (förmlich-warm)", "Wirkt anteilnehmend, etwas gehoben."),
            V("Б+едный!", "Du Armer!", "Zu einem Mann."),
            V("Б+едная!", "Du Arme!", "Zu einer Frau."),
            V("Держ+ись!", "Halt durch!", "Ermutigung, sehr vertraut."),
        ],
        "examples": [
            E("Мне +очень жаль. Я с тоб+ой.", "Es tut mir sehr leid. Ich bin bei dir.", "Mitgefühl + Nähe."),
            E("Б+едная! Держ+ись!", "Du Arme! Halt durch!", "Zu einer Frau."),
        ],
        "gender": {
            "title": "Б+едный / Б+едная",
            "text": "Wenn du jemanden bemitleidest, richtet sich das Adjektiv nach dem Geschlecht der Person: Б+едный (m), Б+едная (f), Б+едные (pl). Dasselbe gilt für Anreden wie Дорог+ой / Дорог+ая.",
            "forms": [
                V("Б+едный!", "Du Armer!", "Zu einem Mann."),
                V("Б+едная!", "Du Arme!", "Zu einer Frau."),
            ],
        },
        "grammar": None,
    },
    "d13_udachi": {
        "register": "herzlich",
        "summary": "Wünsche stehen im Genitiv, Glückwünsche mit с + Instrumental: Уд+ачи! (Genitiv) aber С днём рожд+ения! (с + Instrumental). So erkennst du beide Typen.",
        "variants": [
            V("Усп+ехов!", "Viel Erfolg!", "Genitiv Plural."),
            V("Счастл+ивого пут+и!", "Gute Reise!", "Wörtlich 'glücklichen Weg'."),
            V("Выздор+авливай!", "Gute Besserung!", "Befehl: werde gesund."),
            V("С днём рожд+ения!", "Alles Gute zum Geburtstag!", "С + Instrumental für Glückwünsche."),
            V("С пр+аздником!", "Frohes Fest!", "Zu Feiertagen."),
        ],
        "examples": [
            E("Уд+ачи! Усп+ехов!", "Viel Glück! Viel Erfolg!", "Zwei Wünsche im Genitiv."),
            E("С днём рожд+ения! Всег+о хор+ошего!", "Alles Gute zum Geburtstag! Alles Gute!", "Glückwunsch + Wunsch."),
        ],
        "grammar": "Wünsche (Genitiv): удачи, успехов, счастья, здоровья. Glückwünsche (с + Instrumental): с днём рождения, с праздником, с Новым годом.",
    },
    "d13_ty_horosho_govorish": {
        "register": "neutral · bescheiden",
        "summary": "Auf ein Kompliment antwortest du nicht mit 'Danke, ich weiß', sondern mit Bescheidenheit — das gehört zur russischen Höflichkeit. Wer gelobt wird, spielt es oft herunter.",
        "variants": [
            V("Н+у что ты!", "Ach was!", "Bescheidene Abwehr."),
            V("Спас+ибо, я ещё уч+усь.", "Danke, ich lerne noch.", "Dankbar und lernbereit."),
            V("Мне при+ятно.", "Das freut mich.", "Ehrlich dankbar."),
            V("Ты мен+я смущ+аешь.", "Du bringst mich in Verlegenheit.", "Scherzhaft-verlegen."),
            V("У теб+я отл+ичный р+усский.", "Du hast ein ausgezeichnetes Russisch.", "Ein Kompliment mit Nomen."),
        ],
        "examples": [
            E("Ты хорош+о говор+ишь по-р+усски! — Спас+ибо, я ещё уч+усь.", "Du sprichst gut Russisch! — Danke, ich lerne noch.", "Die beste Antwort."),
            E("У теб+я отл+ичный р+усский! — Н+у что ты!", "Dein Russisch ist ausgezeichnet! — Ach was!", "Bescheidenheit als Höflichkeit."),
        ],
        "grammar": "Bei хорош+о говор+ишь ist хорош+о ein Adverb (wie?). Bei отл+ичный р+усский ist отл+ичный ein Adjektiv (welcher?) — beides loben, aber unterschiedlich gebaut.",
    },
    "d14_bylo_priyatno": {
        "register": "höflich · warm",
        "summary": "Б+ыло при+ятно ('es war angenehm') ist unpersönlich und bleibt immer sächlich — egal wer spricht. Р+ад был dagegen richtet sich nach deinem Geschlecht.",
        "variants": [
            V("Р+ад был познак+омиться.", "Ich habe mich gefreut, dich kennenzulernen. (Mann)", "Männlich."),
            V("Р+ада был+а познак+омиться.", "Ich habe mich gefreut, dich kennenzulernen. (Frau)", "Weiblich, beide Wörter in Frauenform."),
            V("Спас+ибо за встр+ечу!", "Danke für das Treffen!", "Freundlich zum Abschied."),
            V("Б+ыло зд+орово!", "Es war toll!", "Locker und ehrlich."),
            V("Спас+ибо за в+ечер!", "Danke für den Abend!", "Nach einer Einladung."),
        ],
        "examples": [
            E("Б+ыло +очень при+ятно познак+омиться!", "Es war sehr schön, dich kennenzulernen!", "Gleich für alle Geschlechter."),
            E("Спас+ибо за в+ечер! Б+ыло зд+орово!", "Danke für den Abend! Es war toll!", "Zwei warme Sätze."),
        ],
        "gender": {
            "title": "Б+ыло bleibt gleich — р+ад ändert sich",
            "text": "Б+ыло при+ятно ist unpersönlich: immer sächlich (было), egal ob du Mann oder Frau bist. Р+ад был (m) / Р+ада был+а (f) dagegen richtet sich nach dir. Merke: Unpersönliche Sätze (было, надо, можно) kennen kein Geschlecht.",
        },
        "grammar": None,
    },
    "d14_uvidimsya": {
        "register": "locker",
        "summary": "Abschiede mit Plan klingen besser als nur 'Tschüss': Du sagst, wann ihr euch wiederseht — und freust dich darauf.",
        "variants": [
            V("До встр+ечи!", "Bis bald!", "Ohne festen Termin."),
            V("До з+автра!", "Bis morgen!", "Mit festem Termin."),
            V("До понед+ельника!", "Bis Montag!", "До + Genitiv."),
            V("До ск+орого!", "Bis bald!", "Herzlich."),
            V("Созвон+имся!", "Wir telefonieren!", "Locker, unter Freunden."),
        ],
        "examples": [
            E("Ув+идимся в п+ятницу! До п+ятницы!", "Wir sehen uns Freitag! Bis Freitag!", "В + Akkusativ für Zeit, до + Genitiv für 'bis'."),
            E("Созвон+имся в выходн+ые! Пок+а!", "Wir telefonieren am Wochenende! Tschüss!", "Ein lockerer Abschied."),
        ],
        "grammar": "До + Genitiv: до завтра, до встречи, до понедельника. В + Akkusativ für Wochentage: в пятницу, в субботу.",
    },
    "d14_beregi_sebya": {
        "register": "herzlich · nah",
        "summary": "Sorge um andere ausdrücken ist ein Zeichen von Nähe. Russen verabschieden sich oft mit einem Wunsch für das Wohlergehen des anderen.",
        "variants": [
            V("Будь остор+ожен!", "Sei vorsichtig! (zu einem Mann)", "Männlich."),
            V("Будь остор+ожна!", "Sei vorsichtig! (zu einer Frau)", "Weiblich."),
            V("Не бол+ей!", "Werd nicht krank!", "Fürsorglich."),
            V("В+ыспись!", "Schlaf dich aus!", "Praktischer Wunsch."),
            V("Отдых+ай!", "Ruh dich aus!", "Freundlicher Rat."),
        ],
        "examples": [
            E("Берег+и себ+я! Будь остор+ожен!", "Pass auf dich auf! Sei vorsichtig!", "Zu einem Mann."),
            E("Берег+и себ+я! Будь остор+ожна!", "Pass auf dich auf! Sei vorsichtig!", "Zu einer Frau."),
        ],
        "gender": {
            "title": "Будь остор+ожен / остор+ожна",
            "text": "Das Kurzadjektiv richtet sich nach dem Angesprochenen: остор+ожен (m), остор+ожна (f). Die Betonung wandert bei der Frauenform ans Ende — hör dir beides an.",
        },
        "grammar": "Будь + Kurzadjektiv ist der Befehl 'sei …': Будь здор+ов (m) / здор+ова (f), Будь счастл+ив (m) / счастл+ива (f).",
    },
    "d14_mozhno_tvoy_nomer": {
        "register": "locker · höflich",
        "summary": "Kontakte austauschen ist heute selbstverständlich. Je nach Nähe wählst du ты (твой) oder вы (ваш) und eine passende Form.",
        "variants": [
            V("М+ожно ваш н+омер?", "Kann ich Ihre Nummer haben?", "Höflich, an Fremde."),
            V("Дашь мне св+ой н+омер?", "Gibst du mir deine Nummer?", "Locker, unter Gleichaltrigen."),
            V("Ты есть в Телегр+аме?", "Bist du auf Telegram?", "Heute der übliche Weg."),
            V("Дав+ай обмен+яемся конт+актами!", "Lass uns Kontakte austauschen!", "Freundlich, ohne Druck."),
            V("Напиш+у теб+е.", "Ich schreibe dir.", "Zum Abschluss."),
        ],
        "examples": [
            E("М+ожно твой н+омер? — Кон+ечно, запиш+и.", "Kann ich deine Nummer haben? — Klar, schreib auf.", "Запиши = 'schreib auf'."),
            E("Ты есть в Телегр+аме? Напиш+и мне!", "Bist du auf Telegram? Schreib mir!", "Kurz und modern."),
        ],
        "grammar": None,
    },
    "d14_priezzhay": {
        "register": "herzlich",
        "summary": "Einladungen gehören zur russischen Gastfreundschaft. Приезж+ай lädt jemanden von weit her ein, Приход+и zu dir nach Hause, Заход+и auf einen kurzen Besuch.",
        "variants": [
            V("Приход+и в г+ости!", "Komm zu Besuch!", "Du lädst jemanden zu dir ein."),
            V("Заход+и!", "Komm vorbei!", "Locker, kurz."),
            V("Добр+о пож+аловать!", "Herzlich willkommen!", "Beim Empfang."),
            V("Будь как д+ома!", "Fühl dich wie zu Hause!", "Gastgeber-Satz."),
            V("Мы теб+я ждём.", "Wir erwarten dich.", "Warm und verbindlich."),
        ],
        "examples": [
            E("Приезж+ай в Герм+анию! Мы теб+я ждём.", "Komm nach Deutschland! Wir erwarten dich.", "Приезжать = anreisen (mit Verkehrsmittel)."),
            E("Приход+и в г+ости! Будь как д+ома.", "Komm zu Besuch! Fühl dich wie zu Hause.", "Gastfreundschaft in zwei Sätzen."),
        ],
        "grammar": "Приезж+ай (anreisen, mit Verkehrsmittel) vs Приход+и (zu Fuß kommen). В + Akkusativ nach Richtung: в Герм+анию, в Москв+у.",
    },
}
