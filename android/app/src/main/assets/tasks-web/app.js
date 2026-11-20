"use strict";
/* Aufgaben — Oberfläche. Regeln wie backend/src/tasks/plan.ts (Server ist die Quelle). */

const N = window.HikariNative || null;
const Q = new URLSearchParams(location.search);
/** null = Reiter im Profil, sonst eine Unterseite als eigener Bildschirm. */
const ROOT = Q.get("page");
const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

/* ---------- Symbole ---------- */
const P = {
  mic: '<rect x="9" y="2.5" width="6" height="12" rx="3"/><path d="M18.5 10.5v.5a6.5 6.5 0 0 1-13 0v-.5M12 17.5V21"/>',
  check: '<path d="M20 6.5 9.5 17 4 11.5"/>',
  chev: '<path d="m9 6 6 6-6 6"/>',
  back: '<path d="M15 18l-6-6 6-6"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  split: '<path d="M4 6h7M4 12h11M4 18h16"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  cal: '<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  dots: '<circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/>',
  star: '<path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/>',
  kb: '<rect x="2.5" y="6" width="19" height="12" rx="2.5"/><path d="M6.5 10h.01M10 10h.01M14 10h.01M17.5 10h.01M7.5 14h9"/>',
  amt: '<rect x="5" y="2.5" width="14" height="19" rx="2.5"/><circle cx="12" cy="10" r="3.2"/><path d="M8.8 16.5h6.4"/>',
  geld: '<rect x="2.5" y="6" width="19" height="13" rx="2.5"/><path d="M2.5 10.5h19M15.5 15h2.5"/>',
  job: '<rect x="3" y="7" width="18" height="13" rx="2.5"/><path d="M9 7V5.5A2 2 0 0 1 11 3.5h2a2 2 0 0 1 2 2V7M3 13h18"/>',
  leute: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><circle cx="17" cy="9" r="2.5"/><path d="M16.5 14.6a5 5 0 0 1 5 5.4"/>',
  haus: '<path d="M3 11 12 3.5l9 7.5"/><path d="M5.5 9.5V20.5h13V9.5M10 20.5v-6h4v6"/>',
  ich: '<path d="M12 3v3.5M12 17.5V21M3 12h3.5M17.5 12H21M5.6 5.6l2.5 2.5M15.9 15.9l2.5 2.5M5.6 18.4l2.5-2.5M15.9 8.1l2.5-2.5"/><circle cx="12" cy="12" r="2.5"/>',
};
const I = (n, cls = "ic", st = "") => `<svg class="${cls}" viewBox="0 0 24 24" ${st}>${P[n]}</svg>`;
const ART = {
  rechner: '<rect x="34" y="12" width="92" height="60" rx="6"/><path d="M20 84h120l-9 10H29z"/><path d="M48 30h34M48 42h52M48 54h24"/><circle cx="108" cy="30" r="5"/>',
  telefon: '<rect x="58" y="8" width="44" height="86" rx="10"/><path d="M73 17h14"/><circle cx="80" cy="44" r="10"/><path d="M68 70h24"/><path d="M112 34a18 18 0 0 1 0 26M121 26a30 30 0 0 1 0 42M48 34a18 18 0 0 0 0 26M39 26a30 30 0 0 0 0 42"/>',
  unterwegs: '<path d="M16 92c22-4 30-26 52-24s30 8 44-6" stroke-dasharray="2 9"/><circle cx="16" cy="92" r="5"/><path d="M118 8a17 17 0 0 1 17 17c0 13-17 30-17 30s-17-17-17-30a17 17 0 0 1 17-17z"/><circle cx="118" cy="25" r="6"/>',
  zuhause: '<path d="M30 52 80 14l50 38"/><path d="M42 44v50h76V44"/><path d="M70 94V70h20v24"/><rect x="98" y="56" width="12" height="12" rx="1"/><path d="M52 6v14"/>',
};

const AREAS = {
  amt: { name: "Behörden & Reisen", short: "Behörden", c: "#38BDF8" },
  geld: { name: "Finanzen", short: "Finanzen", c: "#34D399" },
  job: { name: "Arbeit", short: "Arbeit", c: "#A78BFA" },
  leute: { name: "Menschen", short: "Menschen", c: "#F472B6" },
  haus: { name: "Zuhause & Alltag", short: "Alltag", c: "#FB923C" },
  ich: { name: "Hobby & Ich", short: "Hobby", c: "#2DD4BF" },
};
const KINDS = { rechner: { name: "Am Rechner" }, telefon: { name: "Telefonieren" }, unterwegs: { name: "Unterwegs" }, zuhause: { name: "Zuhause" } };

/* ---------- Datum (alles relativ zum »heute« des Servers) ---------- */
const DAY = 864e5;
const WD = ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"];
const WDL = ["Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"];
const pad = (n) => String(n).padStart(2, "0");
const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const pd = (s) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };
const T0 = () => pd(D.today);
const dOf = (o) => { const d = T0(); d.setDate(d.getDate() + o); return d; };
const dayIso = (o) => iso(dOf(o));
const off = (s) => (s ? Math.round((pd(s) - T0()) / DAY) : null);
const dueWord = (o) => o == null ? null : o < 0 ? "überfällig" : o === 0 ? "heute" : o === 1 ? "morgen" : o < 7 ? WDL[dOf(o).getDay()] : dOf(o).toLocaleDateString("de-DE", { day: "numeric", month: "short" });
const fmtMin = (m) => m >= 60 ? `${Math.floor(m / 60)} Std${m % 60 ? " " + (m % 60) : ""}` : `${m} min`;
const untilWd = (wd) => { const d = (wd - T0().getDay() + 7) % 7; return d === 0 ? 7 : d; };
const dayLabel = (ts) => { const d = new Date(ts); d.setHours(0, 0, 0, 0); const o = Math.round((T0() - d) / DAY); return o <= 0 ? "Heute" : o === 1 ? "Gestern" : o < 7 ? WDL[d.getDay()] : d.toLocaleDateString("de-DE", { day: "numeric", month: "long" }); };
const hhmm = (ts) => new Date(ts).toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" });

