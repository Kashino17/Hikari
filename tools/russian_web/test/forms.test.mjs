// Wortformen offline (js/domain/forms.js + assets/russian/forms, gebaut von tools/russian_forms/build.mjs).
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import * as F from '../../../android/app/src/main/assets/russian-web/js/domain/forms.js';
import * as Ph from '../../../android/app/src/main/assets/russian-web/js/domain/phonetics.js';
import { normForm, splitCell } from '../../russian_forms/stress.mjs';

const DIR = new URL('../../../android/app/src/main/assets/russian/forms/', import.meta.url);
let loads = 0;
F.setLoader(async (path) => { loads++; return JSON.parse(await readFile(new URL(path, DIR), 'utf8')); });

const one = async (word, pos) => {
  const all = await F.lookup(word);
  const e = pos ? all.find((x) => x.pos === pos) : all[0];
  assert.ok(e, `kein Eintrag für ${word}${pos ? ` (${pos})` : ''}`);
  return e;
};

// ── Betonung umwandeln ──────────────────────────────────────────────────────

test('Betonung: Apostroph danach → "+" davor', () => {
  assert.equal(normForm("сказа'ть"), 'сказ+ать');
  assert.equal(normForm("вода'"), 'вод+а');
  assert.equal(normForm("лю'бишь"), 'л+юбишь');
  assert.equal(normForm("Пресс-конфере'нция"), 'Пресс-конфер+енция');
});

test('Betonung: Akut (Wiktionary) wie Apostroph, einsilbig ohne Zeichen', () => {
  assert.equal(normForm('сказа́ть'), 'сказ+ать');
  assert.equal(normForm('бы́ть'), 'быть');
  assert.equal(normForm("мне"), 'мне');
  assert.equal(normForm('кто́-то'), 'кт+о-то');
});

test('Betonung: ё bleibt und bekommt kein Zeichen', () => {
  assert.equal(normForm('идёшь'), 'идёшь');
  assert.equal(normForm("шёл"), 'шёл');
  assert.equal(normForm("ещё'"), 'ещё');
  assert.equal(normForm('смëтывать'), 'смётывать'); // lateinisches ë in der Quelle
});

test('Betonung: Nebenbetonung fällt weg, Gravis allein ist die Hauptbetonung', () => {
  assert.equal(normForm("стѐреоизображе'ние"), 'стереоизображ+ение');
  assert.equal(normForm("ме`жплане'тен"), 'межплан+етен');
  assert.equal(normForm('крутѝть'), 'крут+ить');
});

test('Betonung: zwei erlaubte Betonungen bleiben beide stehen', () => {
  assert.equal(normForm("гу'ме'н"), 'г+ум+ен');
});

test('Betonung: fehlt sie in der Quelle, wird nichts geraten', () => {
  assert.equal(normForm('пресс-конференция'), 'пресс-конференция');
  assert.equal(normForm("тво'рог"), 'тв+орог');
});

test('Quelle: lateinische Doppelgänger, Müll und Varianten', () => {
  assert.equal(normForm('скачáй'), 'скач+ай');
  assert.equal(normForm("трū`хко'мнатны"), null);
  assert.equal(normForm('-'), null);
  assert.deepEqual(splitCell("но'вы / новы'").map(normForm), ['н+овы', 'нов+ы']);
  assert.deepEqual(splitCell("застону'//_устар._застона'ю").map(normForm), ['застон+у', 'застон+аю']);
  assert.deepEqual(splitCell("до'лжной,(до'лжною)").map(normForm), ['д+олжной', 'д+олжною']);
  assert.deepEqual(splitCell("водо'й,  водо'ю").map(normForm), ['вод+ой', 'вод+ою']);
  assert.deepEqual(splitCell("взъе'лась$II(взъедатлась").map(normForm), ['взъ+елась']);
  assert.deepEqual(splitCell("*вы'сша").map(normForm), ['в+ысша']);
});

test('Kodierung: Formen relativ zur Grundform hin und zurück', () => {
  const lemma = 'челов+ек';
  for (const alts of [['челов+ека'], ['л+юди'], ['челов+ек', 'челов+еком'], []]) {
    assert.deepEqual(F.decodeValue(F.encodeValue(alts, lemma), lemma), alts);
  }
  assert.equal(F.encodeValue(['челов+ека'], lemma), '8а');
});

test('Schlüssel wie Ph.wordKeys: klein, ohne Betonung, ё→е', () => {
  assert.equal(F.keyOf('Идёт'), Ph.wordKeys('Идёт')[0]);
  assert.equal(F.keyOf('в+оду'), 'воду');
  assert.equal(F.keyOf('Ёлка'), 'елка');
});

// ── Nachschlagen ────────────────────────────────────────────────────────────

