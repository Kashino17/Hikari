// Kindle-artiger Leser für Hikari (EPUB). Seitenumbruch und Spalten macht foliate-js;
// die App (EpubReaderScreen.kt) steuert über window.HikariReader und hört über window.HikariBridge.
import './vendor/foliate-js/view.js'
import { Overlayer } from './vendor/foliate-js/overlayer.js'

const ORIGIN = 'https://appassets.androidplatform.net'
const THEMES = {
  papier: { bg: '#F7F3EA', fg: '#1E1B16', link: '#7A4E14' },
  sepia: { bg: '#F1E3C6', fg: '#3B2F1E', link: '#7A4E14' },
  nacht: { bg: '#111111', fg: '#CFCAC0', link: '#D4A373' },
}
const FONTS = {
  literata: "'Literata', Georgia, serif",
  serif: "Georgia, 'Noto Serif', serif",
  sans: "system-ui, 'Roboto', sans-serif",
}
const MARGINS = { schmal: '3%', mittel: '6%', breit: '10%' }
const MARK_COLORS = { gelb: 'rgba(250, 204, 21, .45)', gruen: 'rgba(74, 222, 128, .40)', blau: 'rgba(96, 165, 250, .40)', rosa: 'rgba(244, 114, 182, .40)' }

const bridge = window.HikariBridge ?? { onReady() {}, onRelocate() {}, onToc() {}, onSelection() {}, onTapCenter() {}, onError(m) { console.error(m) }, onTapMark() {} }
const send = (fn, v) => { try { bridge[fn](typeof v === 'string' ? v : JSON.stringify(v ?? null)) } catch (e) { console.error(e) } }
const $msg = document.getElementById('msg')

let view = null
let settings = { theme: 'papier', font: 'literata', size: 19, lineHeight: 1.5, margin: 'mittel', columns: 1 }
let marks = new Map() // cfi -> { id, color }

function fontFaces() {
  // Absolute Adressen: das Buch läuft in einem eigenen Rahmen (blob:), relative Pfade gingen ins Leere.
  return ['normal 400', 'normal 600', 'italic 400'].flatMap((sw) => {
    const [style, weight] = sw.split(' ')
    return ['latin', 'latin-ext'].map((sub) =>
      `@font-face{font-family:'Literata';font-style:${style};font-weight:${weight};src:url(${ORIGIN}/fonts/literata-${style}-${weight}-${sub}.woff2) format('woff2');}`)
  }).join('\n')
}

function bookCss() {
  const t = THEMES[settings.theme] ?? THEMES.papier
  return `${fontFaces()}
    @namespace epub "http://www.idpf.org/2007/ops";
    html { color-scheme: ${settings.theme === 'nacht' ? 'dark' : 'light'}; }
    html, body { background: ${t.bg} !important; color: ${t.fg} !important; }
    body * { color: inherit !important; background-color: transparent !important; }
    body { font-family: ${FONTS[settings.font] ?? FONTS.literata} !important; font-size: ${settings.size}px !important; }
    p, li, blockquote, dd, div { line-height: ${settings.lineHeight} !important; }
    p { text-align: justify; hyphens: auto; -webkit-hyphens: auto; widows: 2; orphans: 2; }
    a:link, a:visited { color: ${t.link} !important; }
    img, svg { max-width: 100% !important; height: auto !important; }
    ${settings.theme === 'nacht' ? 'img { filter: brightness(.85); }' : ''}
    pre { white-space: pre-wrap !important; }
    aside[epub|type~="footnote"], aside[epub|type~="endnote"], aside[epub|type~="note"] { display: none; }`
}

function applyLayout() {
  if (!view) return
  const t = THEMES[settings.theme] ?? THEMES.papier
  document.documentElement.style.setProperty('--bg', t.bg)
  document.documentElement.style.setProperty('--fg', t.fg)
  view.renderer.setAttribute('flow', 'paginated')
  view.renderer.setAttribute('gap', MARGINS[settings.margin] ?? MARGINS.mittel)
  view.renderer.setAttribute('max-column-count', String(settings.columns === 2 ? 2 : 1))
  view.renderer.setAttribute('max-inline-size', settings.columns === 2 ? '640px' : '760px')
  view.renderer.setStyles?.(bookCss())
}