/* ---------- Zustand ---------- */
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};
let D = store.get("tasks_cache", null) || { today: iso(new Date()), tasks: [], projects: [], pending: 0 };
let loaded = false, offline = false;
let minutes = store.get("tasks_minutes", 15);

const byId = (id) => D.tasks.find((t) => t.id === id);
const projOf = (pid) => D.projects.find((p) => p.id === pid);

function projNext(pid) {
  const p = projOf(pid); if (!p) return null;
  for (const st of p.steps) {
    if (st.task) { const t = byId(st.task); if (!t) continue; if (!t.done && !t.wait) return t; if (!t.done) return null; }
    else if (!st.done && !st.fixed) return null;
  }
  return null;
}
function projProgress(pid) {
  const steps = projOf(pid).steps.filter((s) => !s.fixed && (!s.task || byId(s.task)));
  return { done: steps.filter((s) => (s.task ? byId(s.task).done : s.done)).length, all: steps.length };
}
const blocked = (t) => !!t.wait || (!!t.project && projNext(t.project) !== t);
const held = (t) => !!t.holdUntil && off(t.holdUntil) >= 0;
function effDue(t) {
  if (t.due) return off(t.due);
  if (!t.project) return null;
  const p = projOf(t.project); if (!p || !p.deadline) return null;
  const left = p.steps.filter((s) => s.task && byId(s.task) && !byId(s.task).done && !byId(s.task).wait).length;
  return Math.max(0, off(p.deadline) - (left - 1) * 2 - 1);
}
function score(t) {
  const d = effDue(t);
  const frist = d == null ? 0 : d <= 0 ? 1 : d === 1 ? 0.75 : d <= 3 ? 0.55 : d <= 7 ? 0.32 : 0.15;
  const age = Math.max(0, (Date.now() - t.createdAt) / DAY);
  return frist * 60 + (Math.min(3, t.stakes) / 3) * 24 + (t.project ? 14 : 0) + Math.min(1, age / 21) * 12;
}
const openTasks = () => D.tasks.filter((t) => !t.done && !blocked(t) && !held(t)).sort((a, b) => score(b) - score(a));
function fitFor(m) {
  const pick = []; let left = m;
  for (const t of openTasks()) { if (pick.length >= 3) break; if (t.minutes <= left) { pick.push(t); left -= t.minutes; } }
  return pick;
}
function why(t) {
  const d = effDue(t); const r = [];
  if (t.project && projOf(t.project)) r.push(`Schritt für ${projOf(t.project).title}`);
  else if (d != null) r.push(d < 0 ? "überfällig" : d === 0 ? "Frist heute" : "bis " + dueWord(d));
  if (t.note) r.push(t.note);
  const age = (Date.now() - t.createdAt) / DAY;
  if (age >= 14) r.push(`seit ${Math.round(age / 7)} Wochen offen`);
  if (!r.length) r.push(AREAS[t.area].short);
  return r;
}
function dueTag(t) {
  const d = effDue(t);
  if (d == null || t.project) return "";
  return d < 0 ? '<span class="hot">überfällig</span>' : d === 0 ? '<span class="hot">heute fällig</span>' : d === 1 ? '<span class="hot">bis morgen</span>' : `bis ${dueWord(d)}`;
}

/* ---------- Server ---------- */
let reqSeq = 0; const waiting = new Map();
window.__hkResp = (id, status, text) => {
  const w = waiting.get(id); if (!w) return; waiting.delete(id);
  if (status >= 200 && status < 300) { try { w.ok(text ? JSON.parse(text) : null); } catch { w.ok(null); } }
  else w.fail(new Error(String(status)));
};
function api(method, path, body) {
  if (method === "GET") return fetch("/api" + path, { cache: "no-store" }).then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); });
  if (N && N.request) return new Promise((ok, fail) => { const id = ++reqSeq; waiting.set(id, { ok, fail }); N.request(id, method, path, body ? JSON.stringify(body) : ""); });
  return fetch("/api" + path, { method, headers: body ? { "content-type": "application/json" } : {}, body: body ? JSON.stringify(body) : undefined })
    .then((r) => { if (!r.ok) throw new Error(r.status); return r.status === 204 ? null : r.json(); });
}
let pollT = null;
async function load() {
  try {
    D = await api("GET", "/tasks");
    store.set("tasks_cache", D); offline = false;
  } catch { offline = true; }
  loaded = true; render();
  clearTimeout(pollT);
  if (D.pending > 0) pollT = setTimeout(load, 4000);
}
window.__hkRefresh = load;
const changed = () => { store.set("tasks_cache", D); N?.changed?.(); };
function failed() { toast("Nicht gespeichert, Hikari-Server nicht erreichbar"); load(); }

/* ---------- Bausteine ---------- */
const sq = (a) => `<span class="sq" style="background:${AREAS[a].c}">${I(a)}</span>`;
const mbar = (list) => {
  const sum = list.reduce((s, t) => s + t.minutes, 0) || 1;
  return `<div class="mbar">${list.slice().sort((a, b) => a.minutes - b.minutes).map((t) => `<i style="flex:${t.minutes / sum};background:${AREAS[t.area].c}"></i>`).join("")}</div>`;
};
const pill = (t) => `<span class="pill" style="background:${AREAS[t.area].c}"><b>${t.minutes >= 100 ? Math.round(t.minutes / 60) : t.minutes}</b><small>${t.minutes >= 100 ? "Std" : "min"}</small></span>`;
const longDate = () => T0().toLocaleDateString("de-DE", { weekday: "long", day: "numeric", month: "long" });

