#!/usr/bin/env python3
"""Holt die kuratierten Rezepte von lecker.de (schema.org-Recipe in der Seite) für den privaten Gebrauch.
Liste: backend/fitness/lecker_picked.txt (»gruppe url« je Zeile). Ergebnis: ~/.hikari/fitness/lecker.json.
Höflich: eine Seite pro Sekunde, schon geholte werden übersprungen."""
import gzip, json, os, re, time, urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = Path(os.path.expanduser("~/.hikari/fitness/lecker.json"))
have = {r["url"]: r for r in (json.loads(OUT.read_text()) if OUT.exists() else [])}
for line in (HERE.parent / "lecker_picked.txt").read_text().splitlines():
    group, url = line.split(" ", 1)
    if url in have:
        continue
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Hikari-Fitness/1.0 (private)"})
        raw = urllib.request.urlopen(req, timeout=30).read()
        # lecker.de schickt gzip auch ungefragt
        html = (gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw).decode("utf-8", "replace")
    except Exception as e:
        print("FEHL", url, e); time.sleep(1); continue
    recipe = None
    for block in re.findall(r'<script[^>]*application/ld\+json[^>]*>(.*?)</script>', html, re.S):
        try:
            data = json.loads(block)
        except Exception:
            continue
        for d in (data if isinstance(data, list) else data.get("@graph", [data])):
            if isinstance(d, dict) and d.get("@type") == "Recipe":
                recipe = d
    if recipe and recipe.get("recipeIngredient") and recipe.get("recipeInstructions"):
        have[url] = {"url": url, "group": group, "ld": recipe}
        print("ok  ", url.rsplit("/", 1)[-1])
    else:
        print("kein Rezept", url.rsplit("/", 1)[-1])
    OUT.write_text(json.dumps(list(have.values()), ensure_ascii=False))
    time.sleep(1)
print(len(have), "Rezepte")
