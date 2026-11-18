// Ersatz für node:fs im Handy: Die Gesprächssteuerung schreibt nur ihren Tageszähler — der landet
// im Speicher der Russisch-Oberfläche (globalThis.__anjaStore, gesetzt von createLocalAnja).
const store = () => globalThis.__anjaStore;
export const existsSync = () => true;
export const mkdirSync = () => {};
export function readFileSync(path) {
  const v = store()?.get(path);
  if (v == null) throw new Error(`ENOENT: ${path}`);
  return v;
}
export function writeFileSync(path, data) {
  store()?.set(path, String(data));
}
export default { existsSync, mkdirSync, readFileSync, writeFileSync };