test('любить: Präsens, Vergangenheit, Imperativ, Aspekt und Partner', async () => {
  const [e] = await F.entry('любить');
  assert.equal(e.lemma, 'люб+ить');
  assert.equal(e.pos, 'verb');
  assert.equal(e.table, 'verb');
  assert.equal(e.aspect, 'impf');
  assert.ok(e.partner.includes('полюб+ить'));
  assert.ok(e.de.includes('lieben'));
  assert.deepEqual(e.forms.inf, ['люб+ить']);
  assert.deepEqual(e.forms.present, {
    sg1: ['любл+ю'], sg2: ['л+юбишь'], sg3: ['л+юбит'], pl1: ['л+юбим'], pl2: ['л+юбите'], pl3: ['л+юбят'],
  });
  assert.deepEqual(e.forms.past, { m: ['люб+ил'], f: ['люб+ила'], n: ['люб+ило'], pl: ['люб+или'] });
  assert.deepEqual(e.forms.imperative, { sg: ['люб+и'], pl: ['люб+ите'] });
  assert.equal(e.forms.future, undefined);
});

test('вода: alle Fälle in Einzahl und Mehrzahl', async () => {
  const [e] = await F.entry('вода');
  assert.equal(e.lemma, 'вод+а');
  assert.equal(e.gender, 'f');
  assert.equal(e.animate, false);
  assert.deepEqual(e.forms.sg, {
    nom: ['вод+а'], gen: ['вод+ы'], dat: ['вод+е'], acc: ['в+оду'], inst: ['вод+ой', 'вод+ою'], prep: ['вод+е'],
  });
  assert.deepEqual(e.forms.pl, {
    nom: ['в+оды'], gen: ['вод'], dat: ['в+одам'], acc: ['в+оды'], inst: ['в+одами'], prep: ['в+одах'],
  });
});

test('Form → Grundform: воду → вода, хочешь → хотеть (Betonung und Großschreibung egal)', async () => {
  assert.equal((await one('воду')).lemma, 'вод+а');
  assert.equal((await one('в+оду')).lemma, 'вод+а');
  assert.equal((await one('во́ду')).lemma, 'вод+а');
  const h = await one('Хочешь');
  assert.equal(h.lemma, 'хот+еть');
  assert.deepEqual(h.forms.present.sg1, ['хоч+у']);
  assert.deepEqual(h.forms.present.pl3, ['хот+ят']);
});

test('Jedes Wort eines Satzes aus dem Kurs findet seinen Eintrag', async () => {
  const sentence = 'Я хоч+у пить в+оду, а он л+юбит чай.';
  const want = ['я', 'хот+еть', 'пить', 'вод+а', 'а', 'он', 'люб+ить', 'чай'];
  const keys = Ph.wordKeys(sentence);
  assert.equal(keys.length, want.length);
  for (let i = 0; i < keys.length; i++) {
    const all = await F.lookup(keys[i]);
    assert.ok(all.some((e) => e.lemma === want[i]), `${keys[i]} → ${want[i]} (gefunden: ${all.map((e) => e.lemma)})`);
  }
});

test('Adjektiv хороший: Geschlecht × Fall, Kurzformen, Steigerung', async () => {
  const e = await one('хорошая', 'adj');
  assert.equal(e.lemma, 'хор+оший');
  assert.equal(e.table, 'adj');
  assert.deepEqual(e.forms.m.nom, ['хор+оший']);
  assert.deepEqual(e.forms.f.gen, ['хор+ошей']);
  assert.deepEqual(e.forms.m.acc, ['хор+ошего', 'хор+оший']);
  assert.deepEqual(e.forms.pl.inst, ['хор+ошими']);
  assert.deepEqual(e.forms.short, { m: ['хор+ош'], f: ['хорош+а'], n: ['хорош+о'], pl: ['хорош+и'] });
  assert.ok(e.forms.comp.includes('л+учше'));
});

test('Pronomen я: меня, мне, мной — und zurück', async () => {
  const [e] = await F.entry('я');
  assert.equal(e.pos, 'pron');
  assert.equal(e.table, 'pron');
  assert.deepEqual(e.forms.nom, ['я']);
  assert.deepEqual(e.forms.gen, ['мен+я']);
  assert.deepEqual(e.forms.acc, ['мен+я']);
  assert.deepEqual(e.forms.dat, ['мне']);
  assert.deepEqual(e.forms.inst, ['мной', 'мн+ою']);
  assert.deepEqual(e.forms.prep, ['мне']);
  for (const w of ['меня', 'мне', 'мной', 'мною']) assert.ok((await F.lookup(w)).some((x) => x.lemma === 'я'), w);
  const on = (await F.entry('он'))[0];
  assert.deepEqual(on.forms.gen, ['ег+о', 'нег+о']);
  assert.deepEqual(on.forms.prep, ['нём']);
  const my = (await F.entry('мы'))[0];
  assert.deepEqual(my.forms.inst, ['н+ами']);
  assert.ok((await F.lookup('нас')).some((x) => x.lemma === 'мы'));
});

