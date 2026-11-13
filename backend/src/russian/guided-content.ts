// Anjas geführte Gespräche für die ersten Stufen — von Hand geschrieben statt vom Sprachmodell erzeugt.
//
// Warum: Ein lokales Modell kann mit einem Dutzend erlaubter Wörter kein Gespräch führen. Es
// wiederholte Fragen, plapperte Antworten nach und brauchte dafür 20–40 s je Zug (Kadir, 14.11.2026).
// Hier ist jede Frage neu, jede Reaktion bezieht sich auf die Antwort, und jedes Wort ist erklärt.
//
// Regel für neue Inhalte: Jede Zeile nennt in `vocab` ALLE Wörter, die nicht in BASE stehen — auch
// solche, die frühere Schritte schon brachten. Die Wortkarte zeigt davon nur, was der Lernende noch
// nicht kennt. Der Test guided.test.ts prüft das für jede Zeile.

export type Line = { ru: string; de: string };

export type Option = {
  /** "yes" · "no" · "any" (jede Antwort passt) · Wortanfänge, an denen die Wahl zu erkennen ist. */
  when: "yes" | "no" | "any" | string[];
  /** Beispielantwort, die die Hilfe zeigt. */
  example: Line;
  /** Anjas Reaktion — steht vor ihrer nächsten Frage. */
  react: Line;
  /** Wörter in Beispiel und Reaktion, die nicht in BASE stehen. */
  vocab?: Line[];
  /** Für das Lob: »Du hast gesagt, …«. */
  says: string;
};

export type Beat = {
  /** Thema — ein Gespräch fragt jedes Thema nur einmal. */
  key: string;
  ask: Line;
  /** Dieselbe Frage einfacher (für »Einfacher fragen«). */
  simple?: Line;
  /** Deutscher Hinweis auf der Karte »Erst in Ruhe anschauen«. */
  cue: string;
  /** Wörter in Frage und Satzanfang, die nicht in BASE stehen. */
  vocab?: Line[];
  starter?: string;
  options: Option[];
};

/** Die Wörter der vier Mini-Gespräche — sie gelten als bekannt. */
export const BASE =
  "привет чай да спасибо кофе нет пожалуйста или я люблю ты а как дела хорошо нормально";

// ── Wörter, die mehrfach vorkommen ──────────────────────────────────────────
const w = (ru: string, de: string): Line => ({ ru, de });
const TOZHE = w("тоже", "auch");
const NE = w("не", "nicht");
const I = w("и", "und");
const OCHEN = w("очень", "sehr");
const ETO = w("это", "das");
const CHTO = w("что", "was");
const LJUBISH = w("любишь", "du magst");
const BUDU = w("буду", "ich nehme");
const BUDESH = w("будешь", "du nimmst");
const HOCHU = w("хочу", "ich möchte");
const HOCHESH = w("хочешь", "du möchtest");
const PJU = w("пью", "ich trinke");
const PJOSH = w("пьёшь", "du trinkst");
const EM = w("ем", "ich esse");
const ESH = w("ешь", "du isst");
const PONYATNO = w("понятно", "verstehe");
const OTLICHNO = w("отлично", "super");
const OJ = w("ой", "oh");
const VKUSNO = w("вкусно", "lecker");
const DOMA = w("дома", "zu Hause");
const SEGODNYA = w("сегодня", "heute");
const UTROM = w("утром", "morgens");
const VECHEROM = w("вечером", "abends");
const UTRO = w("утро", "Morgen");
const VECHER = w("вечер", "Abend");
const KINO = w("кино", "Kino · Filme");
const KAFE = w("кафе", "Café");
const MUZYKU = w("музыку", "Musik (nach »ich mag«)");
const MOLOKO = w("молоко", "Milch");
const SUP = w("суп", "Suppe");
const TORT = w("торт", "Kuchen · Torte");
const SOK = w("сок", "Saft");
const SAHAR = w("сахар", "Zucker");
const VODU = w("воду", "Wasser (nach »ich trinke«)");
const HLEB = w("хлеб", "Brot");
const SYR = w("сыр", "Käse");

const yes = (example: Line, react: Line, says: string, vocab: Line[] = []): Option => ({
  when: "yes",
  example,
  react,
  says,
  vocab,
});
const no = (example: Line, react: Line, says: string, vocab: Line[] = []): Option => ({
  when: "no",
  example,
  react,
  says,
  vocab,
});
const pick = (
  when: string[],
  example: Line,
  react: Line,
  says: string,
  vocab: Line[] = [],
): Option => ({ when, example, react, says, vocab });
const NOT_REALLY = w("Нет, не очень.", "Nein, nicht so sehr.");

const PRAVDA = w("правда", "wirklich");
const KNIGI = w("книги", "Bücher");
const SALAT = w("салат", "Salat");
const U_TEBYA_EST = w("у тебя есть", "du hast");
const U_MENYA_EST = w("у меня есть", "ich habe");
const GOVORISH = w("говоришь", "du sprichst");
const V_GORODE = w("в городе", "in der Stadt");
const ZHIVU = w("живу", "ich wohne");
const RABOTAYU = w("работаю", "ich arbeite");
const TEPLO = w("тепло", "warm");
const HOLODNO = w("холодно", "kalt");
const MYASO = w("мясо", "Fleisch");
const RYBU = w("рыбу", "Fisch (nach »ich esse«)");
const PIT = w("пить", "trinken");
const S_SAHAROM = w("с сахаром", "mit Zucker");
const LETO = w("лето", "Sommer");
const ZIMU = w("зиму", "Winter (nach »ich mag«)");

/** Anja reagiert nicht immer gleich — drei Tonlagen, damit zehn »Magst du …?« nicht wie ein Fragebogen klingen. */
type Tone = 0 | 1 | 2;