/* ---------- Reiter (Hauptansicht) ---------- */
function viewHome() {
  const opts = [[5, "5 min"], [15, "15 min"], [30, "30 min"], [90, "1 Std+"]];
  const pick = fitFor(minutes); const sum = pick.reduce((s, t) => s + t.minutes, 0);
  const open = openTasks();
  const doneW = D.tasks.filter((t) => t.done && t.doneAt && Date.now() - t.doneAt < 7 * DAY).length;
  const all = D.tasks.filter((t) => !t.done);

  let h = `<div class="dayrow"><span class="d">${longDate()}</span><button class="donelink" data-go="done"><i>${I("check")}</i>${doneW} erledigt${I("chev", "ic c")}</button></div>`;
  if (offline) h += `<div class="offline">Hikari-Server gerade nicht erreichbar, du siehst den letzten Stand.</div>`;
  if (D.pending > 0) h += `<div class="pend"><span class="sp"></span><div><b>Hikari sortiert ${D.pending === 1 ? "deine Aufnahme" : D.pending + " Aufnahmen"}</b><span>läuft im Hintergrund, du bekommst Bescheid</span></div></div>`;

  const empty = !all.length
    ? (loaded ? "Noch keine Aufgaben. Tipp aufs Mikrofon und sag einfach, was ansteht, auch mehrere Sachen in einem Satz." : "Lädt …")
    : "Für so wenig Zeit ist gerade nichts dran.";
  h += `<div class="tc"><h2>Wie viel Zeit hast du?</h2>
    <div class="tchips">${opts.map(([v, l]) => `<button data-min="${v}" class="${minutes === v ? "on" : ""}">${l}</button>`).join("")}</div>
    <div class="tlist">${pick.map((t) => `<button class="trow" data-go="focus:${t.id}">${pill(t)}
      <div style="min-width:0"><div class="t">${esc(t.title)}</div><div class="m">${esc(why(t).slice(0, 2).join(" · "))}</div></div><span class="ch">${I("chev")}</span></button>`).join("") || `<div class="tempty">${empty}</div>`}</div>
    ${pick.length ? `<button class="tgo" data-go="focus:${pick[0].id}">Los geht's<span>${pick.length} ${pick.length === 1 ? "Ding" : "Dinge"} · ${sum} min</span>${I("arrow")}</button>` : ""}</div>`;

  const dl = D.tasks.filter((t) => !t.done && !blocked(t) && !held(t) && effDue(t) != null && effDue(t) <= 6).sort((a, b) => effDue(a) - effDue(b) || score(b) - score(a));
  const nx = dl[0];
  h += `<div class="wk"><div class="wh"><b>Fristen diese Woche</b><span>${dl.length ? dl.length + " fällig" : "nichts fällig"}</span></div><div class="week">${[0, 1, 2, 3, 4, 5, 6].map((o) => {
    const l = dl.filter((t) => Math.max(0, effDue(t)) === o);
    return `<button data-go="day:${dayIso(o)}" class="${o === 0 ? "today" : ""}"><small>${WD[dOf(o).getDay()]}</small><b>${dOf(o).getDate()}</b><span class="wd">${l.slice(0, 3).map((t) => `<i style="background:${AREAS[t.area].c}"></i>`).join("")}</span></button>`;
  }).join("")}</div>
    ${nx ? `<button class="nx" data-go="day:${dayIso(Math.max(0, effDue(nx)))}"><span class="bar" style="background:${AREAS[nx.area].c}"></span><div><div class="t">${esc(nx.title)}</div><div class="m">Als Nächstes fällig · ${dueWord(effDue(nx))}</div></div>${I("chev")}</button>` : ""}</div>`;

  h += `<div class="hubs">
    <button class="hub k" data-go="kinds"><div class="mini">${Object.keys(KINDS).map((k) => `<span><svg viewBox="0 0 160 104">${ART[k]}</svg></span>`).join("")}</div><div class="lb"><div><b>Was gerade geht</b><span>${open.length} Dinge · 4 Orte</span></div>${I("chev")}</div></button>
    <button class="hub a" data-go="areas">${miniTreemap()}<div class="lb"><div><b>Alles nach Bereich</b><span>${all.length} offen · 6 Bereiche</span></div>${I("chev")}</div></button></div>`;
  return h;
}

