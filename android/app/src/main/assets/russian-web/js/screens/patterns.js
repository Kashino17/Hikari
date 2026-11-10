// Kurs »Satzbau«: Muster-Liste und Muster-Lektion (Szene → Muster → Bauen → Sprechen).
import { html, cls, Icon, TopBar, IL, Practice, usePlaying, mk, useState, useMemo, useRef, useEffect, audio, fx } from '../ui.js';
import { D, useProgress, progress, nav, back, toast } from '../app.js';
import * as Ct from '../domain/content.js';
import { makePhrase } from '../domain/course.js';
import * as Ph from '../domain/phonetics.js';
import { PhraseCard } from './phrasebook.js';

export function PatternList() {
  const p = useProgress();
  const weeks = [...new Set(D.patterns.map((x) => x.week))].sort((a, b) => a - b);
  const next = D.patterns.find((x) => !p.patternsDone.includes(x.id));
  return html`<div class="screen c-words">
    <${TopBar} title="Satzbau" sub="Muster, mit denen du eigene Sätze baust" onBack=${() => back('kurse')} />
    <div class="scroll">
      <p class="sub" style="padding:0 20px">Jedes Muster beginnt mit einer Szene voller Gefühl. Danach siehst du die Regel, baust eigene Sätze und sprichst frei. <b style="color:var(--text)">${p.patternsDone.length} von ${D.patterns.length} geschafft.</b></p>
      ${weeks.map((w) => html`<div class="sec"><div class="h3" style="margin:0 4px 8px">Woche ${w}</div>
        <div class="list">${D.patterns.filter((x) => x.week === w).map((x) => {
          const done = p.patternsDone.includes(x.id);
          return html`<button class=${cls('row', done && 'done', x === next && 'now')} onClick=${() => nav(`pattern/${x.id}`)}>
            <div class="dn">${done ? html`<${Icon} n="check" />` : D.patterns.indexOf(x) + 1}</div>
            <div class="tx"><b class="ell">${x.title}</b><span class="ell" style="color:rgb(var(--c))">${mk(x.formula)}</span>
              <span class="ell">${x.emotion ? `${x.emotion} · ` : ''}${x.scene.title}</span></div>
            ${x === next ? html`<span class="tag solid">Weiter</span>` : html`<${Icon} n="chevron_right" c="faint" />`}
          </button>`;
        })}</div></div>`)}
      <div class="end"></div>
    </div>
  </div>`;
}

const STEPS = ['Szene', 'Muster', 'Bauen', 'Sprechen'];

function SceneBubble({ l, gender }) {
  const cur = usePlaying();
  const { ru, de } = Ct.sceneLine(l, gender);
  const clip = Ct.sceneLineClip(l, gender);
  const me = l.who === 'U';
  const phrase = useMemo(() => makePhrase({ id: `dd:${clip}`, ru, de, day: 0, audio: clip }), [clip]);
  return html`<div class=${cls('bub', me && 'me', cur?.key === clip && 'on')} style="max-width:90%">
    <button style="display:block;width:100%" onClick=${() => audio.play(clip, { text: Ph.plain(ru), ru, voice: me ? undefined : 'p' })}>
      <div class="who">${me ? 'DU' : 'ANJA'}</div><${IL} ru=${ru} size=${17} /><div class="de">${de}</div>
      ${l.how && html`<div class="nt" style="font-style:italic">Gefühl: ${l.how}</div>`}</button>
    ${me && html`<div class="mt12"><${Practice} phrase=${phrase} compact /></div>`}
  </div>`;
}