/** »Magst du X?« — mit eigener Reaktion auf Ja und Nein. */
function likes(key: string, thing: Line, de: string, lead = "Ты любишь", tone: Tone = 0): Beat {
  const agree = (
    [
      { ru: `Я тоже люблю ${thing.ru}!`, de: `Ich mag auch ${de}!`, vocab: [thing, TOZHE] },
      { ru: "Отлично! Я тоже.", de: "Super! Ich auch.", vocab: [OTLICHNO, TOZHE] },
      {
        ru: `Правда? Я тоже люблю ${thing.ru}!`,
        de: `Wirklich? Ich mag auch ${de}!`,
        vocab: [PRAVDA, thing, TOZHE],
      },
    ] as const
  )[tone];
  const differ = (
    [
      {
        ru: `Понятно. А я люблю ${thing.ru}.`,
        de: `Verstehe. Und ich mag ${de}.`,
        vocab: [PONYATNO, thing],
      },
      {
        ru: `Правда? А я очень люблю ${thing.ru}.`,
        de: `Wirklich? Und ich mag ${de} sehr.`,
        vocab: [PRAVDA, OCHEN, thing],
      },
      { ru: `Ой! А я люблю ${thing.ru}.`, de: `Oh! Und ich mag ${de}.`, vocab: [OJ, thing] },
    ] as const
  )[tone];
  return {
    key,
    ask: { ru: `${lead} ${thing.ru}?`, de: `Magst du ${de}?` },
    cue: `Magst du ${de}? Ja oder nein.`,
    vocab: [LJUBISH, thing],
    options: [
      no(NOT_REALLY, { ru: differ.ru, de: differ.de }, `dass du ${de} nicht so magst`, [
        NE,
        OCHEN,
        ...differ.vocab,
      ]),
      yes(
        { ru: `Да, я люблю ${thing.ru}.`, de: `Ja, ich mag ${de}.` },
        { ru: agree.ru, de: agree.de },
        `dass du ${de} magst`,
        [thing, ...agree.vocab],
      ),
    ],
  };
}

/** »Machst du gern X?« — люблю mit Grundform. `de`: Frage, du-Form und ich-Form auf Deutsch. */
function likesDoing(
  key: string,
  verb: Line,
  de: { ask: string; du: string; ich: string },
  lead = "Ты любишь",
): Beat {
  return {
    key,
    ask: { ru: `${lead} ${verb.ru}?`, de: de.ask },
    cue: `${de.ask} Ja oder nein.`,
    vocab: [LJUBISH, verb],
    options: [
      no(
        NOT_REALLY,
        { ru: `Понятно. А я люблю ${verb.ru}.`, de: `Verstehe. Und ich ${de.ich} gern.` },
        `dass du nicht so gern ${de.du}`,
        [NE, OCHEN, PONYATNO, verb],
      ),
      yes(
        { ru: `Да, я люблю ${verb.ru}.`, de: `Ja, ich ${de.ich} gern.` },
        { ru: `Я тоже люблю ${verb.ru}!`, de: `Ich ${de.ich} auch gern!` },
        `dass du gern ${de.du}`,
        [verb, TOZHE],
      ),
    ],
  };
}

// ── Einzelne Themen (stehen in SHARED und zum Teil auch im Anfang eines Szenarios) ──
const WOHNEN: Beat = {
  key: "wohnen",
  ask: { ru: "Ты живёшь в городе?", de: "Wohnst du in der Stadt?" },
  cue: "Wohnst du in der Stadt? Ja oder nein.",
  vocab: [w("живёшь", "du wohnst"), V_GORODE],
  options: [
    no(
      w("Нет.", "Nein."),
      { ru: "Понятно. А я живу в городе.", de: "Verstehe. Und ich wohne in der Stadt." },
      "dass du nicht in der Stadt wohnst",
      [PONYATNO, ZHIVU, V_GORODE],
    ),
    yes(
      w("Да, я живу в городе.", "Ja, ich wohne in der Stadt."),
      { ru: "Я тоже живу в городе.", de: "Ich wohne auch in der Stadt." },
      "dass du in der Stadt wohnst",
      [ZHIVU, V_GORODE, TOZHE],
    ),
  ],
};

const SPRACHE: Beat = {
  key: "sprache",
  ask: { ru: "Ты говоришь по-русски?", de: "Sprichst du Russisch?" },
  cue: "Sprichst du schon ein bisschen Russisch?",
  vocab: [GOVORISH, w("по-русски", "auf Russisch")],
  options: [
    pick(
      ["немног", "чуть"],
      w("Да, немного.", "Ja, ein bisschen."),
      { ru: "Отлично! Ты хорошо говоришь.", de: "Super! Du sprichst gut." },
      "dass du ein bisschen Russisch sprichst",
      [w("немного", "ein bisschen"), OTLICHNO, GOVORISH],
    ),
    no(
      w("Нет.", "Nein."),
      { ru: "Правда? Ты хорошо говоришь!", de: "Wirklich? Du sprichst gut!" },
      "dass du noch kein Russisch sprichst",
      [PRAVDA, GOVORISH],
    ),
    yes(
      w("Да.", "Ja."),
      { ru: "Отлично! Ты хорошо говоришь.", de: "Super! Du sprichst gut." },
      "dass du Russisch sprichst",
      [OTLICHNO, GOVORISH],
    ),
  ],
};

const WETTER: Beat = {
  key: "wetter",
  ask: { ru: "Сегодня тепло или холодно?", de: "Ist es heute warm oder kalt?" },
  simple: { ru: "Тепло или холодно?", de: "Warm oder kalt?" },
  cue: "Wie ist es heute bei dir: warm oder kalt?",
  vocab: [SEGODNYA, TEPLO, HOLODNO],
  options: [
    pick(
      ["тепл"],
      w("Тепло.", "Warm."),
      { ru: "Тепло — хорошо!", de: "Warm — schön!" },
      "dass es heute warm ist",
      [TEPLO],
    ),
    pick(
      ["холодн"],
      w("Холодно.", "Kalt."),
      { ru: "Ой! Холодно.", de: "Oh! Kalt." },
      "dass es heute kalt ist",
      [OJ, HOLODNO],
    ),
  ],
};