function areaStats(k) {
  const l = D.tasks.filter((t) => t.area === k && !t.done);
  return { n: l.length, min: l.reduce((s, t) => s + t.minutes, 0) };
}
function tmLayout() {
  const items = Object.keys(AREAS).map((k) => ({ k, ...areaStats(k) })).map((x) => ({ ...x, v: x.n + 1.5 })).sort((a, b) => b.v - a.v);
  const out = [];
  (function lay(list, x, y, w, h) {
    if (list.length === 1) { out.push({ ...list[0], x, y, w, h }); return; }
    const tot = list.reduce((s, i) => s + i.v, 0); let acc = 0, i = 0;
    while (i < list.length - 1 && acc + list[i].v <= tot / 2) { acc += list[i].v; i++; }
    if (i === 0) { acc = list[0].v; i = 1; }
    const r = acc / tot;
    if (w * 1.15 >= h) { lay(list.slice(0, i), x, y, w * r, h); lay(list.slice(i), x + w * r, y, w * (1 - r), h); }
    else { lay(list.slice(0, i), x, y, w, h * r); lay(list.slice(i), x, y + h * r, w, h * (1 - r)); }
  })(items, 0, 0, 100, 100);
  return out;
}
const miniTreemap = () => `<div class="mtm">${tmLayout().map((c) => `<i style="left:${c.x}%;top:${c.y}%;width:${c.w}%;height:${c.h}%"><b style="background:${AREAS[c.k].c}"></b></i>`).join("")}</div>`;
function treemap() {
  return `<div class="tm">${tmLayout().map((c) => {
    const smallC = c.w * c.h < 1500; const low = c.h * 4.6 < 118;
    return `<button class="cell ${smallC ? "small" : ""} ${low ? "low" : ""}" data-go="area:${c.k}" style="left:${c.x}%;top:${c.y}%;width:${c.w}%;height:${c.h}%">
      <span class="in" style="background:${AREAS[c.k].c}">${I(c.k)}${I(c.k, "big")}<span class="cnt cond">${c.n}</span><span class="nm">${AREAS[c.k].short}</span>${smallC ? "" : `<span class="sm">${fmtMin(c.min)}</span>`}</span></button>`;
  }).join("")}</div>`;
}
function ring(pid, size = 34) {
  const { done, all } = projProgress(pid); const c = AREAS[projOf(pid).area].c;
  const r = size / 2 - 3.5, L = 2 * Math.PI * r;
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" style="flex:none"><circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="#ffffff1a" stroke-width="4"/>
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${c}" stroke-width="4" stroke-linecap="round" stroke-dasharray="${L * done / Math.max(1, all)} ${L}" transform="rotate(-90 ${size / 2} ${size / 2})"/></svg>`;
}

/* ---------- Unterseiten ---------- */
const pgTop = (title) => `<div class="pg-top"><button data-back aria-label="Zurück">${I("back")}</button>${title ? `<h3>${esc(title)}</h3>` : ""}</div>`;
const BUCKETS = [["q", "Schnell", (m) => m <= 10, "bis 10 min"], ["m", "Mittel", (m) => m > 10 && m <= 30, "10 bis 30 min"], ["l", "Länger", (m) => m > 30, "über 30 min"]];
const buckets = {};
function taskList(key, list) {
  const sorted = list.slice().sort((a, b) => a.minutes - b.minutes || score(b) - score(a));
  const counts = BUCKETS.map(([, , f]) => sorted.filter((t) => f(t.minutes)).length);
  let b = buckets[key];
  if (!b || !counts[BUCKETS.findIndex((x) => x[0] === b)]) b = buckets[key] = BUCKETS[Math.max(0, counts.findIndex((c) => c > 0))][0];
  const B = BUCKETS.find((x) => x[0] === b);
  const shown = sorted.filter((t) => B[2](t.minutes));
  return `<div class="seg">${BUCKETS.map(([id, l], i) => `<button data-bucket="${key}|${id}" class="${b === id ? "on" : ""}" ${counts[i] ? "" : 'style="opacity:.35"'}>${l}<small>${counts[i]}</small></button>`).join("")}</div>
    <div>${shown.map((t) => `<div class="tr">${pill(t)}
      <button style="flex:1;min-width:0" data-edit="${t.id}"><div class="t">${esc(t.title)}</div><div class="m">${AREAS[t.area].short}${dueTag(t) ? " · " + dueTag(t) : ""}${t.project && projOf(t.project) ? " · " + esc(projOf(t.project).title) : ""}</div></button>
      <button class="ck" data-ck="${t.id}" aria-label="Erledigt">${I("check")}</button></div>`).join("") || '<div class="hint">Hier ist nichts offen.</div>'}</div>
    ${shown.length ? `<div class="hint">Schnellstes zuerst · ${B[3]} · Tippen zum Ändern</div>` : ""}`;
}

