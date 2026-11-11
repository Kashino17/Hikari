// Lautlabor: Minimalpaare erst hören und unterscheiden, dann selbst sprechen.
import { html, cls, Icon, TopBar, SessionBar, Opt, Mic, Summary, RecordCompare, useListen, useKeys, usePlaying, useState, useEffect, useMemo, fx, audio, MIC_WAIT } from '../ui.js';
import { D, useProgress, progress, nav, back } from '../app.js';
import { speech as recog } from '../platform.js';
import * as Ph from '../domain/phonetics.js';
import * as Co from '../domain/course.js';
import * as Sp from '../domain/speech.js';
import { sha1Hex } from '../domain/content.js';

const HEAR_ROUNDS = 8;

/** sounds.json schreibt das "+" HINTER den betonten Vokal — für Anzeige und Umschrift nach vorn holen. */
function word(w) {
  const ru = w.ru.replace(/([аеёиоуыэюя])\+/gi, '+$1');
  const plain = Ph.plain(ru);
  return { ru, de: w.de, plain, latin: Ph.transliterate(ru), flat: Ph.transliterateFlat(ru), display: Ph.display(ru), audio: `lab_${sha1Hex(w.ru).slice(0, 10)}` };
}
const play = (w, slow) => audio.play(w.audio, { slow, text: w.plain });
const Lat = ({ w }) => html`${w.latin.map((s) => (s.stressed ? html`<u style="color:var(--amber);text-underline-offset:3px">${s.text}</u>` : s.text))}`;

export function LabList() {
  const p = useProgress();
  return html`<div class="screen c-listen">
    <${TopBar} title="Lautlabor" sub="Ähnliche Laute unterscheiden — dann sprechen" onBack=${() => back('ueben')} />
    <div class="scroll"><div class="pad stack g8">
      ${D.sounds.map((g) => {
        const best = p.labBest[g.id];
        return html`<button class="row card" style="border-radius:18px;align-items:flex-start;padding:14px" onClick=${() => nav(`sounds/${g.id}`)}>
          <div class="ic"><${Icon} n=${g.id === 'stress' ? 'priority_high' : 'graphic_eq'} /></div>
          <div class="tx"><b>${g.title}</b><span style="line-height:1.5">${g.pairs.map((x) => `${word(x.a).flat} / ${word(x.b).flat}`).join('  ·  ')}</span></div>
          ${best != null ? html`<b class="num" style=${`color:${best >= 80 ? 'var(--good)' : 'var(--amber)'}`}>${best} %</b>` : html`<span class="badge">Neu</span>`}</button>`;
      })}
    </div><div class="end"></div></div>
  </div>`;
}

function WordCard({ w }) {
  const cur = usePlaying();
  return html`<button class="card p grow center" onClick=${() => play(w)}>
    <div style=${`font-size:22px;font-weight:600;color:${cur?.key === w.audio ? 'var(--amber)' : 'var(--text)'}`}><${Lat} w=${w} /></div>
    <div class="cyr" lang="ru">${w.display}</div><div class="sub mt4">${w.de}</div></button>`;
}