const JAHRESZEIT: Beat = {
  key: "jahreszeit",
  ask: { ru: "Ты любишь лето или зиму?", de: "Magst du den Sommer oder den Winter?" },
  simple: { ru: "Лето или зима?", de: "Sommer oder Winter?" },
  cue: "Was magst du lieber: Sommer oder Winter?",
  vocab: [LJUBISH, LETO, ZIMU, w("зима", "Winter")],
  starter: "Я люблю …",
  options: [
    pick(
      ["лет"],
      w("Я люблю лето.", "Ich mag den Sommer."),
      { ru: "Я тоже люблю лето!", de: "Ich mag auch den Sommer!" },
      "dass du den Sommer magst",
      [LETO, TOZHE],
    ),
    pick(
      ["зим"],
      w("Я люблю зиму.", "Ich mag den Winter."),
      { ru: "Зиму? А я люблю лето.", de: "Den Winter? Und ich mag den Sommer." },
      "dass du den Winter magst",
      [ZIMU, LETO],
    ),
  ],
};

const FAMILIE: Beat = {
  key: "familie",
  ask: { ru: "У тебя есть брат или сестра?", de: "Hast du einen Bruder oder eine Schwester?" },
  simple: { ru: "Брат или сестра?", de: "Bruder oder Schwester?" },
  cue: "Hast du Geschwister?",
  vocab: [U_TEBYA_EST, w("брат", "Bruder"), w("сестра", "Schwester")],
  options: [
    // Die Beispielantworten bleiben kurz; »у меня есть« bringt Anja in ihrer Reaktion selbst.
    pick(
      ["брат"],
      w("Брат.", "Einen Bruder."),
      {
        ru: "Брат — хорошо! А у меня есть сестра.",
        de: "Ein Bruder — schön! Und ich habe eine Schwester.",
      },
      "dass du einen Bruder hast",
      [w("брат", "Bruder"), U_MENYA_EST, w("сестра", "Schwester")],
    ),
    pick(
      ["сестр"],
      w("Сестра.", "Eine Schwester."),
      {
        ru: "Сестра — хорошо! У меня тоже есть сестра.",
        de: "Eine Schwester — schön! Ich habe auch eine Schwester.",
      },
      "dass du eine Schwester hast",
      [w("сестра", "Schwester"), U_MENYA_EST, TOZHE],
    ),
    no(
      w("Нет.", "Nein."),
      { ru: "Понятно. А у меня есть сестра.", de: "Verstehe. Und ich habe eine Schwester." },
      "dass du keine Geschwister hast",
      [PONYATNO, U_MENYA_EST, w("сестра", "Schwester")],
    ),
  ],
};

const BUECHER = likes("buecher", KNIGI, "Bücher", "А ты любишь", 2);

const MEDIEN: Beat = {
  key: "medien",
  ask: { ru: "Что ты любишь: кино или книги?", de: "Was magst du lieber: Filme oder Bücher?" },
  simple: { ru: "Кино или книги?", de: "Filme oder Bücher?" },
  cue: "Was magst du lieber: Filme oder Bücher?",
  vocab: [CHTO, LJUBISH, KINO, KNIGI],
  starter: "Я люблю …",
  options: [
    pick(
      ["кино", "фильм"],
      w("Я люблю кино.", "Ich mag Filme."),
      { ru: "Кино! Я тоже люблю кино.", de: "Filme! Ich mag auch Filme." },
      "dass du lieber Filme magst",
      [KINO, TOZHE],
    ),
    pick(
      ["книг"],
      w("Я люблю книги.", "Ich mag Bücher."),
      { ru: "Книги! Я тоже люблю книги.", de: "Bücher! Ich mag auch Bücher." },
      "dass du lieber Bücher magst",
      [KNIGI, TOZHE],
    ),
  ],
};

const FISCH_FLEISCH: Beat = {
  key: "fisch-fleisch",
  ask: { ru: "Ты ешь мясо или рыбу?", de: "Isst du Fleisch oder Fisch?" },
  simple: { ru: "Мясо или рыба?", de: "Fleisch oder Fisch?" },
  cue: "Was isst du: Fleisch oder Fisch?",
  vocab: [ESH, MYASO, RYBU, w("рыба", "Fisch")],
  options: [
    pick(
      ["мяс"],
      w("Я ем мясо.", "Ich esse Fleisch."),
      { ru: "Мясо? А я ем рыбу.", de: "Fleisch? Und ich esse Fisch." },
      "dass du Fleisch isst",
      [EM, MYASO, RYBU],
    ),
    pick(
      ["рыб"],
      w("Я ем рыбу.", "Ich esse Fisch."),
      { ru: "Я тоже ем рыбу.", de: "Ich esse auch Fisch." },
      "dass du Fisch isst",
      [EM, RYBU, TOZHE],
    ),
    no(
      w("Нет.", "Nein."),
      { ru: "Понятно.", de: "Verstehe." },
      "dass du weder Fleisch noch Fisch isst",
      [PONYATNO],
    ),
  ],
};

const SPAZIEREN = likesDoing(
  "spazieren",
  w("гулять", "spazieren gehen"),
  { ask: "Gehst du gern spazieren?", du: "spazieren gehst", ich: "gehe" },
  "А ты любишь",
);
const TANZEN = likesDoing(
  "tanzen",
  w("танцевать", "tanzen"),
  { ask: "Tanzt du gern?", du: "tanzt", ich: "tanze" },
  "А ты любишь",
);
const WALD = likes("wald", w("лес", "Wald"), "den Wald", "А ты любишь");