let focusSets = {};
function pageHtml(key) {
  const [kind, arg] = key.includes(":") ? [key.slice(0, key.indexOf(":")), key.slice(key.indexOf(":") + 1)] : [key, ""];
  if (kind === "kinds") {
    const open = openTasks();
    return pgTop("Was gerade geht") + `<div class="pg-body"><div class="hint" style="text-align:left;padding:0 2px">Wo bist du gerade? Tippen zeigt die Liste, schnellstes zuerst.</div>
      <div class="kinds">${Object.entries(KINDS).map(([k, v]) => {
        const l = open.filter((t) => t.kind === k); const tot = l.reduce((s, t) => s + t.minutes, 0);
        const fast = l.length ? Math.min(...l.map((t) => t.minutes)) : 0;
        return `<button class="kt" data-go="kind:${k}"><svg class="art" viewBox="0 0 160 104">${ART[k]}</svg><div class="foot"><span class="cnt cond">${l.length}</span><div><div class="nm">${v.name}</div><div class="sm">${l.length ? `ab ${fast} min · ${fmtMin(tot)}` : "nichts offen"}</div></div></div>${mbar(l)}</button>`;
      }).join("")}</div></div>`;
  }
  if (kind === "areas") {
    const all = D.tasks.filter((t) => !t.done).length;
    return pgTop("Alles nach Bereich") + `<div class="pg-body"><div class="hint" style="text-align:left;padding:0 2px">${all} offen. Je größer die Fläche, desto mehr liegt dort.</div>${treemap()}</div>`;
  }
  if (kind === "kind") {
    const l = openTasks().filter((t) => t.kind === arg); const tot = l.reduce((s, t) => s + t.minutes, 0);
    return pgTop() + `<div class="pg-hero" style="background:var(--paper)"><svg class="art" viewBox="0 0 160 104">${ART[arg]}</svg><div class="k">Was gerade geht</div><h2>${KINDS[arg].name}</h2>
      <div class="goal">${l.length} Dinge · zusammen ${fmtMin(tot)}</div>${mbar(l)}</div><div class="pg-body">${taskList(key, l)}</div>`;
  }
  if (kind === "area") {
    const l = openTasks().filter((t) => t.area === arg);
    const projs = D.projects.filter((p) => p.area === arg && p.steps.some((s) => s.task && byId(s.task) && !byId(s.task).done));
    const waiting = D.tasks.filter((t) => t.area === arg && !t.done && (blocked(t) || held(t))).length;
    return pgTop() + `<div class="pg-hero" style="background:${AREAS[arg].c}"><div class="k">Bereich</div><h2>${AREAS[arg].name}</h2>
      <div class="goal">${l.length} jetzt machbar${waiting ? ` · ${waiting} warten noch` : ""}</div>${I(arg, "big")}</div>
      <div class="pg-body">${projs.length ? `<div class="pchips">${projs.map((p) => { const pr = projProgress(p.id); return `<button class="pchip" data-go="project:${p.id}">${ring(p.id)}<div><b>${esc(p.title)}</b><span>Vorhaben · ${pr.done}/${pr.all}</span></div></button>`; }).join("")}</div>` : ""}
      ${taskList(key, l)}</div>`;
  }
  if (kind === "day") {
    const o = off(arg);
    const l = D.tasks.filter((t) => !t.done && !blocked(t) && !held(t) && effDue(t) != null && Math.max(0, effDue(t)) === o);
    return pgTop(o === 0 ? "Heute fällig" : `${WDL[pd(arg).getDay()]}, ${pd(arg).getDate()}.`) + `<div class="pg-body">${taskList(key, l)}</div>`;
  }
  if (kind === "done") {
    const l = D.tasks.filter((t) => t.done).sort((a, b) => (b.doneAt || 0) - (a.doneAt || 0));
    const groups = {}; l.forEach((t) => { const g = t.doneAt ? dayLabel(t.doneAt) : "Früher"; (groups[g] = groups[g] || []).push(t); });
    return pgTop() + `<div class="pg-hero" style="background:var(--amber);min-height:130px"><div class="k">Erledigt</div><h2>${l.length} geschafft</h2>
      <div class="goal">Aus Versehen abgehakt? Mit »Zurück« ist es wieder offen.</div>${I("check", "big")}</div>
      <div class="pg-body">${Object.entries(groups).map(([g, ts]) => `<div class="grp">${g}</div><div>${ts.map((t) => `<div class="dr"><span class="bar" style="background:${AREAS[t.area].c}"></span>
        <div style="flex:1;min-width:0"><div class="t">${esc(t.title)}</div><div class="m">${AREAS[t.area].short}${t.doneAt ? " · " + hhmm(t.doneAt) : ""}</div></div>
        <button class="back" data-restore="${t.id}">Zurück</button></div>`).join("")}</div>`).join("") || '<div class="hint">Noch nichts erledigt.</div>'}</div>`;
  }
  if (kind === "focus") return focusHtml(key, arg);
  if (kind === "project") return projectHtml(arg);
  if (kind === "new") return newHtml(arg);
  return pgTop() + '<div class="hint">Seite nicht gefunden.</div>';
}

function focusHtml(key, startId) {
  if (!focusSets[key]) {
    const pick = fitFor(Math.max(minutes, byId(startId)?.minutes || 0)).map((t) => t.id);
    focusSets[key] = pick.includes(startId) ? [startId, ...pick.filter((x) => x !== startId)] : [startId];
  }
  const ts = focusSets[key].map(byId).filter(Boolean); const cur = ts.find((t) => !t.done && !held(t));
  const pips = `<div class="pips">${ts.map((t) => `<i class="${t.done ? "on" : ""}"></i>`).join("")}</div>`;
  if (!cur) return pgTop("Fokus") + `<div class="pg-body"><div class="won"><h3>Erledigt.<br>Stark.</h3><p>${ts.filter((t) => t.done).length} von ${ts.length} geschafft.</p><button class="btn" data-back>Zurück</button>${I("star", "big")}</div></div>`;
  const rest = ts.filter((t) => t !== cur && !t.done && !held(t));
  return pgTop("Fokus") + `<div class="pg-body"><div style="display:flex;justify-content:space-between;align-items:center"><span class="d">${ts.indexOf(cur) + 1} von ${ts.length}</span>${pips}</div>
    <div class="hero" id="hero" style="background:${AREAS[cur.area].c}"><div class="k">${AREAS[cur.area].name}</div><h3>${esc(cur.title)}</h3>
      <div class="chips">${[...why(cur).slice(0, 2), fmtMin(cur.minutes)].map((r) => `<span class="chip">${esc(r)}</span>`).join("")}</div>
      <div class="act"><button class="done" data-done="${cur.id}" aria-label="Erledigt">${I("check")}</button><button class="later" data-later="${cur.id}">Später</button>
      ${cur.minutes > 15 ? `<button class="later" data-split="${cur.id}" style="opacity:.7">Zerlegen</button>` : ""}</div>${I(cur.area, "big")}</div>
    ${rest.length ? `<div class="grp">Danach</div><div>${rest.map((t) => `<div class="tr">${pill(t)}<div class="t">${esc(t.title)}</div></div>`).join("")}</div>` : ""}</div>`;
}