function flatToc(items, depth = 0, out = []) {
  for (const i of items ?? []) {
    out.push({ label: (i.label ?? '').trim(), href: i.href, depth })
    flatToc(i.subitems, depth + 1, out)
  }
  return out
}

function hookDoc(doc, index) {
  // Tippen und Wischen erkennen wir selbst; die App entscheidet, was passiert (Tippbereiche, Menü,
  // Blätter-Animation). foliate bekommt Tippen und Wischen nicht mehr: sonst blättern beide zugleich,
  // und foliate bleibt gesperrt (am Gerät gesehen, 19.11.). Lange Drücke bleiben für die Auswahl.
  const W = () => window.innerWidth || 1
  const frameLeft = () => doc.defaultView?.frameElement?.getBoundingClientRect().left ?? 0
  const tapAt = (clientX, target, big) => {
    if (target?.closest?.('a[href]')) return
    const sel = doc.getSelection()
    if (sel && !sel.isCollapsed) return
    const px = frameLeft() + clientX
    send('onTap', { x: px / W(), edge: px < 10 || px > W() - 10, big: !!big })
  }
  let touch = null
  let drag = null
  let lastTouchTap = 0
  let pendingMove = null
  const flushMove = () => { if (pendingMove != null) { send('onDrag', { phase: 'move', dx: pendingMove }); pendingMove = null } }
  doc.addEventListener('touchstart', (e) => {
    const t = e.changedTouches[0]
    if (e.touches.length > 1) {
      // zweiter Finger: kein Tippen, laufendes Wischen abbrechen
      if (drag) { send('onDrag', { phase: 'cancel' }); drag = null }
      touch = null
      return
    }
    touch = t ? { x: t.screenX, y: t.screenY, cx: t.clientX, at: e.timeStamp, target: e.target, r: Math.max(t.radiusX || 0, t.radiusY || 0) } : null
  }, { capture: true, passive: true })
  doc.addEventListener('touchmove', (e) => {
    if (!touch || e.touches.length !== 1) return
    const t = e.touches[0]
    const dx = t.screenX - touch.x
    const dy = t.screenY - touch.y
    if (!drag) {
      const sel = doc.getSelection()
      if (sel && !sel.isCollapsed) return
      if (Math.abs(dx) < 12 || Math.abs(dx) < Math.abs(dy) * 1.3) return
      drag = { lastX: t.screenX, lastT: e.timeStamp, vx: 0 }
      send('onDrag', { phase: 'start', dir: dx < 0 ? 1 : -1 })
    }
    e.preventDefault()
    e.stopImmediatePropagation()
    const dt = Math.max(1, e.timeStamp - drag.lastT)
    drag.vx = (t.screenX - drag.lastX) / dt
    drag.lastX = t.screenX
    drag.lastT = e.timeStamp
    if (pendingMove == null) requestAnimationFrame(flushMove)
    pendingMove = dx / W()
  }, { capture: true, passive: false })
  doc.addEventListener('touchend', (e) => {
    const t = e.changedTouches[0]
    const start = touch
    touch = null
    if (drag) {
      e.stopImmediatePropagation()
      flushMove()
      send('onDrag', { phase: 'end', dx: start && t ? (t.screenX - start.x) / W() : 0, vx: drag.vx })
      drag = null
      return
    }
    if (!start || !t) return
    const moved = Math.hypot(t.screenX - start.x, t.screenY - start.y)
    if (moved > 10 || e.timeStamp - start.at > 300) return // gerutscht oder gehalten: kein Tippen
    e.stopImmediatePropagation()
    lastTouchTap = Date.now()
    tapAt(start.cx, start.target, start.r > 30)
  }, { capture: true })
  doc.addEventListener('touchcancel', () => {
    if (drag) send('onDrag', { phase: 'cancel' })
    drag = null
    touch = null
  }, { capture: true })
  doc.addEventListener('click', (e) => {
    if (Date.now() - lastTouchTap < 600) return // schon als Tippen erledigt
    tapAt(e.clientX, e.target, false)
  })
  let selTimer = null
  doc.addEventListener('selectionchange', () => {
    clearTimeout(selTimer)
    selTimer = setTimeout(() => {
      const sel = doc.getSelection()
      if (!sel || sel.isCollapsed || !sel.rangeCount) return send('onSelection', null)
      const range = sel.getRangeAt(0)
      const text = sel.toString().trim()
      if (!text) return
      send('onSelection', { cfi: view.getCFI(index, range), text: text.slice(0, 2000) })
    }, 350)
  })
}