// ── Themen, die in jedem Gespräch vorkommen können ──────────────────────────
// Reihenfolge mit Absicht: Auf einen Schritt mit vielen neuen Wörtern folgt einer mit wenigen,
// und Fragearten wechseln sich ab (mögen · tun · wählen · haben).
const SHARED: Beat[] = [
  likes("saft", SOK, "Saft", "Ты любишь", 1),
  likes("kino", KINO, "Filme", "А ты любишь"),
  {
    key: "wasser",
    ask: { ru: "Ты пьёшь воду?", de: "Trinkst du Wasser?" },
    cue: "Trinkst du Wasser? Ja oder nein.",
    vocab: [PJOSH, VODU],
    options: [
      no(
        w("Нет.", "Nein."),
        { ru: "Понятно. А я пью воду.", de: "Verstehe. Und ich trinke Wasser." },
        "dass du kein Wasser trinkst",
        [PONYATNO, PJU, VODU],
      ),
      yes(
        w("Да, я пью воду.", "Ja, ich trinke Wasser."),
        { ru: "Я тоже пью воду.", de: "Ich trinke auch Wasser." },
        "dass du Wasser trinkst",
        [PJU, VODU, TOZHE],
      ),
    ],
  },
  {
    key: "morgenkaffee",
    ask: { ru: "Ты пьёшь кофе утром?", de: "Trinkst du morgens Kaffee?" },
    cue: "Trinkst du morgens Kaffee?",
    vocab: [PJOSH, UTROM],
    options: [
      no(
        w("Нет, я пью чай.", "Nein, ich trinke Tee."),
        { ru: "Чай утром — хорошо.", de: "Tee am Morgen — gut." },
        "dass du morgens keinen Kaffee trinkst",
        [PJU, UTROM],
      ),
      yes(
        w("Да, я пью кофе утром.", "Ja, ich trinke morgens Kaffee."),
        { ru: "Я тоже пью кофе утром.", de: "Ich trinke morgens auch Kaffee." },
        "dass du morgens Kaffee trinkst",
        [PJU, UTROM, TOZHE],
      ),
    ],
  },
  {
    key: "brot",
    ask: { ru: "Ты ешь хлеб утром?", de: "Isst du morgens Brot?" },
    simple: { ru: "Ты ешь хлеб?", de: "Isst du Brot?" },
    cue: "Isst du morgens Brot? Ja oder nein.",
    vocab: [ESH, HLEB, UTROM],
    options: [
      no(
        w("Нет.", "Nein."),
        { ru: "Понятно. А я ем хлеб.", de: "Verstehe. Und ich esse Brot." },
        "dass du morgens kein Brot isst",
        [PONYATNO, EM, HLEB],
      ),
      yes(
        w("Да, я ем хлеб.", "Ja, ich esse Brot."),
        { ru: "Я тоже ем хлеб утром.", de: "Ich esse morgens auch Brot." },
        "dass du morgens Brot isst",
        [EM, HLEB, TOZHE, UTROM],
      ),
    ],
  },
  likes("kaese", SYR, "Käse", "А ты любишь", 2),
  {
    key: "heute",
    ask: { ru: "Сегодня ты дома?", de: "Bist du heute zu Hause?" },
    cue: "Bist du heute zu Hause? Ja oder nein.",
    vocab: [SEGODNYA, DOMA],
    options: [
      no(
        w("Нет, я не дома.", "Nein, ich bin nicht zu Hause."),
        { ru: "Понятно. А я сегодня дома.", de: "Verstehe. Und ich bin heute zu Hause." },
        "dass du heute nicht zu Hause bist",
        [NE, DOMA, PONYATNO, SEGODNYA],
      ),
      yes(
        w("Да, я дома.", "Ja, ich bin zu Hause."),
        { ru: "Я тоже дома!", de: "Ich bin auch zu Hause!" },
        "dass du heute zu Hause bist",
        [DOMA, TOZHE],
      ),
    ],
  },
  {
    key: "tageszeit",
    ask: { ru: "Ты любишь утро или вечер?", de: "Magst du den Morgen oder den Abend?" },
    simple: { ru: "Утро или вечер?", de: "Morgen oder Abend?" },
    cue: "Bist du eher ein Morgen- oder ein Abendmensch?",
    vocab: [LJUBISH, UTRO, VECHER],
    starter: "Я люблю …",
    options: [
      pick(
        ["утр"],
        w("Я люблю утро.", "Ich mag den Morgen."),
        { ru: "Утро? А я люблю вечер.", de: "Den Morgen? Und ich mag den Abend." },
        "dass du den Morgen magst",
        [UTRO, VECHER],
      ),
      pick(
        ["вечер"],
        w("Я люблю вечер.", "Ich mag den Abend."),
        { ru: "Я тоже люблю вечер!", de: "Ich mag auch den Abend!" },
        "dass du den Abend magst",
        [VECHER, TOZHE],
      ),
    ],
  },
  {
    key: "haustier",
    ask: { ru: "У тебя есть кот или собака?", de: "Hast du einen Kater oder einen Hund?" },
    simple: { ru: "Кот или собака?", de: "Kater oder Hund?" },
    cue: "Hast du ein Haustier?",
    vocab: [U_TEBYA_EST, w("кот", "Kater"), w("собака", "Hund")],
    options: [
      pick(
        ["кот", "кошк"],
        w("У меня есть кот.", "Ich habe einen Kater."),
        { ru: "Кот — хорошо!", de: "Ein Kater — schön!" },
        "dass du einen Kater hast",
        [U_MENYA_EST, w("кот", "Kater")],
      ),
      pick(
        ["собак"],
        w("У меня есть собака.", "Ich habe einen Hund."),
        { ru: "Собака — хорошо!", de: "Ein Hund — schön!" },
        "dass du einen Hund hast",
        [U_MENYA_EST, w("собака", "Hund")],
      ),
      no(w("Нет.", "Nein."), { ru: "Понятно.", de: "Verstehe." }, "dass du kein Haustier hast", [
        PONYATNO,
      ]),
    ],
  },
  likes("suppe", SUP, "Suppe"),
  {
    key: "abendessen",
    ask: { ru: "Что ты ешь вечером: суп или салат?", de: "Was isst du abends: Suppe oder Salat?" },
    simple: { ru: "Суп или салат?", de: "Suppe oder Salat?" },
    cue: "Was isst du abends? Such dir eins aus.",
    vocab: [CHTO, ESH, VECHEROM, SUP, SALAT],
    options: [
      pick(
        ["суп"],
        w("Я ем суп.", "Ich esse Suppe."),
        { ru: "Суп вечером — хорошо!", de: "Suppe am Abend — gut!" },
        "dass du abends Suppe isst",
        [EM, SUP, VECHEROM],
      ),
      pick(
        ["салат"],
        w("Я ем салат.", "Ich esse Salat."),
        { ru: "Салат? А я ем суп.", de: "Salat? Und ich esse Suppe." },
        "dass du abends Salat isst",
        [EM, SALAT, SUP],
      ),
    ],
  },
  SPAZIEREN,
  WOHNEN,
  likes("sport", w("спорт", "Sport"), "Sport", "А ты любишь", 1),
  WETTER,
  BUECHER,
  MEDIEN,
  {
    key: "arbeit",
    ask: { ru: "Ты сегодня работаешь?", de: "Arbeitest du heute?" },
    cue: "Arbeitest du heute? Ja oder nein.",
    vocab: [SEGODNYA, w("работаешь", "du arbeitest")],
    options: [
      no(
        w("Нет.", "Nein."),
        { ru: "Отлично! А я сегодня работаю.", de: "Super! Und ich arbeite heute." },
        "dass du heute nicht arbeitest",
        [OTLICHNO, SEGODNYA, RABOTAYU],
      ),
      yes(
        w("Да, я работаю.", "Ja, ich arbeite."),
        { ru: "Я тоже работаю сегодня.", de: "Ich arbeite heute auch." },
        "dass du heute arbeitest",
        [RABOTAYU, TOZHE, SEGODNYA],
      ),
    ],
  },
  JAHRESZEIT,
  FAMILIE,
  likesDoing("kochen", w("готовить", "kochen"), {
    ask: "Kochst du gern?",
    du: "kochst",
    ich: "koche",
  }),
  FISCH_FLEISCH,
  SPRACHE,
  likes("schokolade", w("шоколад", "Schokolade"), "Schokolade", "А ты любишь", 2),
  {
    key: "durst",
    ask: { ru: "Ты хочешь пить?", de: "Hast du Durst?" },
    cue: "Hast du gerade Durst? Ja oder nein.",
    vocab: [HOCHESH, PIT],
    options: [
      no(
        w("Нет, спасибо.", "Nein, danke."),
        { ru: "Хорошо. А я хочу пить.", de: "Gut. Und ich habe Durst." },
        "dass du keinen Durst hast",
        [HOCHU, PIT],
      ),
      yes(
        w("Да, я хочу пить.", "Ja, ich habe Durst."),
        { ru: "Я тоже хочу пить.", de: "Ich habe auch Durst." },
        "dass du Durst hast",
        [HOCHU, PIT, TOZHE],
      ),
    ],
  },
  {
    key: "jetzt-tee",
    ask: { ru: "Ты хочешь чай сейчас?", de: "Möchtest du jetzt einen Tee?" },
    cue: "Möchtest du jetzt gerade einen Tee?",
    vocab: [HOCHESH, w("сейчас", "jetzt")],
    options: [
      no(
        w("Нет, спасибо.", "Nein, danke."),
        { ru: "Хорошо. А я хочу чай.", de: "Gut. Und ich möchte einen Tee." },
        "dass du gerade keinen Tee möchtest",
        [HOCHU],
      ),
      yes(
        w("Да, пожалуйста.", "Ja, bitte."),
        { ru: "Я тоже хочу чай!", de: "Ich möchte auch Tee!" },
        "dass du jetzt einen Tee möchtest",
        [HOCHU, TOZHE],
      ),
    ],
  },
  {
    key: "tee-mit-zucker",
    ask: { ru: "Ты пьёшь чай с сахаром?", de: "Trinkst du Tee mit Zucker?" },
    cue: "Trinkst du deinen Tee mit Zucker?",
    vocab: [PJOSH, S_SAHAROM],
    options: [
      no(
        w("Нет.", "Nein."),
        { ru: "Понятно. А я пью чай с сахаром.", de: "Verstehe. Und ich trinke Tee mit Zucker." },
        "dass du Tee ohne Zucker trinkst",
        [PONYATNO, PJU, S_SAHAROM],
      ),
      yes(
        w("Да.", "Ja."),
        { ru: "Я тоже пью чай с сахаром.", de: "Ich trinke Tee auch mit Zucker." },
        "dass du Tee mit Zucker trinkst",
        [PJU, S_SAHAROM, TOZHE],
      ),
    ],
  },
  WALD,
  {
    key: "abends-zuhause",
    ask: { ru: "Вечером ты дома?", de: "Bist du abends zu Hause?" },
    cue: "Bist du abends meistens zu Hause?",
    vocab: [VECHEROM, DOMA],
    options: [
      no(
        w("Нет.", "Nein."),
        { ru: "Понятно. А я вечером дома.", de: "Verstehe. Und ich bin abends zu Hause." },
        "dass du abends nicht zu Hause bist",
        [PONYATNO, VECHEROM, DOMA],
      ),
      yes(
        w("Да, я дома.", "Ja, ich bin zu Hause."),
        { ru: "Я тоже вечером дома.", de: "Ich bin abends auch zu Hause." },
        "dass du abends zu Hause bist",
        [DOMA, TOZHE, VECHEROM],
      ),
    ],
  },
  likesDoing("schlafen", w("спать", "schlafen"), {
    ask: "Schläfst du gern?",
    du: "schläfst",
    ich: "schlafe",
  }),
  TANZEN,
];