function projectHtml(pid) {
  const p = projOf(pid);
  if (!p) return pgTop() + '<div class="hint">Dieses Vorhaben gibt es nicht mehr.</div>';
  const c = AREAS[p.area].c, pr = projProgress(pid), n = projNext(pid);
  let num = 0;
  const steps = p.steps.map((s) => {
    if (s.fixed) return `<div class="step"><span class="dot">${I("cal", "ic", 'style="width:16px;height:16px;stroke-width:2.4"')}</span><div><div class="t">${esc(s.title)}</div><div class="m">${WDL[pd(s.fixed).getDay()]}, ${pd(s.fixed).toLocaleDateString("de-DE", { day: "numeric", month: "long" })} · steht fest</div></div></div>`;
    const t = s.task ? byId(s.task) : null; if (s.task && !t) return "";
    num++;
    const done = t ? t.done : s.done; const title = t ? t.title : s.title;
    if (done) return `<div class="step ok"><span class="dot">${I("check")}</span><div class="t">${esc(title)}</div></div>`;
    if (t && t === n) return `<div class="step now"><span class="dot" style="background:${c};color:var(--ink)">${I("arrow", "ic", 'style="width:16px;height:16px;stroke-width:2.6"')}</span><div class="box"><div class="t">${esc(title)}</div><div class="m">Jetzt dran · ${fmtMin(t.minutes)}</div><button data-done="${t.id}">${I("check")}Erledigt</button></div></div>`;
    if (t && t.wait) return `<div class="step wait"><span class="dot">${I("lock", "ic", 'style="width:15px;height:15px;stroke-width:2.4"')}</span><div><div class="t">${esc(title)}</div><div class="m">${esc(t.wait)}</div></div></div>`;
    return `<div class="step"><span class="dot">${num}</span><button style="text-align:left" ${t ? `data-edit="${t.id}"` : ""}><div class="t">${esc(title)}</div><div class="m">${t ? fmtMin(t.minutes) : ""}</div></button></div>`;
  }).join("");
  return pgTop() + `<div class="pg-hero" style="background:${c}"><div class="k">Vorhaben · ${AREAS[p.area].name}</div><h2>${esc(p.title)}</h2>
    <div class="goal">${p.deadline ? `${esc(p.goal)}: ${WDL[pd(p.deadline).getDay()]}, ${pd(p.deadline).toLocaleDateString("de-DE", { day: "numeric", month: "long" })}` : "ohne feste Frist"}</div>
    <div class="pb"><b style="width:${pr.done / Math.max(1, pr.all) * 100}%"></b></div><div class="goal" style="padding-top:6px;font-weight:700">${pr.done} von ${pr.all} Schritten</div>${I(p.area, "big")}</div>
    <div class="pg-body"><div>${steps}</div>
    <div class="origin">Aus deinem Satz gebaut: <q>${esc(p.origin)}</q><br>Fehlt etwas? Einfach sagen: »Beim ${esc(p.title.split(" ")[0])} fehlt noch …«</div></div>`;
}

const captures = {};
function newHtml(cid) {
  const c = captures[cid];
  if (!c) {
    api("GET", `/tasks/capture/${cid}`).then((r) => { captures[cid] = r; render(); }).catch(() => { captures[cid] = { error: "offline", created: [] }; render(); });
    return pgTop("Neu angelegt") + '<div class="pg-body"><div class="spin"><i></i>Lädt …</div></div>';
  }
  const where = (t) => t.project ? `${t.project}, Schritt ${t.step}` : `${KINDS[t.kind].name}${t.due ? ", bis " + dueWord(off(t.due)) : ""}`;
  const items = c.created.filter((t) => byId(t.id));
  return pgTop("Neu angelegt") + `<div class="pg-body">${c.text ? `<div class="origin"><q>${esc(c.text)}</q></div>` : ""}
    ${c.fallback ? '<div class="hint" style="text-align:left">LM Studio lief nicht, einfache Regeln haben sortiert. Tippen zum Ändern.</div>' : ""}
    ${items.map((t, i) => `<div class="rc" style="animation-delay:${i * 0.08}s">${sq(t.area)}<button style="flex:1;min-width:0" data-edit="${t.id}"><div class="t">${esc(byId(t.id).title)}</div>
      <div class="m">${AREAS[byId(t.id).area].name} · ${fmtMin(byId(t.id).minutes)}</div>
      <span class="to" style="background:${AREAS[byId(t.id).area].c}">${I("arrow", "ic", 'style="width:13px;height:13px;stroke-width:2.6"')}${esc(where(t))}</span></button><button class="x" data-drop="${t.id}" aria-label="Entfernen">${I("x")}</button></div>`).join("")
      || `<div class="hint">${c.status === "failed" ? "Hikari hat in der Aufnahme nichts verstanden." : c.error === "offline" ? "Server nicht erreichbar." : "Nichts mehr da."}</div>`}</div>`;
}

/* ---------- Navigation ---------- */
let stack = ROOT ? [ROOT] : [];
function go(key) {
  if (!ROOT && N && N.open) { N.open(key); return; }
  stack.push(key);
  showPage(true);
}
function showPage(scrollTop) {
  const pg = $("#page");
  if (!stack.length) { pg.classList.remove("on"); render(); return; }
  pg.innerHTML = pageHtml(stack[stack.length - 1]);
  pg.classList.add("on");
  if (scrollTop) pg.scrollTop = 0;
}
function back() {
  if ($("#sheet").classList.contains("on")) { closeSheet(); return true; }
  if (stack.length > (ROOT ? 1 : 0)) { stack.pop(); showPage(true); return true; }
  return false;
}
window.__hkBack = back;