function Hear({ q, n, total, onDone }) {
  const [picked, setPicked] = useState(null);
  const played = q.playedA ? q.a : q.b;
  useEffect(() => { play(played); }, []);
  const pick = (isA) => { if (picked != null) return; setPicked(isA); const ok = isA === q.playedA; (ok ? fx.right : fx.wrong)(); q.ok = ok; };
  useKeys({ 1: () => pick(true), 2: () => pick(false), ' ': () => play(played), Enter: () => picked != null && onDone(q.ok) }, [picked]);
  const cur = usePlaying();
  const state = (isA) => (picked == null ? '' : isA === q.playedA ? 'ok' : isA === picked ? 'bad' : 'dim');
  return html`<div class="scroll"><div class="stepc center g16">
      <div><div class="over c">Hören ${n} / ${total}</div><div class="title mt4">Welches Wort hast du gehört?</div></div>
      <div class="play" style="--s:84px"><button class=${cls('pbtn', cur?.key === played.audio && !cur.slow && 'on')} onClick=${() => play(played)} aria-label="Anhören"><${Icon} n="volume_up" fill /></button>
        <button class=${cls('pbtn slow', cur?.key === played.audio && cur.slow && 'on')} onClick=${() => play(played, true)} aria-label="Langsam anhören"><${Icon} n="slow_motion_video" /></button></div>
      <div class="opts">${[[q.a, true], [q.b, false]].map(([w, isA], i) => html`<${Opt} kb=${i + 1} state=${state(isA)} onClick=${() => pick(isA)}>
        <div style="font-size:20px;font-weight:600"><${Lat} w=${w} /></div><div class="cyr" lang="ru">${w.display}</div><div class="sub">${w.de}</div><//>`)}</div>
      ${picked != null && html`<div class=${q.ok ? 'good' : 'bad'} style="font-weight:500">${q.ok ? 'Richtig.' : `Gespielt wurde »${played.flat}« — ${played.de}.`}</div>`}
    </div></div>
    ${picked != null && html`<div class="bottom"><button class="btn" onClick=${() => onDone(q.ok)}>Weiter<span class="kb">↵</span></button></div>`}`;
}

function Speak({ w, partner, group, onDone }) {
  const [verdict, setVerdict] = useState(null);
  const [passed, setPassed] = useState(false);
  const stress = group.id === 'stress' || !recog.available();
  const L = useListen((hyps) => {
    const target = Co.normWord(w.plain);
    const other = Co.normWord(partner.plain);
    const heard = hyps.flatMap((h) => Co.norm(h).split(' ')).filter(Boolean);
    if (heard.some((x) => Sp.similarity(x, target) >= 0.99)) { setVerdict(`Genau — das war »${w.flat}«.`); setPassed(true); fx.right(); }
    else { fx.wrong(); setVerdict(heard.includes(other) ? `Das klang wie »${partner.flat}«. ${group.tip}` : !heard.length ? 'Nichts verstanden — nochmal, etwas lauter.' : `Gehört: »${Ph.transliterateFlat(heard[0])}«. Hör das Original nochmal und versuche es erneut.`); }
  });
  useKeys({ Enter: () => onDone(passed), ' ': () => play(w) }, [passed]);
  const cur = usePlaying();
  return html`<div class="scroll"><div class="stepc center g16">
      <div class="over c">Sprechen</div>
      <div><div style="font-size:36px;font-weight:700"><${Lat} w=${w} /></div><div class="cyr" lang="ru" style="font-size:16px">${w.display}</div><div class="muted mt4">${w.de}</div></div>
      <div class="play" style="--s:64px"><button class=${cls('pbtn', cur?.key === w.audio && !cur.slow && 'on')} onClick=${() => play(w)} aria-label="Anhören"><${Icon} n="volume_up" fill /></button>
        <button class=${cls('pbtn slow', cur?.key === w.audio && cur.slow && 'on')} onClick=${() => play(w, true)} aria-label="Langsam anhören"><${Icon} n="slow_motion_video" /></button></div>
      ${stress ? html`<p class="sub" style="max-width:300px">${group.id === 'stress' ? 'Betonungspaare kann die Spracherkennung nicht trennen. Nimm dich auf und vergleiche Melodie und Laute mit dem Original.' : 'Nimm dich auf und vergleiche mit dem Original.'}</p>
          <${RecordCompare} phrase=${w} onTake=${() => { setPassed(true); setVerdict('Erst du, dann das Original — hörst du den Unterschied?'); }} />`
        : html`<div class="micwrap"><${Mic} L=${L} onClick=${() => { setVerdict(null); L.toggle(w.plain); }} /></div>`}
      <div class=${cls('mictxt', L.listening && 'on', passed && 'good')} style="max-width:320px">${L.evaluating ? 'Wird ausgewertet …' : L.listening ? (L.opening ? MIC_WAIT : 'Ich höre zu … tippe nochmal, wenn du fertig bist') : L.errorText ?? verdict ?? (stress ? '' : 'Tippen und das Wort sagen')}</div>
    </div></div>
    <div class="bottom"><button class=${cls('btn', !passed && 'ghost')} onClick=${() => { L.cancel(); onDone(passed); }}>${passed ? 'Weiter' : 'Überspringen'}<span class="kb">↵</span></button></div>`;
}

export function LabSession({ id }) {
  const group = D.sounds.find((g) => g.id === id);
  const data = useMemo(() => {
    if (!group) return null;
    const rng = Co.defaultRng();
    const pairs = group.pairs.map((x) => ({ a: word(x.a), b: word(x.b) }));
    const base = rng.shuffled(pairs.flatMap((x) => [{ ...x, playedA: true }, { ...x, playedA: false }]));
    const questions = [...base, ...rng.shuffled(base)].slice(0, Math.min(HEAR_ROUNDS, base.length * 2)).map((q) => ({ ...q }));
    const speak = rng.shuffled(pairs.flatMap((x) => [[x.a, x.b], [x.b, x.a]])).slice(0, 6);
    return { pairs, questions, speak };
  }, [id]);
  const [phase, setPhase] = useState('intro');
  const [i, setI] = useState(0);
  const [hear, setHear] = useState(0);
  const [spoke, setSpoke] = useState(0);
  const [percent, setPercent] = useState(0);
  const close = () => { audio.stop(); back('sounds'); };
  useKeys({ Enter: () => phase === 'intro' && setPhase('hear') }, [phase]);
  if (!group) return html`<div class="screen"><${TopBar} title="Lautlabor" onBack=${close} /><div class="empty">Diese Gruppe gibt es nicht.</div></div>`;
  const { pairs, questions, speak } = data;
  const total = questions.length + speak.length;
  const finish = (s) => {
    const pct = Math.floor(((hear + s) * 100) / total);
    setPercent(pct);
    progress.update((p) => ({ ...p, labBest: { ...p.labBest, [group.id]: Math.max(p.labBest[group.id] ?? 0, pct) } }));
    progress.addXp(5 + Math.floor(pct / 10));
    setPhase('done');
  };
  if (phase === 'done') {
    return html`<${Summary} celebrate=${percent >= 80} over="Geschafft" big=${`${percent} %`} title=${group.title}
      tiles=${[{ v: `${hear}/${questions.length}`, l: 'gehört' }, { v: `${spoke}/${speak.length}`, l: 'gesprochen' }, { v: `+${5 + Math.floor(percent / 10)}`, l: 'XP', c: 'var(--xp)' }]}
      actions=${html`<div class="rowx g8"><button class="btn ghost" onClick=${close}>Zurück</button><button class="btn" onClick=${() => { setI(0); setHear(0); setSpoke(0); setPhase('hear'); }}>Nochmal</button></div>`} />`;
  }
  const pos = phase === 'intro' ? 0 : phase === 'hear' ? i : questions.length + i;
  return html`<div class="screen c-listen">
    <${SessionBar} value=${pos / total} count=${phase === 'intro' ? null : `${pos + 1}/${total}`} onClose=${close} />
    ${phase === 'intro' && html`<div class="scroll"><div class="stepc g16">
        <div><div class="over c">Lautlabor</div><div class="title mt4">${group.title}</div></div>
        <div class="hint"><span class="over">So klingt der Unterschied</span><p>${group.tip}</p></div>
        ${pairs.map((x) => html`<div class="rowx g8" style="align-items:stretch"><${WordCard} w=${x.a} /><${WordCard} w=${x.b} /></div>`)}
        <p class="sub center">Tippe die Wörter an und hör genau hin.</p>
      </div></div><div class="bottom"><button class="btn" onClick=${() => setPhase('hear')}>Los: Hören<span class="kb">↵</span></button></div>`}
    ${phase === 'hear' && html`<${Hear} key=${`h${i}`} q=${questions[i]} n=${i + 1} total=${questions.length} onDone=${(ok) => { if (ok) setHear((h) => h + 1); if (i + 1 < questions.length) setI(i + 1); else { setI(0); setPhase('speak'); } }} />`}
    ${phase === 'speak' && html`<${Speak} key=${`s${i}`} w=${speak[i][0]} partner=${speak[i][1]} group=${group} onDone=${(ok) => { const s = spoke + (ok ? 1 : 0); setSpoke(s); if (i + 1 < speak.length) setI(i + 1); else finish(s); }} />`}
  </div>`;
}