// ── Der Anfang jedes Szenarios ──────────────────────────────────────────────
const LIKES_TEA_OR_COFFEE: Beat = {
  key: "tee-kaffee",
  ask: { ru: "Ты любишь чай или кофе?", de: "Magst du Tee oder Kaffee?" },
  simple: { ru: "Чай или кофе?", de: "Tee oder Kaffee?" },
  cue: "Was magst du lieber: Tee oder Kaffee?",
  vocab: [LJUBISH],
  starter: "Я люблю …",
  options: [
    pick(
      ["чай"],
      w("Я люблю чай.", "Ich mag Tee."),
      { ru: "Я тоже люблю чай!", de: "Ich mag auch Tee!" },
      "dass du Tee magst",
      [TOZHE],
    ),
    pick(
      ["коф"],
      w("Я люблю кофе.", "Ich mag Kaffee."),
      { ru: "Кофе? А я люблю чай.", de: "Kaffee? Und ich mag Tee." },
      "dass du Kaffee magst",
    ),
  ],
};

const LIKES_MUSIC: Beat = { ...likes("musik", MUZYKU, "Musik", "А ты любишь") };

const whenOfDay = (lead: Line, extra: Line[] = []): Beat => ({
  key: "wann",
  ask: { ru: `${lead.ru} — утром или вечером?`, de: `${lead.de} — morgens oder abends?` },
  simple: { ru: "Утром или вечером?", de: "Morgens oder abends?" },
  cue: "Wann am liebsten: morgens oder abends?",
  vocab: [UTROM, VECHEROM, ...extra],
  options: [
    pick(
      ["утр"],
      w("Утром.", "Morgens."),
      { ru: "Утром? Хорошо!", de: "Morgens? Gut!" },
      "dass es für dich morgens passt",
      [UTROM],
    ),
    pick(
      ["вечер"],
      w("Вечером.", "Abends."),
      { ru: "Вечером? Хорошо!", de: "Abends? Gut!" },
      "dass es für dich abends passt",
      [VECHEROM],
    ),
  ],
});