function render() {
  if (ROOT) {
    $("#app").innerHTML = "";
    $("#fab").classList.add("hide");
    $("#page").classList.add("still");
    showPage(false);
    return;
  }
  $("#app").innerHTML = `<div class="wrap">${viewHome()}</div>`;
  $("#fab").classList.remove("hide");
  if (stack.length) $("#page").innerHTML = pageHtml(stack[stack.length - 1]);
}

/* ---------- Aktionen ---------- */
let undo = null;
function toast(msg, fn) {
  const el = $("#toast"); undo = fn;
  el.innerHTML = `<span>${esc(msg)}</span>${fn ? "<button data-undo>Rückgängig</button>" : ""}`;
  el.classList.add("on"); clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove("on"), 3600);
}
function patch(t, body, okMsg, undoFn) {
  return api("PATCH", `/tasks/${t.id}`, body).then((r) => { Object.assign(t, r); changed(); render(); if (okMsg) toast(okMsg, undoFn); }).catch(failed);
}
function complete(id) {
  const t = byId(id); if (!t) return;
  const go2 = () => {
    t.done = true; t.doneAt = Date.now(); changed(); render();
    const n = t.project && projNext(t.project);
    patch(t, { done: true }, n ? `Erledigt. Als Nächstes: ${n.title}` : "Erledigt", () => { t.done = false; t.doneAt = null; render(); patch(t, { done: false }); });
  };
  const hero = $("#hero");
  if (hero) { hero.classList.add("out"); setTimeout(go2, 280); } else go2();
}
function snooze(id, days, label) {
  const t = byId(id); closeSheet();
  const prev = { holdUntil: t.holdUntil, later: t.later };
  t.holdUntil = dayIso(days - 1); t.later = (t.later || 0) + 1; render();
  patch(t, { holdUntil: t.holdUntil, later: t.later }, label, () => { Object.assign(t, prev); render(); patch(t, prev); });
}
function drop(id) {
  const t = byId(id); closeSheet();
  D.tasks = D.tasks.filter((x) => x !== t);
  D.projects.forEach((p) => { p.steps = p.steps.filter((s) => s.task !== id); });
  render();
  api("DELETE", `/tasks/${id}`).then(() => { changed(); toast("Gestrichen"); }).catch(failed);
}

document.addEventListener("click", (e) => {
  const b = e.target.closest("button"); if (!b) return;
  const d = b.dataset;
  if (d.go) go(d.go);
  else if (d.back !== undefined) { if (!back()) N?.close?.(); }
  else if (d.min) { minutes = +d.min; store.set("tasks_minutes", minutes); render(); }
  else if (d.bucket) { const [k, id] = d.bucket.split("|"); buckets[k] = id; showPage(false); }
  else if (d.ck) { const tr = b.closest(".tr"); b.style.cssText = "background:var(--amber);border-color:var(--amber);color:var(--ink)"; if (tr) tr.style.cssText = "transition:.3s;opacity:0;transform:translateX(30px)"; setTimeout(() => complete(d.ck), 260); }
  else if (d.done) complete(d.done);
  else if (d.restore) { const t = byId(d.restore); t.done = false; t.doneAt = null; t.holdUntil = null; render(); patch(t, { done: false, holdUntil: null }, `Wieder offen: ${t.title}`, () => complete(t.id)); }
  else if (d.later) laterSheet(d.later);
  else if (d.split) splitSheet(d.split);
  else if (d.snooze) snooze(d.sid, +d.snooze, d.lbl);
  else if (d.drop) drop(d.drop);
  else if (d.edit) editSheet(d.edit);
  else if (d.ea) editPick(d.ea, d.v);
  else if (d.save !== undefined) saveEdit();
  else if (d.splitgo) doSplit(d.splitgo);
  else if (d.type !== undefined) typeSheet();
  else if (d.send !== undefined) sendText();
  else if (d.close !== undefined) closeSheet();
  else if (d.undo !== undefined) { $("#toast").classList.remove("on"); undo && undo(); }
});
$("#fab").innerHTML = I("mic");
$("#fab").onclick = () => (N && N.record ? N.record() : typeSheet());
$("#scrim").onclick = () => closeSheet();

