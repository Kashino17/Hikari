/**
 * Einheitliche Kategorien für alle Quellen. Die Quellen benennen ihre Schlagwörter
 * verschieden (Gutenberg englisch, Wikisource deutsch) — hier wird beides auf
 * dieselben deutschen Kategorien abgebildet.
 */

export const CATEGORIES: {
  id: string;
  label: string;
  group: "belletristik" | "sachbuch" | "weitere";
}[] = [
  { id: "roman", label: "Roman", group: "belletristik" },
  { id: "krimi", label: "Krimi & Mystery", group: "belletristik" },
  { id: "abenteuer", label: "Abenteuer", group: "belletristik" },
  { id: "fantastik", label: "Fantastik & Grusel", group: "belletristik" },
  { id: "historisch", label: "Historischer Roman", group: "belletristik" },
  { id: "liebe", label: "Liebe", group: "belletristik" },
  { id: "humor", label: "Humor", group: "belletristik" },
  { id: "erzaehlungen", label: "Erzählungen", group: "belletristik" },
  { id: "maerchen", label: "Märchen & Sagen", group: "belletristik" },
  { id: "drama", label: "Drama", group: "belletristik" },
  { id: "lyrik", label: "Lyrik", group: "belletristik" },
  { id: "kinder", label: "Kinder & Jugend", group: "belletristik" },
  { id: "geschichte", label: "Geschichte", group: "sachbuch" },
  { id: "philosophie", label: "Philosophie", group: "sachbuch" },
  { id: "wissenschaft", label: "Wissenschaft & Natur", group: "sachbuch" },
  { id: "politik", label: "Politik & Gesellschaft", group: "sachbuch" },
  { id: "reisen", label: "Reisen", group: "sachbuch" },
  { id: "biografie", label: "Biografien & Briefe", group: "sachbuch" },
  { id: "religion", label: "Religion", group: "sachbuch" },
  { id: "kunst", label: "Kunst & Musik", group: "sachbuch" },
  { id: "psychologie", label: "Psychologie", group: "sachbuch" },
  { id: "weitere", label: "Weitere", group: "weitere" },
];

const RULES: [string, RegExp][] = [
  [
    "krimi",
    /detective|mystery|mysteries|crime|criminal|murder|kriminal|krimi|detektiv|thriller|spionage|spy stories/i,
  ],
  ["abenteuer", /adventure|abenteuer|sea stories|western stories|pirat|seefahrt|indianer/i],
  [
    "fantastik",
    /fantas|horror|ghost|supernatural|science fiction|gothic|grusel|gespenst|phantast|utopi|vampir/i,
  ],
  ["historisch", /historical fiction|historischer roman|historische erz/i],
  ["liebe", /love stories|liebes|man-woman relationships|romance/i],
  ["humor", /humou?r|satire|comedy|comedies|komödie|lustspiel|schwank|humoreske|parod/i],
  ["erzaehlungen", /short stories|erzählung|novelle|\btales\b|kurzgeschicht|anekdote/i],
  ["maerchen", /fairy tales|märchen|maerchen|legends|sage[n]?\b|folklore|fables|fabel|mytholog/i],
  [
    "drama",
    /\bdrama\b|tragedies|tragödie|trauerspiel|comedy plays|plays|theater|schauspiel|lustspiel|DE Drama/i,
  ],
  ["lyrik", /poetry|poems|lyrik|gedicht|ballade|DE Lyrik/i],
  ["kinder", /children|juvenile|kinderbuch|jugendbuch|kinder|DE Kinderbuch|young adult/i],
  ["roman", /novels|\broman\b|bildungsroman|DE Prosa/i],
  [
    "geschichte",
    /history|geschichte|world war|weltkrieg|antike|mittelalter|kings and rulers|prussia/i,
  ],
  ["philosophie", /philosoph|ethics|ethik|aesthetics/i],
  [
    "wissenschaft",
    /science|natur|biolog|physik|physics|chemi|astronom|mathemat|botan|zoolog|medizin|medicine|technolog|geolog|evolution/i,
  ],
  [
    "politik",
    /politic|politik|socialism|sozialismus|gesellschaft|social|economics|wirtschaft|law\b|recht|women|frauen|demokrat/i,
  ],
  ["reisen", /travel|reise|voyages|description and travel|expedition/i],
  [
    "biografie",
    /biograph|autobiograph|memoirs|erinnerungen|correspondence|letters|briefe|tagebuch|diaries/i,
  ],
  ["religion", /religion|christ|bible|bibel|theolog|church|kirche|jud(ai|e)|islam|buddh/i],
  ["kunst", /\bart\b|arts|kunst|music|musik|painting|malerei|architektur|architecture/i],
  ["psychologie", /psycholog|psychoanal|seele/i],
];

/** Kategorien eines Buchs aus den Schlagwörtern der Quelle — nie leer. */
export function categorize(_source: string, subjects: string[]): string[] {
  const out = new Set<string>();
  for (const s of subjects) {
    for (const [id, re] of RULES) if (re.test(s)) out.add(id);
  }
  // „DE Prosa“ ist bei Gutenberg der Sammeltopf aller Erzählprosa: nur dann Roman,
  // wenn keine genauere Gattung gefunden wurde.
  if (out.has("roman") && !subjects.some((s) => /novels|\broman\b|bildungsroman/i.test(s))) {
    const genauer = [...out].some(
      (c) => c !== "roman" && CATEGORIES.find((k) => k.id === c)?.group === "belletristik",
    );
    if (genauer) out.delete("roman");
  }
  return out.size ? [...out] : ["weitere"];
}

/** Gattung für die Mindestlänge. */
export function kindOf(categories: string[]): "prosa" | "drama" | "lyrik" {
  if (categories.includes("lyrik")) return "lyrik";
  if (categories.includes("drama")) return "drama";
  return "prosa";
}