const OPENINGS: Record<string, Beat[]> = {
  cafe: [
    {
      // Bestellen ist das Tee-Kaffee-Thema dieses Gesprächs.
      key: "tee-kaffee",
      ask: { ru: "Привет! Чай или кофе?", de: "Hallo! Tee oder Kaffee?" },
      simple: { ru: "Чай или кофе?", de: "Tee oder Kaffee?" },
      cue: "Such dir etwas aus: Tee oder Kaffee.",
      options: [
        pick(
          ["чай"],
          w("Чай, пожалуйста.", "Tee, bitte."),
          { ru: "Чай — хорошо. А я буду кофе.", de: "Tee — gut. Und ich nehme Kaffee." },
          "dass du Tee möchtest",
          [BUDU],
        ),
        pick(
          ["коф"],
          w("Кофе, пожалуйста.", "Kaffee, bitte."),
          { ru: "Кофе — хорошо. А я буду чай.", de: "Kaffee — gut. Und ich nehme Tee." },
          "dass du Kaffee möchtest",
          [BUDU],
        ),
      ],
    },
    {
      key: "zucker",
      ask: { ru: "Ты будешь сахар?", de: "Nimmst du Zucker?" },
      cue: "Möchtest du Zucker dazu? Ja oder nein.",
      vocab: [BUDESH, SAHAR],
      options: [
        no(
          w("Нет, спасибо.", "Nein, danke."),
          { ru: "Хорошо. А я буду сахар.", de: "Gut. Und ich nehme Zucker." },
          "dass du keinen Zucker möchtest",
          [BUDU, SAHAR],
        ),
        yes(
          w("Да, пожалуйста.", "Ja, bitte."),
          { ru: "Я тоже буду сахар.", de: "Ich nehme auch Zucker." },
          "dass du Zucker möchtest",
          [BUDU, TOZHE, SAHAR],
        ),
      ],
    },
    {
      key: "milch",
      ask: { ru: "Ты любишь молоко?", de: "Magst du Milch?" },
      cue: "Magst du Milch? Ja oder nein.",
      vocab: [LJUBISH, MOLOKO],
      options: [
        no(
          w("Нет, не люблю.", "Nein, mag ich nicht."),
          { ru: "Хорошо. А я люблю молоко.", de: "Gut. Und ich mag Milch." },
          "dass du keine Milch magst",
          [NE, MOLOKO],
        ),
        yes(
          w("Да, я люблю молоко.", "Ja, ich mag Milch."),
          { ru: "Я тоже люблю молоко!", de: "Ich mag auch Milch!" },
          "dass du Milch magst",
          [MOLOKO, TOZHE],
        ),
      ],
    },
    {
      key: "essen",
      ask: { ru: "Ты хочешь суп или торт?", de: "Möchtest du Suppe oder Kuchen?" },
      simple: { ru: "Суп или торт?", de: "Suppe oder Kuchen?" },
      cue: "Möchtest du etwas essen? Suppe oder Kuchen.",
      vocab: [HOCHESH, SUP, TORT],
      options: [
        pick(
          ["суп"],
          w("Суп, пожалуйста.", "Suppe, bitte."),
          { ru: "Суп — хорошо! Я тоже буду суп.", de: "Suppe — gut! Ich nehme auch Suppe." },
          "dass du Suppe möchtest",
          [SUP, TOZHE, BUDU],
        ),
        pick(
          ["торт"],
          w("Торт, пожалуйста.", "Kuchen, bitte."),
          { ru: "Торт! Я тоже буду торт.", de: "Kuchen! Ich nehme auch Kuchen." },
          "dass du Kuchen möchtest",
          [TORT, TOZHE, BUDU],
        ),
        no(
          w("Нет, спасибо.", "Nein, danke."),
          { ru: "Хорошо. А я буду торт.", de: "Gut. Und ich nehme Kuchen." },
          "dass du nichts essen möchtest",
          [BUDU, TORT],
        ),
      ],
    },
    {
      key: "lecker",
      ask: { ru: "Это вкусно?", de: "Schmeckt das?" },
      cue: "Schmeckt es dir? Ja oder nein.",
      vocab: [ETO, VKUSNO],
      options: [
        no(NOT_REALLY, { ru: "Ой!", de: "Oh!" }, "dass es dir nicht so schmeckt", [NE, OCHEN, OJ]),
        yes(
          w("Да, очень вкусно!", "Ja, sehr lecker!"),
          { ru: "Отлично!", de: "Super!" },
          "dass es dir schmeckt",
          [OCHEN, VKUSNO, OTLICHNO],
        ),
      ],
    },
    {
      key: "noch-eins",
      ask: { ru: "Ещё чай или кофе?", de: "Noch einen Tee oder Kaffee?" },
      cue: "Möchtest du noch etwas trinken?",
      vocab: [w("ещё", "noch")],
      options: [
        no(
          w("Нет, спасибо.", "Nein, danke."),
          { ru: "Хорошо.", de: "Gut." },
          "dass du nichts mehr möchtest",
        ),
        pick(
          ["чай"],
          w("Да, чай, пожалуйста.", "Ja, Tee, bitte."),
          { ru: "Хорошо! Я тоже буду чай.", de: "Gut! Ich nehme auch Tee." },
          "dass du noch einen Tee möchtest",
          [TOZHE, BUDU],
        ),
        pick(
          ["коф"],
          w("Да, кофе, пожалуйста.", "Ja, Kaffee, bitte."),
          { ru: "Хорошо! Я тоже буду кофе.", de: "Gut! Ich nehme auch Kaffee." },
          "dass du noch einen Kaffee möchtest",
          [TOZHE, BUDU],
        ),
        yes(
          w("Да, пожалуйста.", "Ja, bitte."),
          { ru: "Хорошо! Я тоже буду.", de: "Gut! Ich nehme auch noch etwas." },
          "dass du noch etwas möchtest",
          [TOZHE, BUDU],
        ),
      ],
    },
  ],
  friend: [
    {
      key: "name",
      ask: { ru: "Привет! Я Аня. Как тебя зовут?", de: "Hallo! Ich bin Anja. Wie heißt du?" },
      simple: { ru: "Я Аня. А ты?", de: "Ich bin Anja. Und du?" },
      cue: "Sag, wie du heißt.",
      vocab: [w("Как тебя зовут?", "Wie heißt du?"), w("Меня зовут …", "Ich heiße …")],
      starter: "Меня зовут …",
      options: [
        {
          when: "any",
          example: w("Меня зовут Макс.", "Ich heiße Max."),
          react: w("Очень приятно!", "Freut mich sehr!"),
          vocab: [w("Меня зовут …", "Ich heiße …"), w("Очень приятно!", "Freut mich sehr!")],
          says: "wie du heißt",
        },
        {
          when: "any",
          example: w("Я Анна.", "Ich bin Anna."),
          react: w("Очень приятно!", "Freut mich sehr!"),
          vocab: [w("Очень приятно!", "Freut mich sehr!")],
          says: "wie du heißt",
        },
      ],
    },
    {
      key: "befinden",
      ask: { ru: "Как дела?", de: "Wie geht es dir?" },
      cue: "Wie geht es dir gerade?",
      options: [
        pick(
          ["хорош", "отличн", "супер"],
          w("Хорошо, спасибо.", "Gut, danke."),
          { ru: "Отлично!", de: "Super!" },
          "dass es dir gut geht",
          [OTLICHNO],
        ),
        pick(
          ["нормальн"],
          w("Нормально.", "Ganz okay."),
          { ru: "Понятно.", de: "Verstehe." },
          "dass es dir ganz okay geht",
          [PONYATNO],
        ),
        pick(
          ["плох"],
          w("Плохо.", "Schlecht."),
          { ru: "Ой!", de: "Oh!" },
          "dass es dir nicht gut geht",
          [w("плохо", "schlecht"), OJ],
        ),
        no(
          w("Не очень.", "Nicht so gut."),
          { ru: "Ой!", de: "Oh!" },
          "dass es dir nicht so gut geht",
          [NE, OCHEN, OJ],
        ),
      ],
    },
    LIKES_TEA_OR_COFFEE,
    LIKES_MUSIC,
    {
      key: "was-magst-du",
      ask: { ru: "А что ты любишь?", de: "Und was magst du?" },
      cue: "Erzähl, was du magst — ein Wort reicht.",
      vocab: [CHTO, LJUBISH],
      starter: "Я люблю …",
      options: [
        {
          when: "any",
          example: w("Я люблю кофе.", "Ich mag Kaffee."),
          react: w("Отлично!", "Super!"),
          vocab: [OTLICHNO],
          says: "was du magst",
        },
        {
          when: "any",
          example: w("Я люблю музыку и чай.", "Ich mag Musik und Tee."),
          react: w("Отлично!", "Super!"),
          vocab: [MUZYKU, I, OTLICHNO],
          says: "was du magst",
        },
      ],
    },
    WOHNEN,
    SPRACHE,
  ],
  weekend: [
    {
      key: "plan",
      ask: { ru: "Привет! Кино или кафе?", de: "Hallo! Kino oder Café?" },
      simple: { ru: "Кино или кафе?", de: "Kino oder Café?" },
      cue: "Was wollen wir heute machen: Kino oder Café?",
      vocab: [KINO, KAFE],
      options: [
        pick(
          ["кино"],
          w("Кино!", "Kino!"),
          { ru: "Кино — хорошо! Я люблю кино.", de: "Kino — gut! Ich mag Filme." },
          "dass du ins Kino möchtest",
          [KINO],
        ),
        pick(
          ["кафе"],
          w("Кафе, пожалуйста.", "Café, bitte."),
          { ru: "Кафе — хорошо! Я люблю кафе.", de: "Café — gut! Ich mag Cafés." },
          "dass du ins Café möchtest",
          [KAFE],
        ),
      ],
    },
    whenOfDay(w("Сегодня", "Heute"), [SEGODNYA]),
    SPAZIEREN,
    WETTER,
  ],
  journey: [
    {
      key: "wo",
      ask: { ru: "Привет! Кафе здесь или там?", de: "Hallo! Ist das Café hier oder dort?" },
      simple: { ru: "Здесь или там?", de: "Hier oder dort?" },
      cue: "Zeig Anja den Weg: Ist das Café hier oder dort?",
      vocab: [KAFE, w("здесь", "hier"), w("там", "dort")],
      options: [
        pick(
          ["здесь", "тут"],
          w("Кафе здесь.", "Das Café ist hier."),
          { ru: "Здесь? Спасибо!", de: "Hier? Danke!" },
          "dass das Café hier ist",
          [KAFE, w("здесь", "hier")],
        ),
        pick(
          ["там"],
          w("Кафе там.", "Das Café ist dort."),
          { ru: "Там? Спасибо!", de: "Dort? Danke!" },
          "dass das Café dort ist",
          [KAFE, w("там", "dort")],
        ),
      ],
    },
    {
      key: "weit",
      ask: { ru: "Это далеко?", de: "Ist das weit?" },
      cue: "Ist es weit bis dahin? Ja oder nein.",
      vocab: [ETO, w("далеко", "weit")],
      options: [
        no(
          w("Нет, недалеко.", "Nein, nicht weit."),
          { ru: "Отлично!", de: "Super!" },
          "dass es nicht weit ist",
          [w("недалеко", "nicht weit"), OTLICHNO],
        ),
        yes(w("Да, далеко.", "Ja, weit."), { ru: "Ой!", de: "Oh!" }, "dass es weit ist", [
          w("далеко", "weit"),
          OJ,
        ]),
      ],
    },
    WOHNEN,
    SPRACHE,
  ],
  memory: [
    {
      key: "jahreszeit",
      ask: {
        ru: "Привет! Ты любишь лето или зиму?",
        de: "Hallo! Magst du den Sommer oder den Winter?",
      },
      simple: { ru: "Лето или зима?", de: "Sommer oder Winter?" },
      cue: "Was magst du lieber: Sommer oder Winter?",
      vocab: [
        LJUBISH,
        w("лето", "Sommer"),
        w("зиму", "Winter (nach »ich mag«)"),
        w("зима", "Winter"),
      ],
      starter: "Я люблю …",
      options: [
        pick(
          ["лет"],
          w("Я люблю лето.", "Ich mag den Sommer."),
          { ru: "Я тоже люблю лето!", de: "Ich mag auch den Sommer!" },
          "dass du den Sommer magst",
          [w("лето", "Sommer"), TOZHE],
        ),
        pick(
          ["зим"],
          w("Я люблю зиму.", "Ich mag den Winter."),
          { ru: "Зиму? А я люблю лето.", de: "Den Winter? Und ich mag den Sommer." },
          "dass du den Winter magst",
          [w("зиму", "Winter (nach »ich mag«)"), w("лето", "Sommer")],
        ),
      ],
    },
    likes("meer", w("море", "Meer"), "das Meer"),
    WALD,
    FAMILIE,
  ],
  film: [
    {
      ...likes("kino", KINO, "Filme", "Привет! Ты любишь"),
      ask: { ru: "Привет! Ты любишь кино?", de: "Hallo! Magst du Filme?" },
      simple: { ru: "Ты любишь кино?", de: "Magst du Filme?" },
    },
    whenOfDay(w("Кино", "Filme"), [KINO]),
    BUECHER,
    MEDIEN,
  ],
  music: [
    {
      ...likes("musik", MUZYKU, "Musik", "Привет! Ты любишь"),
      ask: { ru: "Привет! Ты любишь музыку?", de: "Hallo! Magst du Musik?" },
      simple: { ru: "Ты любишь музыку?", de: "Magst du Musik?" },
    },
    whenOfDay(w("Музыка", "Musik"), [w("музыка", "Musik")]),
    TANZEN,
  ],
  choice: [
    {
      key: "tageszeit",
      ask: { ru: "Привет! Утро или вечер?", de: "Hallo! Morgen oder Abend?" },
      simple: { ru: "Утро или вечер?", de: "Morgen oder Abend?" },
      cue: "Bist du eher ein Morgen- oder ein Abendmensch?",
      vocab: [UTRO, VECHER],
      starter: "Я люблю …",
      options: [
        pick(
          ["утр"],
          w("Я люблю утро.", "Ich mag den Morgen."),
          { ru: "Утро? А я люблю вечер.", de: "Den Morgen? Und ich mag den Abend." },
          "dass du den Morgen magst",
          [UTRO, VECHER],
        ),
        pick(
          ["вечер"],
          w("Я люблю вечер.", "Ich mag den Abend."),
          { ru: "Я тоже люблю вечер!", de: "Ich mag auch den Abend!" },
          "dass du den Abend magst",
          [VECHER, TOZHE],
        ),
      ],
    },
    LIKES_TEA_OR_COFFEE,
    MEDIEN,
  ],
};

