// Deutsche Aussprache-Tipps zu einer Phonem-Abweichung (IPA aus dem Server-Check).
// Portierung von RussianPhonemeTips.kt.
const base = (p) => p.replace(/[͡ːˈˌ]/g, '');
const soft = (p) => p.endsWith('ʲ');
const plain = (p) => (p.endsWith('ʲ') ? p.slice(0, -1) : p);
const HARD_SIB = ['ʂ', 'ʐ'];
const REDUCED = ['ɐ', 'ə', 'ɪ', 'ʌ', 'ᵻ'];
const FULL = ['o', 'a', 'e', 'ɔ', 'ɛ', 'æ'];

export function phonemeTip(ref, user) {
  const r = base(ref);
  if (user == null) {
    return FULL.includes(plain(r)) || REDUCED.includes(plain(r)) || ['i', 'ɨ', 'u'].includes(plain(r))
      ? 'Ein Vokal fehlte. Im Russischen werden auch unbetonte Vokale gesprochen — nur schwächer und kürzer.'
      : 'Ein Laut fehlte. Sprich das Wort langsam, Laut für Laut.';
  }
  const u = base(user);
  if (r === 'ɨ' && plain(u) === 'i') return '»ы« klang wie »и«. Zunge nach hinten ziehen, Lippen locker — kein breites Lächeln.';
  if (r === 'i' && u === 'ɨ') return '»и« klang zu dunkel (wie »ы«). Zunge nach vorn, Lippen leicht breit.';
  if (HARD_SIB.includes(r) && ['s', 'z', 'ɕ', 'ʃ', 'ʒ', 'ʑ'].includes(plain(u))) return '»ш/ж« sind tief und hart: Zunge zurück, Lippen leicht gerundet — nicht wie deutsches »s« oder »sch«.';
  if (r === 'ɕ' && HARD_SIB.includes(u)) return '»щ« ist ein langes, weiches »sch« mit angehobener Zungenmitte — nicht so hart wie »ш«.';
  if (r === 'ts' && ['s', 't', 'tɕ', 'tʃ'].includes(plain(u))) return '»ц« ist ein schnelles »ts« — vorne ein kurzes »t« anschlagen.';
  if ((r === 'tɕ' || r === 'tʃ') && (HARD_SIB.includes(u) || u === 'ts')) return '»ч« beginnt mit »t« und endet weich — ein kompaktes »tsch«, Zungenmitte nach oben.';
  if (plain(r) === 'r' && ['l', 'ɾ', 'ɹ', 'ʁ', 'ʀ'].includes(plain(u))) return '»р« wird mit der Zungenspitze am Gaumen gerollt — nicht hinten im Hals wie im Deutschen.';
  if (plain(r) === 'x' && ['k', 'h', 'ç'].includes(plain(u))) return '»х« ist ein rauer Hauchlaut wie »ch« in »Bach« — ohne Verschluss.';
  if (soft(r) && !soft(u) && plain(r) === plain(u)) return 'Der Konsonant muss weich sein: Zungenmitte zum Gaumen heben, wie ein kleines »j« danach.';
  if (!soft(r) && soft(u) && plain(r) === plain(u)) return 'Der Konsonant muss hart sein: Zunge flach lassen, kein »j«-Nachklang.';
  if (REDUCED.includes(r) && FULL.includes(plain(u))) return 'Unbetontes »о« wird schwach und klingt wie »а« oder ein kurzes »ə« — nicht als klares »o« sprechen.';
  if (['o', 'ɔ'].includes(plain(r)) && (REDUCED.includes(u) || u === 'a')) return 'Betontes »о« klar und rund sprechen — nicht zu »а« verschlucken.';
  return null;
}

export function phonemeHints(marks, max = 3) {
  const out = [];
  for (const m of marks) {
    if (m.status === 'ok') continue;
    const t = phonemeTip(m.ref, m.user ?? null);
    if (t && !out.includes(t)) out.push(t);
    if (out.length >= max) break;
  }
  return out;
}
