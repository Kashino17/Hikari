"""Kostenlose Cloud-Modelle für die Prüfinstanz — mehrere Anbieter in Reihe.

Ist ein Anbieter am Tageslimit, gedrosselt oder nicht erreichbar, geht die Frage an den nächsten.
Alle sprechen dieselbe Schnittstelle (OpenAI-Format: POST <base>/chat/completions).

  providers.json                 Reihenfolge, Adresse, Modell, Tageslimit — keine Geheimnisse
  ~/.hikari/erleben/keys.env     die Schlüssel, eine Zeile je Anbieter:  GEMINI_API_KEY=...
  ~/.hikari/erleben/usage.json   wie viele Anfragen heute an wen gingen

Prüfen, ob alles stimmt (Schlüssel da? Modell vorhanden? Antwort kommt?):
  python3 tools/erleben/cloud.py check
"""
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HOME = Path.home() / ".hikari" / "erleben"
KEYS = HOME / "keys.env"
USAGE = HOME / "usage.json"
PROVIDERS = Path(__file__).with_name("providers.json")
# Wie lange ein Anbieter nach einem Fehler übersprungen wird.
PAUSE_LIMIT_S = 3600      # Tageslimit oder Drosselung (429/402)
PAUSE_ERROR_S = 120       # Störung beim Anbieter (5xx, Zeitüberschreitung)


def load_keys() -> dict[str, str]:
    keys = {}
    if KEYS.exists():
        for line in KEYS.read_text().splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                keys[k.strip()] = v.strip().strip('"\'')
    return keys


class NoProvider(RuntimeError):
    """Kein Anbieter konnte antworten — die Aufbereitung wartet dann, statt ungeprüft weiterzumachen."""


class CloudBackend:
    def __init__(self, only: str | None = None, log=lambda *a: None):
        self.log = log
        keys = load_keys()
        self.providers = []
        for p in json.loads(PROVIDERS.read_text())["providers"]:
            if only and p["name"] != only:
                continue
            key = keys.get(p["key"])
            if key:
                self.providers.append({**p, "secret": key})
        self.blocked: dict[str, float] = {}
        self.name = "cloud:" + ("+".join(p["name"] for p in self.providers) or "kein Schlüssel")
        self.used: dict[str, int] = {}

    # ── Tageszähler ──────────────────────────────────────────────────────────

    def _usage(self) -> dict:
        try:
            u = json.loads(USAGE.read_text())
        except Exception:  # noqa: BLE001
            u = {}
        day = datetime.now(ZoneInfo("America/Los_Angeles")).strftime("%Y-%m-%d")   # Googles Zähltag
        return u if u.get("day") == day else {"day": day, "count": {}}

    def _count(self, name: str):
        u = self._usage()
        u["count"][name] = u["count"].get(name, 0) + 1
        HOME.mkdir(parents=True, exist_ok=True)
        USAGE.write_text(json.dumps(u))
        self.used[name] = self.used.get(name, 0) + 1

    def available(self) -> list[dict]:
        u = self._usage()["count"]
        now = time.time()
        return [p for p in self.providers if self.blocked.get(p["name"], 0) < now and u.get(p["name"], 0) < p.get("perDay", 10**9)]

    # ── Anfrage ──────────────────────────────────────────────────────────────

    def _call(self, p: dict, system: str, user: str, max_tokens: int) -> str:
        body = {"model": p["model"], "temperature": 0, "max_tokens": max_tokens,
                "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}], **p.get("extra", {})}
        req = urllib.request.Request(p["base"].rstrip("/") + "/chat/completions", data=json.dumps(body).encode(),
                                     headers={"content-type": "application/json", "authorization": f"Bearer {p['secret']}"})
        with urllib.request.urlopen(req, timeout=p.get("timeout", 60)) as r:
            data = json.loads(r.read())
        text = (data.get("choices") or [{}])[0].get("message", {}).get("content")
        if not isinstance(text, str) or not text.strip():
            raise ValueError("leere Antwort")
        return text

    def ask(self, system: str, user: str, max_tokens: int = 120) -> str:
        if not self.providers:
            raise NoProvider(f"Kein Schlüssel in {KEYS} — siehe Kopf von cloud.py")
        for p in self.available():
            try:
                # Manche Modelle rechnen ihr Nachdenken auf die Antwortlänge an — etwas Luft lassen.
                out = self._call(p, system, user, max_tokens + p.get("headroom", 0))
                self._count(p["name"])
                return out
            except urllib.error.HTTPError as e:
                limit = e.code in (402, 429)
                self.blocked[p["name"]] = time.time() + (PAUSE_LIMIT_S if limit else PAUSE_ERROR_S)
                self.log(f"{p['name']}: HTTP {e.code} — {'Limit erreicht' if limit else 'Störung'}, nächster Anbieter")
            except Exception as e:  # noqa: BLE001 — Zeitüberschreitung, Netz, leere Antwort
                self.blocked[p["name"]] = time.time() + PAUSE_ERROR_S
                self.log(f"{p['name']}: {type(e).__name__} {e} — nächster Anbieter")
        raise NoProvider("Alle Anbieter sind am Limit oder gestört")


def check():
    """Für jeden Anbieter: Schlüssel vorhanden, Modell in der Liste des Anbieters, eine Probe-Antwort."""
    keys = load_keys()
    for p in json.loads(PROVIDERS.read_text())["providers"]:
        if not keys.get(p["key"]):
            print(f"{p['name']:12} kein Schlüssel ({p['key']} fehlt in {KEYS}) — holen: {p['signup']}")
            continue
        try:
            req = urllib.request.Request(p["base"].rstrip("/") + "/models", headers={"authorization": f"Bearer {keys[p['key']]}"})
            ids = [m.get("id", "") for m in json.loads(urllib.request.urlopen(req, timeout=30).read()).get("data", [])]
            known = any(i == p["model"] or i.endswith("/" + p["model"]) for i in ids)
            note = "Modell vorhanden" if known else f"Modell »{p['model']}« NICHT in der Liste — verfügbar z. B.: {', '.join(ids[:8])}"
        except Exception as e:  # noqa: BLE001
            note = f"Modell-Liste nicht lesbar ({type(e).__name__})"
        b = CloudBackend(only=p["name"])
        try:
            t = time.time()
            ans = b.ask("Answer with exactly one word.", "Translate to German: кошка", 12)
            print(f"{p['name']:12} antwortet in {time.time() - t:.1f} s: {ans.strip()[:40]!r} — {note}")
        except Exception as e:  # noqa: BLE001
            print(f"{p['name']:12} antwortet NICHT ({e}) — {note}")


if __name__ == "__main__":
    if sys.argv[1:] == ["check"]:
        check()
    else:
        print(__doc__)
