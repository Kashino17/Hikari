// Routen der Unterseiten. Spezifische Muster stehen vor allgemeinen.
import { html } from '../vendor/preact-htm.js';
import { Session } from './screens/session.js';
import { Phrasebook, Deepdive } from './screens/phrasebook.js';
import { PatternList, Pattern } from './screens/patterns.js';
import { PodcastHome, PodcastLesson } from './screens/podcast.js';
import { Blitz, Pairs } from './screens/games.js';
import { Reflex, Kombi, Blind, Talk } from './screens/speakmodes.js';
import { LabList, LabSession } from './screens/lab.js';
import { Journey } from './screens/journey.js';
import { StatsVocab, StatsTime, StatsWins } from './screens/stats.js';

const soon = (name) => () => html`<div class="screen"><div class="empty" style="margin-top:120px"><b>${name}</b>Wird gerade gebaut.</div></div>`;

export const ROUTES = {
  tab: { satzbuch: Phrasebook },
  list: [
    [/^ich\/wortschatz$/, () => html`<${StatsVocab} />`],
    [/^ich\/lernzeit$/, () => html`<${StatsTime} />`],
    [/^ich\/erfolge$/, () => html`<${StatsWins} />`],
    [/^anja$/, () => html`<${Journey} />`],
    [/^blitz$/, () => html`<${Blitz} />`],
    [/^pairs$/, () => html`<${Pairs} />`],
    [/^reflex$/, () => html`<${Reflex} />`],
    [/^kombi$/, () => html`<${Kombi} />`],
    [/^blind$/, () => html`<${Blind} />`],
    [/^talk$/, () => html`<${Talk} />`],
    [/^sounds$/, () => html`<${LabList} />`],
    [/^sounds\/(\w+)$/, (m) => html`<${LabSession} id=${m[1]} />`],
    [/^podcast$/, () => html`<${PodcastHome} />`],
    [/^podcast\/(\d+)$/, (m) => html`<${PodcastLesson} day=${+m[1]} />`],
    [/^deep\/(.+)$/, (m) => html`<${Deepdive} id=${m[1]} key=${m[1]} />`],
    [/^patterns$/, () => html`<${PatternList} />`],
    [/^pattern\/(.+)$/, (m) => html`<${Pattern} id=${m[1]} />`],
    [/^lesson\/(\d+)$/, (m) => html`<${Session} mode="LESSON" day=${+m[1]} />`],
    [/^dialog\/(\d+)$/, (m) => html`<${Session} mode="DIALOG" day=${+m[1]} />`],
    [/^talkday\/(\d+)$/, (m) => html`<${Session} mode="TALKDAY" day=${+m[1]} key=${`t${m[1]}`} />`],
    [/^podcastPractice\/(\d+)$/, (m) => html`<${Session} mode="PODCAST" day=${+m[1]} />`],
    [/^review$/, () => html`<${Session} mode="REVIEW" day=${1} />`],
    [/^test$/, () => html`<${Session} mode="TEST" day=${0} />`],
  ],
};