/* ---------- Sheets ---------- */
function openSheet(html) {
  const sh = $("#sheet"); sh.innerHTML = `<div class="grab"></div>${html}`;
  if (sh.classList.contains("on")) return; // schon offen: nur Inhalt tauschen
  requestAnimationFrame(() => { sh.classList.add("on"); $("#scrim").classList.add("on"); });
}
function closeSheet() { $("#sheet").classList.remove("on"); $("#scrim").classList.remove("on"); }
function laterSheet(id) {
  const t = byId(id); const n = t.later || 0;
  openSheet(`<div class="sh"><h3>Wann dann?</h3><div class="sub">${esc(t.title)}</div>
    ${n >= 2 ? `<div class="origin" style="margin-top:12px">Schon ${n} Mal geschoben. Vielleicht zu groß oder nicht mehr wichtig?</div>` : ""}
    <div class="opts"><button data-snooze="1" data-sid="${id}" data-lbl="Auf morgen geschoben">Morgen<span>${WDL[dOf(1).getDay()]}</span></button>
    <button data-snooze="${untilWd(6)}" data-sid="${id}" data-lbl="Aufs Wochenende geschoben">Wochenende<span>Samstag</span></button>
    <button data-snooze="${untilWd(1)}" data-sid="${id}" data-lbl="Auf nächste Woche geschoben">Nächste Woche<span>Montag</span></button>
    <button data-snooze="3" data-sid="${id}" data-lbl="In drei Tagen wieder da">In 3 Tagen<span>${WDL[dOf(3).getDay()]}</span></button>
    <button class="wide" data-split="${id}">${I("split")}<div>In Schritte zerlegen<span>macht es kleiner</span></div></button>
    <button class="wide" data-drop="${id}">${I("x")}<div>Streichen<span>muss nicht mehr</span></div></button></div></div>`);
}
let splitSteps = null;
function splitSheet(id) {
  const t = byId(id); splitSteps = null;
  openSheet(`<div class="sh"><h3>In kleine Schritte</h3><div class="sub">${esc(t.title)}</div><div id="splitBox"><div class="spin"><i></i>Hikari überlegt …</div></div></div>`);
  api("POST", `/tasks/${id}/split-suggest`).then((r) => {
    splitSteps = r.steps;
    const box = $("#splitBox"); if (!box) return;
    box.innerHTML = `<div style="margin-top:8px">${r.steps.map((s, i) => `<div class="tr"><span class="pill" style="background:${AREAS[t.area].c};width:34px;height:34px;border-radius:50%"><b style="font-size:15px">${i + 1}</b></span><div style="flex:1"><div class="t">${esc(s.title)}</div><div class="m">${fmtMin(s.minutes)}</div></div></div>`).join("")}</div>
      <button class="btnw" data-splitgo="${id}">So machen</button><button class="btng" data-close>Abbrechen</button>`;
  }).catch(() => { const box = $("#splitBox"); if (box) box.innerHTML = '<div class="hint">Server nicht erreichbar.</div>'; });
}
function doSplit(id) {
  if (!splitSteps) return;
  closeSheet();
  api("POST", `/tasks/${id}/split`, { steps: splitSteps }).then((r) => {
    Object.keys(focusSets).forEach((k) => { focusSets[k] = focusSets[k].map((x) => (x === id ? r.tasks[0] : x)); });
    toast("Zerlegt. Der erste Schritt steht jetzt oben"); changed(); load();
  }).catch(failed);
}

/* Ändern: Bereich, Ort, Dauer, Frist, Titel — die KI darf sich irren. */
let edit = null;
function editSheet(id) {
  const t = byId(id); if (!t) return;
  edit = { id, title: t.title, area: t.area, kind: t.kind, minutes: t.minutes, due: t.due };
  renderEdit();
}
function renderEdit() {
  const e = edit; const t = byId(e.id);
  const row = (k, items) => `<div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px">${items.map(([v, l, c]) => `<button data-ea="${k}" data-v="${v}" style="padding:8px 11px;border-radius:9px;font-size:13.5px;font-weight:700;${String(e[k]) === String(v) ? `background:${c || "#fff"};color:var(--ink)` : "background:var(--surf);color:#ffffffb0"}">${l}</button>`).join("")}</div>`;
  const dues = [["", "keine"], [dayIso(0), "heute"], [dayIso(1), "morgen"], ...[2, 3, 4, 5, 6].map((o) => [dayIso(o), WD[dOf(o).getDay()]]), [dayIso(untilWd(1) + 7), "nächste Woche"]];
  if (e.due && !dues.some(([v]) => v === e.due)) dues.push([e.due, dueWord(off(e.due))]);
  openSheet(`<div class="sh"><textarea id="et" style="min-height:64px;margin-top:0;font-weight:700">${esc(e.title)}</textarea>
    <div class="grp">Bereich</div>${row("area", Object.entries(AREAS).map(([k, a]) => [k, a.short, a.c]))}
    <div class="grp">Wo</div>${row("kind", Object.entries(KINDS).map(([k, v]) => [k, v.name]))}
    <div class="grp">Dauer</div>${row("minutes", [5, 10, 15, 20, 30, 45, 60, 90, 120].map((m) => [m, fmtMin(m)]))}
    ${t.project ? "" : `<div class="grp">Frist</div>${row("due", dues)}`}
    <button class="btnw" data-save>Speichern</button>
    <div class="opts" style="margin-top:8px"><button data-later="${e.id}">Später<span>schieben</span></button><button data-drop="${e.id}">Streichen<span>muss nicht mehr</span></button></div></div>`);
}
function editPick(k, v) {
  edit.title = $("#et")?.value ?? edit.title;
  edit[k] = k === "minutes" ? +v : v === "" ? null : v;
  const sc = $("#sheet").scrollTop; renderEdit(); $("#sheet").scrollTop = sc;
}
function saveEdit() {
  const t = byId(edit.id); edit.title = ($("#et")?.value || edit.title).trim();
  const body = { title: edit.title, area: edit.area, kind: edit.kind, minutes: edit.minutes };
  if (!t.project) body.due = edit.due;
  Object.assign(t, body); closeSheet(); render();
  patch(t, body, "Gespeichert");
}

/* Tippen statt sprechen (im Browser auch fürs Mikrofon). */
function typeSheet() {
  openSheet(`<div class="sh"><h3>Einfach hinschreiben</h3><div class="sub">Mehrere Sachen in einem Satz gehen auch. Hikari sortiert im Hintergrund.</div>
    <textarea id="ta" placeholder="z. B. morgen Steuerberater anrufen und bis Freitag Flug buchen"></textarea>
    <button class="btnw" data-send>Fertig</button><button class="btng" data-close>Abbrechen</button></div>`);
  setTimeout(() => $("#ta")?.focus(), 80);
}
function sendText() {
  const v = $("#ta")?.value.trim(); if (!v) return;
  closeSheet();
  if (N && N.captureText) N.captureText(v);
  else api("POST", "/tasks/capture", { text: v, source: "text" }).catch(failed);
  D.pending = (D.pending || 0) + 1; render();
  toast("Aufgenommen. Hikari sortiert im Hintergrund");
  clearTimeout(pollT); pollT = setTimeout(load, 3000);
}

render();
load();
