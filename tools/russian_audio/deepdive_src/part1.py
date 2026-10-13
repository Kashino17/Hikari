"""Deepdive-Inhalte Teil 1: Begrüßen, Vorstellen, Wie geht's, Zahlen/Alter, Verstehen."""


def V(ru, de, use):
    return {"ru": ru, "de": de, "use": use}


def E(ru, de, why):
    return {"ru": ru, "de": de, "why": why}


ENTRIES = {
    "d01_privet": {
        "register": "locker · unter Freunden",
        "summary": "Das lockere Hallo für Freunde, Gleichaltrige und Kollegen auf Augenhöhe. Bei Behörden, am Flughafen, bei Älteren oder Vorgesetzten ist es zu salopp — dort greifst du zu Здр+авствуйте.",
        "variants": [
            V("Здр+авствуй!", "Hallo (du, freundlich-neutral)", "Einen Tick förmlicher als Привет, aber immer noch per du. Passt, wenn du jemanden länger nicht gesehen hast."),
            V("Здр+авствуйте!", "Guten Tag (Sie / ihr)", "Die sichere Wahl bei Behörden, am Flughafen, im Hotel, beim Arzt, beim Chef — überall, wo du siezt. Auch an mehrere Personen gerichtet."),
            V("Д+обрый день!", "Guten Tag!", "Höflich und immer richtig: Service, Telefon, E-Mails, Läden."),
            V("Зд+орово!", "Na, du! / Servus!", "Sehr salopp, kumpelhaft, vor allem unter Männern."),
            V("Прив+етик!", "Hallöchen!", "Verspielt und herzlich, oft unter Freundinnen oder in Chats."),
            V("Прив+етствую!", "Ich begrüße Sie / dich", "Wirkt förmlich-geschäftlich, gern am Anfang einer Nachricht oder Rede."),
        ],
        "examples": [
            E("Прив+ет, +Анна! К+ак д+ела?", "Hallo Anna! Wie geht's?", "Name direkt dahinter, genau wie im Deutschen. Danach kommt fast immer sofort Как дела? — so beginnt ein Gespräch."),
            E("Здр+авствуйте! Я хоч+у к+офе.", "Guten Tag! Ich möchte einen Kaffee.", "Siezen und gleich bestellen: Я хочу + Wunsch. К+офе ändert sich nie (unveränderliches Fremdwort)."),
            E("Д+оброе +утро, +Анна Ив+ановна!", "Guten Morgen, Anna Iwanowna!", "Vorname + Vatersname (Отчество) ist die respektvolle Anrede für Ältere und Vorgesetzte. Der Vatersname endet bei Frauen auf -овна/-евна."),
        ],
        "grammar": "Ты (du) und вы (Sie/ihr) bestimmen die ganze Begrüßung: Привет und Здравствуй gehören zu ты, Здравствуйте zu вы. Wer vy sagt, endet Befehle auf -ите: Повторите, Говорите.",
    },
    "d01_zdravstvuyte": {
        "register": "höflich · förmlich",
        "summary": "Das Standard-Hallo für alle, die du siezt: Behörden, Flughafen, Hotel, Arzt, Chef, ältere Menschen. Es ist neutral-höflich und nie falsch.",
        "variants": [
            V("Прив+ет!", "Hallo (locker)", "Nur unter Freunden und Gleichaltrigen — nicht bei Grenzkontrolle oder Amt."),
            V("Д+обрый день!", "Guten Tag!", "Etwas wärmer als Здравствуйте, ebenso höflich — gut in Läden und am Telefon."),
            V("Д+оброе +утро!", "Guten Morgen!", "Bis etwa 11 Uhr, überall zulässig."),
            V("Д+обрый в+ечер!", "Guten Abend!", "Ab dem späten Nachmittag."),
            V("Прош+у прощ+ения!", "Ich bitte um Verzeihung!", "Sehr förmlich, um Aufmerksamkeit zu bekommen oder sich zu entschuldigen."),
        ],
        "examples": [
            E("Здр+авствуйте! Как вас зов+ут?", "Guten Tag! Wie heißen Sie?", "Вас ist der Akkusativ von вы — die Sie-Form. Merke: Как вас зовут (Sie) und Как тебя зовут (du)."),
            E("Здр+авствуйте! Говор+ите м+едленнее, пож+алуйста.", "Guten Tag! Sprechen Sie bitte langsamer.", "Говор+ите ist der höfliche Befehl: -ите macht aus 'sprich' ein 'sprechen Sie'. Das rettet dich am Flughafen und bei Ämtern."),
            E("Здр+авствуйте! Ск+олько ст+оит +это?", "Guten Tag! Wie viel kostet das?", "Ск+олько ст+оит? — wörtlich 'Wie viel steht (es)?'. Eine Standardfrage im Laden."),
        ],
        "grammar": "Das в in Здравствуйте sprichst du nicht: »sdrástwujte«. Gegenüber mehreren Personen — auch Freunden — sagst du ebenfalls Здравствуйте, weil es die Mehrzahl ist.",
    },
    "d01_dobroe_utro": {
        "register": "neutral",
        "summary": "Warum Д+оброе +утро, aber Д+обрый день? Weil das Adjektiv sich dem Geschlecht des Nomens anpasst: утро ist sächlich (доброе), день und вечер sind männlich (добрый), ночь ist weiblich (доброй ночи).",
        "variants": [
            V("Д+обрый день!", "Guten Tag!", "Tagsüber, etwa 12 bis 17 Uhr."),
            V("Д+обрый в+ечер!", "Guten Abend!", "Ab dem späten Nachmittag."),
            V("Д+оброй н+очи!", "Gute Nacht! (auch: Hallo mitten in der Nacht)", "Weibliches Nomen → -ой. Als Abschied sagt man eher Спок+ойной н+очи."),
            V("С д+обрым +утром!", "Mit einem guten Morgen! (herzlich-verspielt)", "Wärmer als das einfache Д+оброе +утро, gern von Freunden und Familie."),
        ],
        "examples": [
            E("Д+оброе +утро! К+офе +или чай?", "Guten Morgen! Kaffee oder Tee?", "+или heißt 'oder'. Ein typischer Morgensatz bei Gastgebern."),
            E("Д+обрый в+ечер! Как д+ела?", "Guten Abend! Wie geht's?", "Beides gemischt: Gruß + Standardfrage. So klingst du sofort natürlich."),
        ],
        "grammar": "Endungen: -ое (sächlich, утро), -ый (männlich, день/вечер), -ая (weiblich, ночь im Nominativ: добрая). In Wünschen steht der Genitiv: Доброй ночи = '(ich wünsche dir) eine gute Nacht'.",
    },
    "d01_spasibo": {
        "register": "neutral",
        "summary": "Danke gibt es in vielen Stufen — von lässig bis feierlich. Und die richtige Antwort darauf hängt davon ab, ob du duzt oder siezt.",
        "variants": [
            V("Спас+ибо больш+ое!", "Vielen Dank!", "Immer passend, auch im Restaurant und am Amt."),
            V("Спас+ибо вам!", "Danke Ihnen!", "Вам = Dativ von вы: 'Danke an Sie'. Höflich-persönlich."),
            V("Спас+ибо теб+е!", "Danke dir!", "Теб+е = Dativ von ты: 'Danke an dich'. Unter Freunden."),
            V("Благодар+ю!", "Ich danke (Ihnen)!", "Gehoben, förmlich — in Briefen, Reden, bei Respektspersonen."),
            V("Мерс+и!", "Merci!", "Scherzhaft-locker unter Freunden; kommt aus dem Französischen."),
            V("Спас+ибо за п+омощь!", "Danke für die Hilfe!", "За + Akkusativ = 'für'. Perfekt, wenn dir jemand geholfen hat."),
        ],
        "examples": [
            E("Спас+ибо! — Пож+алуйста!", "Danke! — Bitte!", "Пож+алуйста ist die Standardantwort auf Спасибо."),
            E("Спас+ибо, +очень помогл+о.", "Danke, das hat sehr geholfen.", "Пом+огло ist sächlich (es hat geholfen) — Verben in der Vergangenheit folgen dem Geschlecht."),
            E("Спас+ибо теб+е за всё!", "Danke dir für alles!", "За всё = 'für alles'. Warm und persönlich — passt zu Freunden und Familie."),
        ],
        "grammar": "Спасибо ist eigentlich 'Gott rette (dich)' — Спаси Бог. Darum steht die Person, der du dankst, im Dativ: Спасибо вам, Спасибо тебе.",
    },
    "d01_pozhaluysta": {
        "register": "neutral",
        "summary": "Пож+алуйста ist doppelt belegt: als Bitte ('bitte') und als Antwort auf Danke ('gern geschehen'). Beim Reichen eines Gegenstands bedeutet es 'bitte sehr'.",
        "variants": [
            V("Н+е за что!", "Keine Ursache!", "Wörtlich 'Wofür nicht'. Locker-warme Antwort auf Спасибо."),
            V("Не ст+оит благод+арности!", "Das ist keinen Dank wert!", "Höflich-bescheiden, ein wenig gehoben."),
            V("В+от, пож+алуйста.", "Hier, bitte.", "Beim Reichen von etwas — в+от heißt 'hier / da'."),
            V("Прош+у.", "Bitte (sehr).", "Förmlich; z. B. wenn dich jemand hereinbittet oder an den Tisch."),
        ],
        "examples": [
            E("Повтор+ите, пож+алуйста.", "Wiederholen Sie bitte.", "Als Bitte hinter dem Befehl: ein Muster für fast jede höfliche Aufforderung."),
            E("Пож+алуйста, проход+ите.", "Bitte, kommen Sie herein / gehen Sie durch.", "Einladung in höflichem Ton; -ите ist die Sie-Form des Befehls."),
        ],
        "grammar": None,
    },
    "d01_izvinite": {
        "register": "höflich",
        "summary": "Извин+ите ist das Allzweck-Entschuldigung: Aufmerksamkeit bekommen, vorbeigehen wollen, einen kleinen Fehler eingestehen. Für echte Schuld reicht Прости/Простите oder ein ganzer Satz.",
        "variants": [
            V("Извин+и!", "Entschuldige!", "Das Du: unter Freunden, bei Kleinigkeiten."),
            V("Прост+и!", "Verzeih!", "Persönlicher, bei echter Schuld oder in Beziehungen."),
            V("Прост+ите!", "Verzeihen Sie!", "Höflich und gewichtiger als Извините."),
            V("Прош+у прощ+ения.", "Ich bitte um Verzeihung.", "Sehr förmlich, in offiziellen Situationen."),
            V("Мне +очень жаль.", "Es tut mir sehr leid.", "Zeigt echtes Bedauern — nicht nur Höflichkeit."),
        ],
        "examples": [
            E("Извин+ите, где туал+ет?", "Entschuldigung, wo ist die Toilette?", "Извините als Türöffner für jede Frage an Fremde."),
            E("Извин+и, я опозд+ал.", "Entschuldige, ich bin zu spät (männlich).", "Das Verb in der Vergangenheit bekommt das Geschlecht des Sprechers."),
            E("Извин+и, я опозд+ала.", "Entschuldige, ich bin zu spät (weiblich).", "Frau: -а an das Verb. Merke: опозд+ал / опозд+ала."),
        ],
        "gender": {
            "title": "Vergangenheit: männlich oder weiblich",
            "text": "In der Vergangenheit endet das Verb auf -л (Mann), -ла (Frau) oder -ло (sächlich). Ein Mann sagt Я опозд+ал, eine Frau Я опозд+ала. Das gilt für fast jedes Verb: Я пон+ял / Я поняла, Я устал / Я устала.",
        },
        "grammar": None,
    },
    "d02_ty": {
        "register": "locker (ты) · förmlich (вы)",
        "summary": "Ты ist das Du, вы das Sie — und gleichzeitig das Ihr (Mehrzahl). Fremde, Ältere, Behörden, Chefs: вы. Freunde, Familie, Kinder, Gleichaltrige in lockerer Runde: ты. Ein Wechsel zum Du wird oft ausdrücklich angeboten.",
        "variants": [
            V("М+ожно на ты?", "Können wir uns duzen?", "Die höfliche Bitte um das Du — fast immer wird sie mit Кон+ечно! beantwortet."),
            V("Дав+ай на ты!", "Lass uns duzen!", "Das Angebot selbst — du bietest das Du an."),
            V("Дав+айте на вы.", "Bleiben wir beim Sie.", "Eine höfliche Distanz-Wahrung, etwa im Beruf."),
            V("Вы", "Sie / ihr", "Die Form bleibt immer gleich, ob du eine Person siezt oder mehrere ansprichst."),
        ],
        "examples": [
            E("Как теб+я зов+ут?", "Wie heißt du?", "Теб+я = Akkusativ von ты (dich) — das Du-Pendant zu Как вас зовут."),
            E("Как вас зов+ут?", "Wie heißen Sie?", "Вас = Akkusativ von вы — siezend und höflich."),
            E("М+ожно на ты? — Кон+ечно!", "Können wir uns duzen? — Klar!", "Ein kleines Gespräch, das den Wechsel zeigt."),
        ],
        "grammar": "Aus ты wird je nach Fall: тебя (Akkusativ), тебе (Dativ). Aus вы: вас, вам. Die Mehrzahl und das Sie sind in der Form identisch — der Zusammenhang sagt dir, wer gemeint ist.",
    },
    "d02_menya_zovut": {
        "register": "neutral",
        "summary": "Wörtlich 'Mich nennen sie Kadir' — Russen sagen nicht 'Ich heiße', sondern 'Mich rufen sie'. Мен+я ist der Akkusativ von я, und зов+ут (sie rufen) braucht kein Subjekt.",
        "variants": [
            V("Я Кад+ир.", "Ich bin Kadir.", "Kürzer und lockerer; unter Jüngeren völlig normal."),
            V("Моё +имя — Кад+ир.", "Mein Name ist Kadir.", "Förmlich-gehoben, wie bei einer Vorstellung."),
            V("Дав+айте познак+омимся!", "Lernen wir uns kennen!", "Höfliche Aufforderung: Давайте = 'lassen Sie uns' (förmlich)."),
            V("Мен+я зов+ут Кад+ир, а вас?", "Ich heiße Kadir, und Sie?", "А вас? gibt den Ball zurück — höflich im Du-Fall mit А тебя?."),
        ],
        "examples": [
            E("Мен+я зов+ут +Анна.", "Ich heiße Anna.", "Name im Nominativ am Satzende, genauso wie bei einem Mann."),
            E("Мен+я зов+ут Ив+ан Петр+ович.", "Ich heiße Iwan Petrowitsch.", "Formell nennt man Vorname + Vatersname — hier 'Sohn von Pjotr'."),
        ],
        "gender": {
            "title": "Namen: Vatersname und Nachname haben eine weibliche Form",
            "text": "Der Vatersname (Отчество) und der Nachname ändern sich je nach Geschlecht: Ив+ан Петр+ович (er) — +Анна Петр+овна (sie, 'Tochter von Pjotr'). Nachnamen mit -ов/-ев bekommen bei Frauen ein -а: Иван+ов / Иван+ова, Смирн+ов / Смирн+ова. Wer das weiß, spricht Frauen wie Männer richtig an.",
            "forms": [
                V("Петр+ович / Петр+овна", "Sohn / Tochter von Pjotr", "Vatersname, männlich -ович, weiblich -овна."),
                V("Иван+ов / Иван+ова", "Nachname Iwanow, er / sie", "Bei Frauen wird ein -а angehängt."),
            ],
        },
        "grammar": None,
    },
    "d02_ya_iz_germanii": {
        "register": "neutral",
        "summary": "Herkunft wird mit из + Genitiv gesagt (Я из Герм+ании), der Wohnort mit в + Präpositiv (Я жив+у в Герм+ании). Dazu kommen Nationalitäten, und die haben männliche und weibliche Formen.",
        "variants": [
            V("Я р+одом из Герм+ании.", "Ich komme ursprünglich aus Deutschland.", "р+одом = 'von Geburt'. Gut, wenn du schon lange woanders lebst."),
            V("Я н+емец.", "Ich bin Deutscher.", "Männlich. Die Frau sagt Я н+емка."),
            V("Я н+емка.", "Ich bin Deutsche.", "Weiblich. Männlich: н+емец."),
            V("Я р+усский / р+усская.", "Ich bin Russe / Russin.", "Das ist ein Adjektiv: р+усский (männlich), р+усская (weiblich)."),
            V("Я из Берл+ина.", "Ich komme aus Berlin.", "Städte nach из stehen im Genitiv: Берлин → Берлина."),
        ],
        "examples": [
            E("Отк+уда ты? — Я из Берл+ина.", "Woher kommst du? — Aus Berlin.", "Отк+уда fragt nach der Herkunft; die Antwort steht mit из + Genitiv."),
            E("Я жив+у в Берл+ине.", "Ich wohne in Berlin.", "В + Präpositiv (-е am Ende) für 'wo'."),
        ],
        "gender": {
            "title": "Nationalitäten: männlich und weiblich",
            "text": "Viele Nationen haben zwei Formen: н+емец / н+емка, англич+анин / англич+анка. Adjektive passen sich an: р+усский / р+усская, нем+ецкий / нем+ецкая. Wer sich selbst vorstellt, nimmt die Form seines Geschlechts.",
            "forms": [
                V("Я н+емец.", "Ich bin Deutscher (Mann).", "Männliche Form."),
                V("Я н+емка.", "Ich bin Deutsche (Frau).", "Weibliche Form."),
            ],
        },
        "grammar": "из + Genitiv: Германия → из Германии, Россия → из России, Берлин → из Берлина. в + Präpositiv: в Германии, в России, в Берлине.",
    },
    "d03_kak_dela": {
        "register": "neutral",
        "summary": "Как д+ела? ist die Standardfrage beim Treffen, wörtlich 'Wie (sind die) Sachen?'. Dazu gehören viele Varianten — locker, vertraut oder förmlich.",
        "variants": [
            V("Как ты?", "Wie geht's dir?", "Vertraut, kurz."),
            V("Как ж+изнь?", "Wie läuft's (im Leben)?", "Locker unter Freunden."),
            V("Как пожив+аете?", "Wie geht es Ihnen?", "Höflich, etwas gehoben (Sie-Form)."),
            V("Как у вас д+ела?", "Wie geht es Ihnen?", "Standard-höflich: у вас = 'bei Ihnen'."),
            V("Что н+ового?", "Was gibt's Neues?", "Beliebt unter Bekannten, die sich länger nicht gesehen haben."),
            V("Как настро+ение?", "Wie ist deine Stimmung?", "Fragt nach der Laune — persönlicher und warm."),
        ],
        "examples": [
            E("Как у теб+я д+ела?", "Wie geht es dir?", "У теб+я = 'bei dir'. У + Genitiv ist das russische Muster für 'bei jemandem'."),
            E("Как у вас д+ела?", "Wie geht es Ihnen?", "Dasselbe in der Sie-Form."),
            E("Как ж+изнь? — Ничег+о.", "Wie läuft's? — Nicht schlecht.", "Ничег+о bedeutet im Alltag 'nicht schlecht, geht'."),
        ],
        "grammar": "Д+ела ist die Mehrzahl von д+ело (Sache). У + Genitiv drückt Besitz und Ort aus: у меня (bei mir), у тебя (bei dir), у вас (bei Ihnen).",
    },
    "d03_horosho": {
        "register": "neutral",
        "summary": "Antworten auf Как дела? sind eine Skala. Wer sie kennt, kann seine Stimmung genau treffen — von begeistert bis müde.",
        "variants": [
            V("Отл+ично!", "Super!", "Sehr gut, mit Schwung."),
            V("Прекр+асно!", "Wunderbar!", "Gehoben, warm."),
            V("Замеч+ательно!", "Ausgezeichnet!", "Wirkt begeistert."),
            V("Хорош+о.", "Gut.", "Normal-positiv."),
            V("Норм+ально.", "Ganz okay.", "Neutral, gelassen."),
            V("Ничег+о.", "Geht so / nicht schlecht.", "Typisch russisch: bescheiden positiv."),
            V("Так себ+е.", "Naja, so lala.", "Nicht berauschend."),
            V("Не +очень.", "Nicht so gut.", "Freundlich-ehrlich: nicht schlecht, nicht gut."),
            V("Пл+охо.", "Schlecht.", "Ehrlich — Russen fragen dann oft nach: Что случ+илось? (Was ist passiert?)"),
        ],
        "examples": [
            E("Спас+ибо, хорош+о. А у теб+я?", "Danke, gut. Und bei dir?", "Zuerst bedanken, dann zurückfragen: А у теб+я? — der Standardzug."),
            E("Не +очень. Я уст+ал.", "Nicht so gut. Ich bin müde.", "Erst die Stimmung, dann der Grund — ehrlich und kurz."),
            E("Отл+ично! Всё хорош+о.", "Super! Alles gut.", "Mit Ausrufezeichen und einem Zusatz klingst du begeisterter."),
        ],
        "grammar": "Diese Wörter sind Adverbien auf -о (хорошо, плохо, нормально). Sie beschreiben Zustand und Wie-Fragen, nicht Nomen — darum ändern sie sich nie.",
    },
    "d03_ya_ustal": {
        "register": "neutral",
        "summary": "Ich bin müde — ein Satz, an dem du Geschlecht in der Vergangenheit lernst. Ein Mann sagt уст+ал, eine Frau уст+ала. Dasselbe Prinzip gilt für fast jedes Verb in der Vergangenheit.",
        "variants": [
            V("Я +очень уст+ал / уст+ала.", "Ich bin sehr müde.", "+Очень intensiviert."),
            V("Я совс+ем без сил.", "Ich habe überhaupt keine Kraft mehr.", "Без сил = 'ohne Kräfte'. Drastischer."),
            V("Я уст+ал как соб+ака.", "Ich bin hundemüde.", "Wörtlich 'müde wie ein Hund' — ein bekanntes Sprichwort."),
            V("Я хоч+у спать.", "Ich will schlafen.", "Praktisch und direkt."),
        ],
        "examples": [
            E("Я уст+ал.", "Ich bin müde (Mann).", "Endung -л = männlich."),
            E("Я уст+ала.", "Ich bin müde (Frau).", "Endung -ла = weiblich. Beachte: das Verb ändert sich, das 'Ich' я bleibt gleich."),
            E("Мы уст+али.", "Wir sind müde.", "Mehrzahl -ли — gilt für alle, ob Männer oder Frauen."),
        ],
        "gender": {
            "title": "Kurzform und Vergangenheit: männlich, weiblich, Mehrzahl",
            "text": "Verben und Kurzadjektive ändern ihre Endung mit dem Geschlecht des Sprechers: уст+ал (Mann) / уст+ала (Frau) / уст+али (wir/sie). Genauso: Я р+ад / Я р+ада (ich freue mich), Я з+анят / Я занят+а (ich bin beschäftigt), Я согл+асен / Я согл+асна (ich stimme zu).",
            "forms": [
                V("Я уст+ал.", "Ich bin müde (Mann).", "-л"),
                V("Я уст+ала.", "Ich bin müde (Frau).", "-ла"),
            ],
        },
        "grammar": None,
    },
    "d04_mne_tridtsat_let": {
        "register": "neutral",
        "summary": "Das Alter wird nicht mit 'sein' gesagt, sondern mit Dativ: 'Мне тр+идцать лет' = 'Mir (sind) dreißig Jahre'. Dazu kommt die Besonderheit: год, года, лет.",
        "variants": [
            V("Мне дв+адцать од+ин год.", "Ich bin einundzwanzig.", "Nach 1 steht год (Singular)."),
            V("Мне дв+адцать два г+ода.", "Ich bin zweiundzwanzig.", "Nach 2, 3, 4 steht года."),
            V("Мне дв+адцать пять лет.", "Ich bin fünfundzwanzig.", "Ab 5 steht лет (Genitiv Plural)."),
            V("Мне исп+олнилось тр+идцать.", "Ich bin dreißig geworden.", "Zum Geburtstag: 'Mir wurde dreißig voll'."),
            V("Ем+у с+орок лет.", "Er ist vierzig.", "Ем+у = Dativ von он."),
        ],
        "examples": [
            E("Ск+олько теб+е лет?", "Wie alt bist du?", "Теб+е = Dativ von ты, лет = Genitiv Plural. Wörtlich 'Wie viel dir Jahre?'."),
            E("Мне тр+идцать лет.", "Ich bin dreißig.", "Мне = Dativ von я."),
            E("Ей дв+адцать пять лет.", "Sie ist fünfundzwanzig.", "Ей = Dativ von она; пять verlangt лет."),
        ],
        "grammar": "Alter = Dativ der Person + Zahl + год/года/лет. 1 → год, 2–4 → года, 5–20 → лет. (21 → год, 22 → года, 25 → лет.) Einmal verstanden, ist es leicht.",
    },
    "d05_ya_ne_ponimayu": {
        "register": "neutral",
        "summary": "Ich verstehe nicht — der wichtigste Satz für Anfänger. Mit der Vergangenheit sagst du genauer: Я не п+онял (Mann) / Я не поняла (Frau) = 'ich habe nicht verstanden'.",
        "variants": [
            V("Я не п+онял.", "Ich habe nicht verstanden (Mann).", "Meint den Moment: gerade eben. Betonung: п+онял."),
            V("Я не понял+а.", "Ich habe nicht verstanden (Frau).", "Hinweis: in der Frauenform springt die Betonung auf das Ende (поняла)."),
            V("Что вы сказ+али?", "Was haben Sie gesagt?", "Höflich und klar."),
            V("Извин+ите, я не рассл+ышал.", "Entschuldigung, ich habe es nicht gehört (Mann).", "Wenn es an der Lautstärke lag. Frau: рассл+ышала."),
            V("М+ожно ещё раз?", "Geht es noch einmal?", "Eine freundliche Bitte statt Befehl."),
        ],
        "examples": [
            E("Извин+ите, я не п+онял.", "Entschuldigung, ich habe nicht verstanden.", "Mit Извините entschärfst du die Aussage."),
            E("Я не поним+аю по-р+усски.", "Ich verstehe kein Russisch.", "По-р+усски = 'auf Russisch'. Ohne Artikel, ohne Fall."),
            E("Я не поним+аю, но я уч+усь.", "Ich verstehe es nicht, aber ich lerne.", "Mit но (aber) zeigst du Lernbereitschaft — Russen lieben das."),
        ],
        "gender": {
            "title": "Vergangenheit: Betonung wandert",
            "text": "Bei manchen Verben verschiebt sich die Betonung mit dem Geschlecht: п+онял (er) — понял+а (sie) — п+оняли (wir). Hör genau hin: der Klang des Satzes ändert sich, und das ist keine Ausnahme — es ist normal.",
        },
        "grammar": None,
    },
    "d05_povtorite": {
        "register": "höflich",
        "summary": "Повтор+ите ist der höfliche Befehl (Sie-Form). Aus ты-Befehlen wird mit -ите ein Sie-Befehl: повтор+и (du) → повтор+ите (Sie).",
        "variants": [
            V("Повтор+и, пож+алуйста.", "Wiederhole bitte.", "Du-Form unter Freunden."),
            V("Скаж+ите ещё раз, пож+алуйста.", "Sagen Sie es bitte noch einmal.", "Höflich und sehr verbreitet."),
            V("М+ожно ещё раз?", "Kann man das noch mal?", "Locker-freundlich."),
            V("Не могл+и бы вы повтор+ить?", "Könnten Sie es wiederholen?", "Besonders höflich — dank могл+и бы ('könnten')."),
        ],
        "examples": [
            E("Повтор+ите, пож+алуйста, м+едленно.", "Wiederholen Sie bitte, langsam.", "Медл+енно ist 'langsam' (Adverb). Hilfreich, wenn jemand zu schnell redet."),
            E("Говор+ите, пож+алуйста, м+едленнее.", "Sprechen Sie bitte langsamer.", "М+едленнее ist der Komparativ — 'langsamer'."),
        ],
        "grammar": "Sie-Befehl: Stamm + -ите (повтор+ить → повтор+ите, говор+ить → говор+ите). Du-Befehl: Stamm + -и (повтор+и, говор+и).",
    },
}
