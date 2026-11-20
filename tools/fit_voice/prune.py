#!/usr/bin/env python3
"""Index der Stimme auf die aktuellen Texte zurückschneiden und verwaiste Clips löschen.

  node tools/fit_voice/texts.mjs > /tmp/fit.json && node tools/fit_voice/mind_texts.mjs > /tmp/mind.json
  python3 tools/fit_voice/prune.py /tmp/fit.json /tmp/mind.json [--dry]
"""
import hashlib, json, os, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
OUT = os.path.join(ROOT, "android/app/src/main/assets/fit-web/voice")
dry = "--dry" in sys.argv
items = [x for f in sys.argv[1:] if not f.startswith("--") for x in json.load(open(f, encoding="utf-8"))]
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voices import clip_id as _cid  # noqa: E402
cid = lambda s, v="rem": _cid(s, v)
index = {x["de"]: cid(x["spoken"], x.get("voice", "rem")) for x in items if os.path.exists(os.path.join(OUT, cid(x["spoken"], x.get("voice", "rem")) + ".ogg"))}
keep = set(index.values())
missing = [x["de"] for x in items if x["de"] not in index]
orphans = [f for f in os.listdir(OUT) if f.endswith(".ogg") and f[:-4] not in keep]
print(f"{len(index)} im Index, {len(missing)} ohne Clip, {len(orphans)} verwaiste Clips")
for m in missing[:20]:
    print("  fehlt:", m)
if not dry:
    for f in orphans:
        os.remove(os.path.join(OUT, f))
    json.dump(index, open(os.path.join(OUT, "index.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