export function Pattern({ id }) {
  const s = useProgress();
  const p = D.patterns.find((x) => x.id === id);
  const [step, setStep] = useState(0);
  const [choices, setChoices] = useState(() => (p ? p.slots.map(() => 0) : []));
  const [note, setNote] = useState('');
  const playing = useRef(0);
  const scroller = useRef(null);
  useEffect(() => { scroller.current?.scrollTo(0, 0); }, [step]);
  useEffect(() => () => { playing.current++; audio.stop(); }, []);
  if (!p) return html`<div class="screen"><${TopBar} title="Satzbau" onBack=${() => back('patterns')} /><div class="empty">Muster nicht gefunden.</div></div>`;
  const gender = s.gender ?? 'MALE';
  const built = Ct.buildPattern(p, choices, gender);
  const builtPhrase = Ct.asPhrase(built.ru, built.de);
  const saved = s.mySentences.some((m) => m.ru === built.ru);
  const done = s.patternsDone.includes(id);

  const playScene = async () => {
    const token = ++playing.current;
    for (const l of p.scene.lines) {
      if (playing.current !== token) return;
      const { ru } = Ct.sceneLine(l, gender);
      if (!(await audio.play(Ct.sceneLineClip(l, gender), { text: Ph.plain(ru), ru, voice: l.who === 'U' ? undefined : 'p' }))) return;
    }
  };
  const goto = (n) => { playing.current++; audio.stop(); setStep(n); };
  const finish = () => {
    audio.stop();
    progress.apply({ xp: done ? 5 : 25, graded: {} });
    if (!done) progress.update((x) => ({ ...x, patternsDone: [...x.patternsDone, id] }));
    fx.win(); toast(done ? 'Muster wiederholt · +5 XP' : 'Muster geschafft · +25 XP');
    back('patterns');
  };
  const toggleSave = () => {
    if (saved) progress.removeMySentence(built.ru);
    else { progress.update((x) => ({ ...x, mySentences: [...x.mySentences.filter((m) => m.ru !== built.ru), { ...built, note: note.trim() }] })); toast('In »Meine Sätze« gespeichert'); }
  };

  return html`<div class="screen c-words">
    <${TopBar} title=${p.title} onBack=${() => back('patterns')} />
    <div class="rowx g8" style="padding:0 16px 10px">${STEPS.map((t, i) => html`<button class="grow" onClick=${() => goto(i)}>
      <div class="pbar" style="height:5px"><i style=${`width:${i <= step ? 100 : 0}%`}></i></div>
      <div class="mt4" style=${`font-size:12px;font-weight:${i === step ? 600 : 400};color:${i === step ? 'var(--text)' : 'var(--faint)'}`}>${t}</div></button>`)}</div>
    <div class="scroll" ref=${scroller}><div class="stepc g12" key=${step}>
      ${step === 0 && html`
        ${p.emotion && html`<span class="tag" style="align-self:flex-start">${p.emotion}</span>`}
        <div class="title">${p.scene.title}</div><p class="muted" style="font-style:italic;line-height:1.5">${p.scene.setting}</p>
        <button class="chip" style="align-self:flex-start" onClick=${playScene}><${Icon} n="play_circle" />Ganze Szene anhören</button>
        ${p.scene.lines.map((l) => html`<${SceneBubble} l=${l} gender=${gender} />`)}
        <p class="sub mt8">Tipp: Sprich die Zeilen mit dem beschriebenen Gefühl mit — erst dadurch bleiben sie hängen.</p>`}
      ${step === 1 && html`
        <div class="over c">Das Muster</div>
        <div class="card p tint center" style="font-size:22px;font-weight:700;color:rgb(var(--c))">${mk(p.formula)}</div>
        <p style="line-height:1.55;color:rgba(255,255,255,.85)">${p.hook}</p>
        <div class="h2 mt12">So klingt es</div><div class="sub" style="margin-top:-8px">Hör zu, sprich nach — dann sitzt das Muster.</div>
        ${p.core.map((c) => html`<${PhraseCard} p=${Ct.asPhrase(c.ru, c.de)} explain=${c.why ? `Warum so: ${mk(c.why)}` : null} />`)}
        <div class="h2 mt12">Die Regel dahinter</div><div class="sub" style="margin-top:-8px">Damit baust du Sätze, die du nie gehört hast.</div>
        <div class="hint"><p>${mk(p.explain)}</p></div>`}
      ${step === 2 && html`
        <div class="title">Bau deinen Satz</div><p class="sub" style="margin-top:-6px">Tausche die Bausteine — jede Kombination ist ein richtiger russischer Satz.</p>
        <${PhraseCard} p=${builtPhrase} highlight key=${built.ru} />
        ${p.slots.map((slot, si) => html`<div><div class="over" style="margin-bottom:8px">${slot.label}</div>
          <div class="rowx g8 wrap">${slot.options.map((o, oi) => {
            const ru = gender === 'FEMALE' && o.ruF ? o.ruF : o.ru;
            const de = gender === 'FEMALE' && o.deF ? o.deF : o.de;
            return html`<button class=${cls('chip', choices[si] === oi && 'on c')} style="height:auto;min-height:36px;padding:6px 13px;white-space:normal;text-align:left"
              onClick=${() => { fx.tap(); setChoices(choices.map((c, i) => (i === si ? oi : c))); }}><span><b>${mk(ru)}</b> · ${de}</span></button>`;
          })}</div></div>`)}
        <input class="field mt8" value=${note} onInput=${(e) => setNote(e.target.value)} placeholder="Wem würdest du das sagen? (optional)" />
        <button class=${cls('btn ghost', saved && 'tonal')} onClick=${toggleSave}><${Icon} n=${saved ? 'bookmark_added' : 'bookmark_add'} fill=${saved} />${saved ? 'Gespeichert — tippen zum Entfernen' : 'In »Meine Sätze« speichern'}</button>`}
      ${step === 3 && html`
        <div class="title">Jetzt du</div><p style="line-height:1.55;color:rgba(255,255,255,.85)">${p.free.prompt}</p>
        <div class="h2 mt12">Zum Orientieren</div><div class="sub" style="margin-top:-8px">Ein Beispiel — sag es mit deinen eigenen Worten und deinem Gefühl.</div>
        <${PhraseCard} p=${Ct.asPhrase(p.free.model.ru, p.free.model.de)} highlight explain=${p.free.model.why ? mk(p.free.model.why) : null} />
        <div class="h2 mt12">Dein gebauter Satz</div><div class="sub" style="margin-top:-8px">Sprich ihn laut, mit dem Gefühl aus der Szene.</div>
        <${PhraseCard} p=${builtPhrase} />`}
    </div></div>
    <div class="bottom">${step < 3
      ? html`<button class="btn" onClick=${() => goto(step + 1)}>Weiter: ${STEPS[step + 1]}<${Icon} n="arrow_forward" /></button>`
      : html`<button class="btn area" onClick=${finish}><${Icon} n="check" />${done ? 'Fertig' : 'Muster abschließen · +25 XP'}</button>`}</div>
  </div>`;
}