async function open(url, startCfi, settingsJson) {
  try {
    if (settingsJson) settings = { ...settings, ...JSON.parse(settingsJson) }
    $msg.textContent = 'Buch wird geöffnet …'
    view = document.createElement('foliate-view')
    document.body.append(view)
    view.addEventListener('load', (e) => hookDoc(e.detail.doc, e.detail.index))
    view.addEventListener('relocate', (e) => {
      relocations++
      const d = e.detail
      send('onRelocate', {
        cfi: d.cfi,
        fraction: d.fraction ?? 0,
        chapter: d.tocItem?.label?.trim() ?? '',
        minutesLeftInChapter: Math.max(0, Math.round(d.time?.section ?? 0)),
        page: d.location?.current ?? 0,
        pages: d.location?.total ?? 0,
      })
    })
    view.addEventListener('create-overlay', (e) => {
      for (const [cfi, m] of marks) if (view) view.addAnnotation({ value: cfi, ...m }).catch(() => {})
    })
    view.addEventListener('draw-annotation', (e) => {
      const { draw, annotation } = e.detail
      draw(Overlayer.highlight, { color: MARK_COLORS[annotation.color] ?? MARK_COLORS.gelb })
    })
    view.addEventListener('show-annotation', (e) => {
      const m = marks.get(e.detail.value)
      if (m) send('onTapMark', { id: m.id, cfi: e.detail.value })
    })
    await view.open(url)
    // Kaputte Einzelteile eines Buchs dürfen das Öffnen nicht verhindern.
    view.book.transformTarget?.addEventListener('data', ({ detail }) => {
      detail.data = Promise.resolve(detail.data).catch(() => '')
    })
    applyLayout()
    await view.init({ lastLocation: startCfi || null, showTextStart: !startCfi })
    $msg.textContent = ''
    send('onToc', flatToc(view.book.toc))
    send('onReady', { title: view.book.metadata?.title ?? '' })
  } catch (e) {
    $msg.textContent = 'Dieses Buch lässt sich nicht öffnen.'
    send('onError', String(e?.message ?? e))
  }
}

let relocations = 0
const frames = (n) => new Promise((r) => { const step = () => (n-- <= 0 ? r() : requestAnimationFrame(step)); step() })

window.HikariReader = {
  open,
  /** Sofort (ohne foliate-Animation) umblättern und melden, ob sich etwas bewegt hat — die App blendet dabei ihre eigene Animation ein. */
  async turn(dir) {
    const before = relocations
    try { await (dir > 0 ? view?.next() : view?.prev()) } catch (e) { console.warn('turn', e) }
    await frames(2)
    send('onTurned', { moved: relocations !== before })
  },
  next: () => view?.goRight(),
  prev: () => view?.goLeft(),
  goTo: (target) => view?.goTo(target).catch((e) => send('onError', String(e))),
  goToFraction: (f) => view?.goToFraction(Number(f)),
  applySettings(json) {
    settings = { ...settings, ...JSON.parse(json) }
    applyLayout()
  },
  setMarks(json) {
    const list = JSON.parse(json) // [{id, cfi, color}]
    const next = new Map(list.map((m) => [m.cfi, { id: m.id, color: m.color }]))
    for (const [cfi, m] of marks) if (!next.has(cfi)) view?.deleteAnnotation({ value: cfi, ...m })
    for (const [cfi, m] of next) if (!marks.has(cfi)) view?.addAnnotation({ value: cfi, ...m }).catch(() => {})
    marks = next
  },
  clearSelection() {
    for (const { doc } of view?.renderer.getContents?.() ?? []) doc.getSelection()?.removeAllRanges()
  },
}
send('onReady', { boot: true })
