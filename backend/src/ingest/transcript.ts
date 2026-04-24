export function parseVtt(vtt: string): string {
  const lines = vtt.split(/\r?\n/);
  const text: string[] = [];
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith("WEBVTT")) continue;
    if (/^\d+$/.test(line)) continue;
    if (/-->/.test(line)) continue;
    if (line.startsWith("NOTE ")) continue;
    text.push(line);
  }
  return text.join(" ");
}

export async function fetchTranscript(url: string): Promise<string | null> {
  const res = await fetch(url);
  if (!res.ok) return null;
  return parseVtt(await res.text());
}
