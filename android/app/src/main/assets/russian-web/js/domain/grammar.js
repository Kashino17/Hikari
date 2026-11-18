// Grammatik-Lernweg: neun Schritte, jeder beginnt bei einer Frage, die man aus dem Deutschen kennt.
// Eine Idee pro Bildschirm, gleich danach üben. Das Fachwort kommt erst, wenn die Idee sitzt.
//
// Alles fest geschrieben und geprüft (keine Modell-Texte zur Laufzeit). Russisch im Kursformat:
// »+« vor dem betonten Vokal. Jede Betonung prüft tools/russian_web/test/grammar.test.mjs gegen das
// Wörterbuch. In deutschen Texten: {so} hebt hervor, »…« mit Kyrillisch wird zur Umschrift.
//
// Bildschirm-Arten:
//   de     { k, t, de: [[satz, satz?]], p: [absatz] }          — vom Deutschen her
//   pairs  { k, t, pairs: [[ru, ru, de]], rule }                — was im Russischen anders ist
//   table  { k, t, rows: [[bezeichnung, ru, de?]], rule }       — die Endungen auf einen Blick
//   memo   { k, t, big, text, term }                            — Eselsbrücke, dann das Fachwort
//   quiz   { k, t, de, stem: [vorher, nachher], opts, ok, why } — die richtige Form wählen
//   say    { k, t, de, ru }                                     — auf Russisch sagen

