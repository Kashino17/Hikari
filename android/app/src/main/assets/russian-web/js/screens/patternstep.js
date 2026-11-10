// Satzbau-Muster als Schritt einer Lektion (Etappe »Anwenden«): erst das Muster verstehen,
// dann eigene Sätze bauen. Die ausführliche Fassung mit Szene bleibt unter Üben → Satzbau.
import { html, cls, Icon, mk, useState, useEffect, useRef, useKeys, fx, audio } from '../ui.js';
import { useProgress, progress, toast } from '../app.js';
import * as Ct from '../domain/content.js';
import { PhraseCard } from './phrasebook.js';

export function PatternStep({ step, env }) {
  const p = step.pattern;
  const s = useProgress();
  const gender = s.gender ?? 'MALE';
  const [page, setPage] = useState(0);
  const [choices, setChoices] = useState(() => p.slots.map(() => 0));
  const [built, setBuilt] = useState(1);
  const scroller = useRef(null);
  useEffect(() => { scroller.current?.scrollTo(0, 0); }, [page]);
  const sentence = Ct.buildPattern(p, choices, gender);
  const phrase = Ct.asPhrase(sentence.ru, sentence.de);
  const saved = s.mySentences.some((m) => m.ru === sentence.ru);
  const forward = () => { audio.stop(); if (page === 0) setPage(1); else env.onNext(); };
  useKeys({ Enter: forward }, [page]);
  const choose = (si, oi) => {
    if (choices[si] === oi) return;
    fx.tap(); audio.stop();
    setChoices(choices.map((c, i) => (i === si ? oi : c)));
    setBuilt((n) => n + 1);
  };
  const toggleSave = () => {
    if (saved) progress.removeMySentence(sentence.ru);
    else { progress.saveMySentence({ ...sentence, note: '' }); toast('In »Meine Sätze« gespeichert'); }
  };

  return html`<div class="scroll" ref=${scroller}><div class="stepc g12" key=${page}>
      ${page === 0 && html`
        <div class="center"><div class="over c">Satzbau des Tages</div><div class="title mt4">${p.title}</div></div>
        <div class="card p tint center" style="font-size:21px;font-weight:700;color:rgb(var(--c))">${mk(p.formula)}</div>
        <p style="line-height:1.55;color:rgba(255,255,255,.85)">${p.hook}</p>
        <div class="h2 mt8">So klingt es</div>
        ${p.core.slice(0, 3).map((c) => html`<${PhraseCard} p=${Ct.asPhrase(c.ru, c.de)} explain=${c.why ? `Warum so: ${mk(c.why)}` : null} />`)}
        <div class="hint"><span class="over">Die Regel dahinter</span><p>${mk(p.explain)}</p></div>`}
      ${page === 1 && html`
        <div class="center"><div class="over c">Bau deinen Satz</div>
          <p class="sub mt4">Tausche die Bausteine und hör dir jeden Satz an. Jede Kombination ist richtiges Russisch.</p></div>
        <${PhraseCard} p=${phrase} highlight key=${sentence.ru} />
        ${p.slots.map((slot, si) => html`<div><div class="over" style="margin-bottom:8px">${slot.label}</div>
          <div class="rowx g8 wrap">${slot.options.map((o, oi) => {
            const ru = gender === 'FEMALE' && o.ruF ? o.ruF : o.ru;
            const de = gender === 'FEMALE' && o.deF ? o.deF : o.de;
            return html`<button class=${cls('chip', choices[si] === oi && 'on c')} style="height:auto;min-height:36px;padding:6px 13px;white-space:normal;text-align:left"
              onClick=${() => choose(si, oi)}><span><b>${mk(ru)}</b> · ${de}</span></button>`;
          })}</div></div>`)}
        <button class=${cls('btn ghost', saved && 'tonal')} onClick=${toggleSave}><${Icon} n=${saved ? 'bookmark_added' : 'bookmark_add'} fill=${saved} />${saved ? 'Gespeichert' : 'Diesen Satz merken'}</button>`}
    </div></div>
    <div class="bottom">${page === 0
      ? html`<button class="btn" onClick=${forward}>Eigene Sätze bauen<span class="kb">↵</span></button>`
      : html`<button class="btn" onClick=${forward}>${built >= 2 ? 'Weiter zum Rollenspiel' : 'Weiter'}<span class="kb">↵</span></button>`}</div>`;
}
