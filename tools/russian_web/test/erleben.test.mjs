// Lieder & Szenen: Vorschlag der Sätze zum Mitnehmen.
import test from 'node:test';
import assert from 'node:assert/strict';

// erleben.js hängt an der Oberfläche (preact) — hier wird nur die reine Auswahl geprüft.
const src = await import('node:fs').then((fs) => fs.readFileSync(new URL('../../../android/app/src/main/assets/russian-web/js/screens/erleben.js', import.meta.url), 'utf8'));
const Ph = await import('../../../android/app/src/main/assets/russian-web/js/domain/phonetics.js');
const body = src.slice(src.indexOf('export function suggestTake'), src.indexOf('function prepare(p)')).replace('export function', 'function');
const suggestTake = new Function('Ph', `${body}; return suggestTake;`)(Ph);

const line = (i, ru, g) => ({ i, ru, g });

test('ganze Sätze vor Fetzen, Namen zuletzt', () => {
  const uniq = [
    line(0, 'Впр+очем, спор о том,', ['Wie dem auch sei', 'die Debatte', 'über', '=']),
    line(1, 'Констант+ин Корн+ей, вот так.', ['Name', 'Name', 'genau', 'so']),
    line(2, 'Я не зн+аю, что д+елать.', ['ich', 'nicht', 'weiß', 'was', 'tun']),
    line(3, '+Это +очень хорош+о.', ['das', 'sehr', 'gut']),
    line(4, 'Мы ж+ивём в +интересное вр+емя.', ['wir', 'leben', 'in', 'interessanter', 'Zeit']),
    line(5, 'Да.', ['ja']),
  ];
  assert.deepEqual(suggestTake(uniq), [2, 3, 4]);
});

test('weniger Zeilen als gewünscht: alle, in Reihenfolge', () => {
  assert.deepEqual(suggestTake([line(3, 'Прив+ет!', ['hallo']), line(1, 'Как дел+а?', ['wie', 'Dinge'])]), [1, 3]);
});