/** Titel aus der App → Szenario. */
const SCENARIO_BY_TITLE: [string, string][] = [
  ["Ein Café", "cafe"],
  ["Ein neuer Kontakt", "friend"],
  ["Ein schöner freier Tag", "weekend"],
  ["Unterwegs", "journey"],
  ["Eine kleine Geschichte", "memory"],
  ["Nach dem Film", "film"],
  ["Dein Soundtrack", "music"],
  ["Was ist dir wichtig", "choice"],
];

export const SCENARIO_IDS = Object.keys(OPENINGS);

export const MISSIONS: Record<string, string> = {
  cafe: "Bestelle etwas und sag, was du magst.",
  friend: "Stell dich vor und erzähl, was du magst.",
  weekend: "Sag, was du heute machen möchtest.",
  journey: "Hilf Anja, den Weg zu finden.",
  memory: "Erzähl, welche Jahreszeit du magst.",
  film: "Sprich über Filme.",
  music: "Sprich über Musik.",
  choice: "Sag, was dir lieber ist.",
};

export function scenarioId(scenario: string): string {
  return SCENARIO_BY_TITLE.find(([title]) => scenario.startsWith(title))?.[1] ?? "friend";
}

/** Der Gesprächsplan eines Szenarios: erst sein eigener Anfang, dann die übrigen Themen — jedes nur einmal. */
export function planFor(id: string): Beat[] {
  const plan: Beat[] = [];
  const seen = new Set<string>();
  for (const beat of [
    ...(OPENINGS[id] ?? OPENINGS.friend ?? []),
    LIKES_TEA_OR_COFFEE,
    LIKES_MUSIC,
    ...SHARED,
  ]) {
    if (seen.has(beat.key)) continue;
    seen.add(beat.key);
    plan.push(beat);
  }
  return plan;
}