export const LESSONS = [
  {
    id: 'praesens', q: 'Wer macht was?', t: 'Verben je Person', term: 'Gegenwart',
    stones: /ich|du|person|verb|endung|konjug/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Die Endung verrät, wer es tut', de: [['Ich spiel{e}.', 'Du spiel{st}.', 'Er spiel{t}.']],
        p: ['Auch im Deutschen hängt das Ende des Verbs davon ab, wer etwas tut.', 'Im Russischen ist das genauso — nur noch deutlicher: An der Endung hörst du sofort, wer gemeint ist.'] },
      { kind: 'table', k: 'Im Russischen', t: 'чит+ать — lesen', rows: [['ich', 'я чит+аю', 'ich lese'], ['du', 'ты чит+аешь', 'du liest'], ['er, sie, es', 'он чит+ает', 'er liest'], ['wir', 'мы чит+аем', 'wir lesen'], ['ihr, Sie', 'вы чит+аете', 'ihr lest'], ['sie (mehrere)', 'он+и чит+ают', 'sie lesen']],
        rule: 'Die meisten Verben folgen einem von zwei Mustern: »-ешь, -ет« (чит+аешь) oder »-ишь, -ит« (говор+ишь). Die ich-Form endet fast immer auf »-ю« oder »-у«.' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Du hört man am »sch«', big: '-шь', text: 'Die du-Form endet in der Gegenwart immer auf »-шь« — wie ein leises »sch«, wenn du jemanden ansprichst.', term: 'Das Fachwort dafür: Konjugation, die Beugung des Verbs.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'Du liest ein Buch.', stem: ['Ты', 'кн+игу.'], opts: ['чит+аю', 'чит+аешь', 'чит+ает'], ok: 1, why: 'Ты (du) → die Endung »-ешь«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'Wir sprechen Russisch.', stem: ['Мы', 'по-р+усски.'], opts: ['говор+ю', 'говор+им', 'говор+ят'], ok: 1, why: 'Мы (wir) → »-им« bei diesem Verb.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Ich lese ein Buch.', ru: 'Я чит+аю кн+игу.' },
    ],
  },
  {
    id: 'akk', q: 'Wen oder was?', t: 'Das, was etwas abbekommt', term: 'Akkusativ',
    stones: /wen|akkusativ|objekt/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Wen oder was?', de: [['D{er} Hund schläft.', 'Ich sehe d{en} Hund.']],
        p: ['Im Deutschen ändert sich nur das kleine Wort davor: {der} wird zu {den}. Es zeigt: Der Hund tut nichts, er wird gesehen.', 'Frag dich: {Wen oder was} sehe ich, will ich, liebe ich? Die Antwort ist das Wort, das sich ändert.'] },
      { kind: 'pairs', k: 'Im Russischen', t: 'Das Wort selbst ändert sich', pairs: [['+Это вод+а.', 'Я пью в+оду.', 'Ich trinke Wasser.'], ['+Это м+узыка.', 'Я любл+ю м+узыку.', 'Ich liebe Musik.'], ['+Это сок.', 'Я пью сок.', 'Ich trinke Saft.']],
        rule: 'Endet das Wort auf »-а«, wird daraus »-у«; auf »-я« wird »-ю«. Männliche Dinge wie »сок« bleiben, wie sie sind.' },
      { kind: 'table', k: 'Auf einen Blick', t: 'Die Wen-Form', rows: [['auf -а', 'вод+а → в+оду', 'Wasser'], ['auf -я', '+Аня → +Аню', 'Anja'], ['männliches Ding', 'сок → сок', 'Saft'], ['männliche Person', 'брат → бр+ата', 'Bruder']],
        rule: 'Nur bei männlichen Personen und Tieren kommt ein »-а« dazu: Я в+ижу бр+ата — ich sehe den Bruder.' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Was du nimmst, kippt zum U', big: 'а → у', text: 'Wenn du etwas nimmst, willst, siehst oder liebst, kippt das »а« am Ende zum »у«.', term: 'Das Fachwort dafür: Akkusativ. Merk dir einfach: Akkusativ = Wen-Fall.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'Ich liebe Musik.', stem: ['Я любл+ю', '.'], opts: ['м+узыка', 'м+узыку', 'м+узыке'], ok: 1, why: 'Was liebst du? Die Musik — sie bekommt die Liebe ab, also Wen-Fall: »а« → »у«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'Ich lese die Zeitung.', stem: ['Я чит+аю', '.'], opts: ['газ+ета', 'газ+ету', 'газ+ете'], ok: 1, why: 'Was liest du? Die Zeitung → »газ+ету«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Ich trinke Wasser.', ru: 'Я пью в+оду.' },
    ],
  },
  {
    id: 'prep', q: 'Wo?', t: 'Orte und Themen', term: 'Präpositiv',
    stones: /wo-fall|wo\b|ort|präpositiv|lokativ|в\b/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Wo bist du?', de: [['Die Stadt ist groß.', 'Ich wohne in d{er} Stadt.']],
        p: ['Auch im Deutschen ändert sich das kleine Wort, sobald es um einen Ort geht: {die} wird zu {der}.', 'Im Russischen ändert sich wieder das Wort selbst — meist nur ein Buchstabe am Ende.'] },
      { kind: 'pairs', k: 'Im Russischen', t: 'Am Ende steht ein »е«', pairs: [['+Это Москв+а.', 'Я жив+у в Москв+е.', 'Ich wohne in Moskau.'], ['+Это +офис.', 'Я раб+отаю в +офисе.', 'Ich arbeite im Büro.'], ['+Это кн+ига.', 'Мы говор+им о кн+иге.', 'Wir sprechen über das Buch.']],
        rule: 'Nach »в« (in), »на« (auf, bei) auf die Frage »Wo?« und nach »о« (über) endet das Wort meist auf »-е«. Wörter auf »-ия« bekommen »-ии«: Герм+ания → в Герм+ании.' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Wo? Dann »е« wie Ende der Reise', big: '-е', text: 'Bist du irgendwo angekommen, steht am Ende ein »е« — wie ein Ortsschild.', term: 'Das Fachwort dafür: Präpositiv. Er heißt so, weil er immer nach einem kleinen Wort wie »в« oder »о« steht.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'Ich wohne in Berlin.', stem: ['Я жив+у в', '.'], opts: ['Берл+ин', 'Берл+ине', 'Берл+ина'], ok: 1, why: 'Wo wohnst du? In Berlin → »в Берл+ине«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'Das Buch liegt auf dem Tisch.', stem: ['Кн+ига леж+ит на', '.'], opts: ['стол+е', 'стол', 'стол+а'], ok: 0, why: 'Wo liegt es? Auf dem Tisch → »на стол+е«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Ich arbeite im Büro.', ru: 'Я раб+отаю в +офисе.' },
    ],
  },
  {
    id: 'gen', q: 'Wessen? Kein …', t: 'Besitz, Mengen, Verneinung', term: 'Genitiv',
    stones: /wessen|kein|nicht|genitiv|нет/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Wessen?', de: [['das Auto d{es} Vaters', 'Ich habe {keine} Zeit.']],
        p: ['Im Deutschen zeigt dieser Fall, wem etwas gehört.', 'Im Russischen brauchst du ihn noch öfter: nach »нет« (kein, es gibt nicht), nach Mengen (ein Glas …) und nach »у« (bei).'] },
      { kind: 'pairs', k: 'Im Russischen', t: '»а« wird zu »ы«', pairs: [['+Это вод+а.', 'Нет вод+ы.', 'Es gibt kein Wasser.'], ['+Это м+ама.', '+Это кн+ига м+амы.', 'Das ist Mamas Buch.'], ['+Это вр+емя.', 'У мен+я нет вр+емени.', 'Ich habe keine Zeit.']],
        rule: 'Weibliche Wörter: »-а« wird zu »-ы« (nach г, к, х, ж, ш, ч, щ zu »-и«). Männliche bekommen ein »-а«: брат → бр+ата.' },
      { kind: 'memo', k: 'Eselsbrücke', t: '»нет« macht leer', big: 'нет + …ы', text: 'Nach »нет« kommt nie die Grundform: Was es nicht gibt, ändert seine Endung.', term: 'Das Fachwort dafür: Genitiv. Merk dir: Genitiv = Wessen- und Kein-Fall.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'Ich habe keinen Bruder.', stem: ['У мен+я нет', '.'], opts: ['брат', 'бр+ата', 'бр+ату'], ok: 1, why: 'Nach »нет« → Genitiv. Männlich: + »а« → »бр+ата«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'ein Glas Wasser', stem: ['стак+ан', ''], opts: ['вод+а', 'вод+ы', 'в+оду'], ok: 1, why: 'Nach einer Menge (ein Glas) → »вод+ы«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Ich habe keine Zeit.', ru: 'У мен+я нет вр+емени.' },
    ],
  },
  {
    id: 'past', q: 'Was war?', t: 'Über gestern reden', term: 'Vergangenheit',
    stones: /vergangen|gestern|past|präteritum/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Gestern', de: [['Ich las.', 'Du last.', 'Sie las.']],
        p: ['Im Deutschen ändert sich die Vergangenheit je nach Person.', 'Im Russischen ist es einfacher — und anders: Es zählt nicht, wer es tut, sondern ob es ein Mann, eine Frau, ein »es« oder mehrere sind.'] },
      { kind: 'table', k: 'Im Russischen', t: 'чит+ать — lesen, gestern', rows: [['ein Mann', 'он чит+ал', 'er las'], ['eine Frau', 'он+а чит+ала', 'sie las'], ['es', 'он+о чит+ало', 'es las'], ['mehrere', 'он+и чит+али', 'sie lasen']],
        rule: 'Grundform ohne »-ть«, dann »-л« (Mann), »-ла« (Frau), »-ло« (es), »-ли« (mehrere). Für ich, du, er gilt dasselbe: Ein Mann sagt »я чит+ал«, eine Frau »я чит+ала«.' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Gestern fragt: wer bist du?', big: '-л · -ла · -ли', text: 'Nicht »ich, du, er« bestimmt die Endung, sondern: Mann, Frau, viele.', term: 'Das Fachwort dafür: Präteritum, die Vergangenheit.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'Gestern habe ich (als Mann) gearbeitet.', stem: ['Вчер+а я', '.'], opts: ['раб+отал', 'раб+отала', 'раб+отали'], ok: 0, why: 'Ein Mann → »-л«: »раб+отал«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'Anja hat angerufen.', stem: ['+Аня', '.'], opts: ['позвон+ил', 'позвон+ила', 'позвон+или'], ok: 1, why: 'Anja ist eine Frau → »-ла«: »позвон+ила«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Gestern habe ich gelesen.', ru: 'Вчер+а я чит+ал.' },
    ],
  },
  {
    id: 'dat', q: 'Wem?', t: 'Jemandem etwas geben', term: 'Dativ',
    stones: /wem|dativ/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Wem?', de: [['Ich gebe d{em} Freund das Buch.', 'Mir ist kalt.']],
        p: ['Wem gibst du etwas, wem schreibst du, wem ist kalt? Im Deutschen ändert sich wieder das kleine Wort: {dem}, {mir}.'] },
      { kind: 'pairs', k: 'Im Russischen', t: 'Die Endung »-е« oder »-у«', pairs: [['+Это м+ама.', 'Я звон+ю м+аме.', 'Ich rufe Mama an.'], ['+Это брат.', 'Я дар+ю бр+ату кн+игу.', 'Ich schenke meinem Bruder ein Buch.'], ['я', 'Мне х+олодно.', 'Mir ist kalt.']],
        rule: 'Weibliche Wörter: »-а« wird zu »-е«. Männliche bekommen ein »-у«. Viele Gefühle sagt man mit Wem: »мне х+олодно« (mir ist kalt), »мне нр+авится« (mir gefällt).' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Der Geschenk-Fall', big: 'ком+у?', text: 'Wem gibst, schenkst, schreibst, zeigst du etwas? Der Empfänger bekommt die Wem-Form.', term: 'Das Fachwort dafür: Dativ. Merk dir: Dativ = Wem-Fall.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'Ich schreibe Anja.', stem: ['Я пиш+у', '.'], opts: ['+Аня', '+Ане', '+Аню'], ok: 1, why: 'Wem schreibst du? Anja → »+Ане«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'Mir gefällt die Musik.', stem: ['', 'нр+авится м+узыка.'], opts: ['Я', 'Мне', 'Мен+я'], ok: 1, why: 'Wem gefällt es? Mir → »мне«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Ich rufe Mama an.', ru: 'Я звон+ю м+аме.' },
    ],
  },
  {
    id: 'inst', q: 'Womit? Mit wem?', t: 'Werkzeug und Begleitung', term: 'Instrumental',
    stones: /womit|mit wem|instrumental/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Mit wem? Womit?', de: [['Ich gehe mit d{er} Freundin.', 'Ich schreibe mit d{em} Stift.']],
        p: ['Mit wem bist du unterwegs? Womit machst du etwas? Im Deutschen hilft »mit« und das kleine Wort davor.'] },
      { kind: 'pairs', k: 'Im Russischen', t: 'Die Endung »-ой« oder »-ом«', pairs: [['+Это м+ама.', 'Я гул+яю с м+амой.', 'Ich gehe mit Mama spazieren.'], ['+Это брат.', 'Я игр+аю с бр+атом.', 'Ich spiele mit meinem Bruder.'], ['+Это р+учка.', 'Я пиш+у р+учкой.', 'Ich schreibe mit einem Stift.']],
        rule: 'Weibliche Wörter: »-а« wird zu »-ой«. Männliche bekommen »-ом«. »с« heißt »mit« (zusammen mit). Beim Werkzeug fällt es weg: »р+учкой« — mit dem Stift.' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Das »о« verbindet', big: '-ом · -ой', text: 'Mit wem oder womit? Dann kommt ein »о« in die Endung — wie ein Ring, der zwei Dinge verbindet.', term: 'Das Fachwort dafür: Instrumental, der Werkzeug-Fall.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'Ich trinke Tee mit Milch.', stem: ['Я пью чай с', '.'], opts: ['молок+о', 'молок+ом', 'молок+а'], ok: 1, why: 'Womit? Mit Milch → »с молок+ом«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'Ich spreche mit Anja.', stem: ['Я говор+ю с', '.'], opts: ['+Аней', '+Аня', '+Аню'], ok: 0, why: 'Mit wem? Mit Anja → »с +Аней«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Ich trinke Tee mit Milch.', ru: 'Я пью чай с молок+ом.' },
    ],
  },
  {
    id: 'aspect', q: 'Einmal oder immer wieder?', t: 'Verbpaare', term: 'Aspekt',
    stones: /aspekt|vollendet|verbpaar|fertig/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Läuft es oder ist es fertig?', de: [['Ich lese gerade.', 'Ich habe das Buch zu Ende gelesen.']],
        p: ['Im Deutschen sagst du mit Zusatzwörtern, ob etwas läuft oder fertig ist: gerade, oft, zu Ende.', 'Das Russische hat dafür zwei Verben: eins für das Laufende und Wiederholte, eins für das Fertige.'] },
      { kind: 'pairs', k: 'Im Russischen', t: 'Zu fast jedem Verb gibt es zwei', pairs: [['чит+ать', 'прочит+ать', 'lesen / fertig lesen'], ['д+елать', 'сд+елать', 'machen / fertig machen'], ['пис+ать', 'напис+ать', 'schreiben / fertig schreiben']],
        rule: 'Läuft etwas oder passiert es immer wieder: das linke Verb (чит+ать). Ist es einmal fertig geworden: das rechte (прочит+ать). Das rechte hat keine Gegenwart — »я прочит+аю« heißt »ich werde es zu Ende lesen«.' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Die Vorsilbe macht fertig', big: 'про- · с- · на-', text: 'Oft macht eine kleine Vorsilbe aus dem Verb ein »fertig«-Verb: чит+ать → прочит+ать.', term: 'Das Fachwort dafür: Aspekt — unvollendet (läuft) und vollendet (fertig).' },
      { kind: 'quiz', k: 'Üben', t: 'Welches Verb passt?', de: 'Gestern habe ich das Buch zu Ende gelesen.', stem: ['Вчер+а я', 'кн+игу.'], opts: ['чит+ал', 'прочит+ал'], ok: 1, why: 'Fertig geworden → das vollendete Verb: »прочит+ал«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'Ich lese jeden Tag.', stem: ['Я', 'к+аждый день.'], opts: ['чит+аю', 'прочит+аю'], ok: 0, why: 'Jeden Tag = immer wieder → »чит+аю«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Ich habe das Buch (fertig) gelesen.', ru: 'Я прочит+ал кн+игу.' },
    ],
  },
  {
    id: 'adj', q: 'Wie ist es?', t: 'Eigenschaftswörter', term: 'Adjektive',
    stones: /adjektiv|eigenschaft/i,
    screens: [
      { kind: 'de', k: 'Kennst du schon', t: 'Gut, guter, gute …', de: [['ein gut{er} Tag', 'eine gut{e} Zeit', 'ein gut{es} Buch']],
        p: ['Auch im Deutschen passt sich das Eigenschaftswort an das Wort an, zu dem es gehört.'] },
      { kind: 'table', k: 'Im Russischen', t: 'хор+оший — gut', rows: [['männlich', 'хор+оший день', 'ein guter Tag'], ['weiblich', 'хор+ошая пог+ода', 'gutes Wetter'], ['sächlich', 'хор+ошее м+есто', 'ein guter Ort'], ['Mehrzahl', 'хор+ошие друзь+я', 'gute Freunde']],
        rule: 'Männlich »-ый/-ий/-ой«, weiblich »-ая«, sächlich »-ое/-ее«, Mehrzahl »-ые/-ие«. Es richtet sich nach dem russischen Wort, nicht nach dem deutschen: »пог+ода« ist weiblich, auch wenn »Wetter« es nicht ist.' },
      { kind: 'memo', k: 'Eselsbrücke', t: 'Schau aufs Hauptwort', big: '-а → -ая', text: 'Endet das Hauptwort auf »-а«, endet das Eigenschaftswort davor auf »-ая«.', term: 'Das Fachwort dafür: Adjektiv, das Eigenschaftswort.' },
      { kind: 'quiz', k: 'Üben', t: 'Welche Form passt?', de: 'eine große Stadt', stem: ['', 'г+ород'], opts: ['больш+ой', 'больш+ая', 'больш+ое'], ok: 0, why: '»г+ород« ist männlich → »больш+ой«.' },
      { kind: 'quiz', k: 'Üben', t: 'Und hier?', de: 'eine neue Wohnung', stem: ['', 'кварт+ира'], opts: ['н+овый', 'н+овая', 'н+овое'], ok: 1, why: '»кварт+ира« endet auf »-а«, ist weiblich → »н+овая«.' },
      { kind: 'say', k: 'Sprechen', t: 'Sag es auf Russisch', de: 'Gutes Wetter!', ru: 'Хор+ошая пог+ода!' },
    ],
  },
];

export const lessonById = (id) => LESSONS.find((l) => l.id === id) ?? null;

/** Alle russischen Texte einer Lektion — für die Betonungsprüfung. */
export function russianOf(lesson) {
  const out = [];
  for (const s of lesson.screens) {
    if (s.pairs) for (const [a, b] of s.pairs) out.push(a, b);
    if (s.rows) for (const r of s.rows) out.push(r[1].replace('→', ' '));
    if (s.stem) out.push(...s.stem.filter(Boolean), ...s.opts);
    if (s.ru) out.push(s.ru);
  }
  return out;
}

/** Lernstand: {done: {id: Tag}, at: {id: Bildschirm}} — eigener Schlüssel, nie in progress_v1. */
export const GRAMMAR_KEY = 'grammar_v1';