test('Pronomen mit Adjektivtabelle: этот, мой', async () => {
  const e = (await F.entry('этот'))[0];
  assert.equal(e.pos, 'pron');
  assert.deepEqual(e.forms.f.acc, ['+эту']);
  assert.ok((await F.lookup('моего')).some((x) => x.lemma === 'мой'));
});

test('ё: mit und ohne Punkte gleich gefunden, Formen behalten ё ohne Zeichen', async () => {
  const a = await one('идёт', 'verb');
  const b = await one('идет', 'verb');
  assert.equal(a.lemma, 'идт+и');
  assert.equal(b.id, a.id);
  assert.deepEqual(a.forms.present.sg2, ['идёшь']);
  assert.deepEqual(a.forms.past.m, ['шёл']);
  const tree = await one('елки', 'noun');
  assert.equal(tree.lemma, 'ёлка');
  assert.equal(tree.key, 'елка');
  assert.deepEqual(tree.forms.sg.nom, ['ёлка']);
});

test('быть: Zukunft aus Wiktionary ergänzt (б+уду …)', async () => {
  const e = await one('буду', 'verb');
  assert.equal(e.lemma, 'быть');
  assert.deepEqual(e.forms.future.sg1, ['б+уду']);
  assert.deepEqual(e.forms.future.pl3, ['б+удут']);
  assert.equal(e.src, 'or+wikt');
});

test('Homographen: замок (з+амок / зам+ок), стали (сталь / стать)', async () => {
  const z = (await F.lookup('замок')).map((e) => e.lemma);
  assert.ok(z.includes('з+амок') && z.includes('зам+ок'), z.join(','));
  assert.ok(z.includes('зам+окнуть'), z.join(',')); // Vergangenheit замо́к
  const only = await F.entry('зам+ок');
  assert.equal(only.length, 1);
  assert.deepEqual(only[0].forms.sg.gen, ['замк+а']);
  const s = await F.lookup('стали');
  const lemmas = s.map((e) => e.lemma);
  assert.ok(lemmas.includes('сталь') && lemmas.includes('стать'), lemmas.join(','));
  const stat = s.find((e) => e.lemma === 'стать' && e.pos === 'verb');
  assert.deepEqual(stat.forms.past.pl, ['ст+али']);
  // gleiche Schreibung: das Verb steht vor dem seltenen Substantiv
  assert.equal((await F.lookup('стать'))[0].pos, 'verb');
});

test('Wendungen aus mehreren Wörtern hängen nicht am ersten Wort', async () => {
  const s = await F.lookup('с');
  assert.ok(s.some((e) => e.lemma === 'с'));
  assert.ok(!s.some((e) => e.lemma.includes(' ')), s.map((e) => e.lemma).join(','));
  const [p] = await F.entry('с тех пор');
  assert.equal(p.key, 'с тех пор');
  assert.ok(p.de.includes('seitdem'));
});

test('Nebenformen aus Wiktionary: со → с, ohne alte Rechtschreibung', async () => {
  const [e] = await F.lookup('со');
  assert.equal(e.lemma, 'с');
  assert.ok(e.variants.includes('со'));
  assert.ok(!e.variants.some((v) => v.endsWith('ъ')), e.variants.join(','));
});

test('Angehängte Partikel und Unbekanntes', async () => {
  assert.ok((await F.lookup('скажи-ка')).some((e) => e.lemma === 'сказ+ать'));
  assert.deepEqual(await F.lookup('ыыыыы'), []);
  assert.deepEqual(await F.lookup(''), []);
  assert.deepEqual(await F.entry('ыыыыы'), []);
});

test('Lädt nur, was gebraucht wird, und merkt es sich', async () => {
  await F.lookup('кошка');
  const before = loads;
  await F.lookup('кошка');
  await F.lookup('кошку');
  assert.equal(loads, before);
});

test('Daten: jede Form im Betonungsformat der App', async () => {
  const meta = JSON.parse(await readFile(new URL('meta.json', DIR), 'utf8'));
  const files = (await readdir(new URL('e/', DIR))).length;
  assert.equal(files, Math.ceil(meta.entries / meta.chunk));
  const bad = [];
  let n = 0;
  for (let c = 0; c < files; c++) {
    const raw = JSON.parse(await readFile(new URL(`e/${c}.json`, DIR), 'utf8'));
    raw.forEach((r, i) => {
      const e = F.expand(c * meta.chunk + i, r);
      for (const f of [e.lemma, ...F.allForms(e)]) {
        n++;
        const ok = /^[а-яёА-ЯЁ+\- ]+$/.test(f) && !/\+[^аеиоуыэюяАЕИОУЫЭЮЯ]/.test(f) && !f.endsWith('+')
          && f.split(' ').every((w) => (w.match(/[аеёиоуыэюя]/gi) ?? []).length > 1 || !w.includes('+'));
        if (!ok && bad.length < 10) bad.push(`${e.lemma}: ${f}`);
      }
    });
  }
  assert.deepEqual(bad, []);
  assert.ok(n > 800_000, `nur ${n} Formen`);
});
