#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = ["openpyxl"]
# ///
"""Bundeslebensmittelschlüssel 4.0 (Max Rubner-Institut, CC BY 4.0, DOI 10.25826/Data20251217-134202-0)
→ backend/fitness/bls.json.gz (nur die Nährstoffe, die der Fitness-Bereich kennt, je 100 g).

Download: https://www.blsdb.de/assets/uploads/BLS_4_0_2025_DE.zip
Aufruf:   uv run --script backend/fitness/tools/import_bls.py <Pfad zu BLS_4_0_Daten_2025_DE.xlsx>

»-« bedeutet »unbekannt« (nicht 0) und wird weggelassen; »<LOD«/»<LOQ« (unter der Nachweisgrenze) zählt als 0.
"""
import gzip
import json
import sys
from pathlib import Path

import openpyxl

# BLS-Komponente → (unser Schlüssel, Faktor)
MAP = {
    "ENERCC": ("kcal", 1), "PROT625": ("protein", 1), "CHO": ("carbs", 1), "FAT": ("fat", 1),
    "FIBT": ("fiber", 1), "SUGAR": ("sugar", 1), "FASAT": ("satfat", 1), "NACL": ("salt", 1),
    "FAPUN3": ("omega3", 1), "CA": ("calcium", 1), "MG": ("magnesium", 1), "FE": ("iron", 1),
    "ZN": ("zinc", 1), "K": ("potassium", 1), "ID": ("iodine", 1), "VITAA": ("vitA", 1),
    "VITC": ("vitC", 1), "VITD": ("vitD", 1), "VITE": ("vitE", 1), "VITK": ("vitK", 1),
    "THIA": ("vitB1", 1), "RIBF": ("vitB2", 1), "VITB6": ("vitB6", 0.001),  # BLS: µg → mg
    "VITB12": ("vitB12", 1), "FOL": ("folate", 1),
}


def num(v):
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).strip()
    if s in ("", "-"):
        return None
    if s.startswith("<"):
        return 0.0
    try:
        return float(s.replace(",", "."))
    except ValueError:
        return None


def main(path: str):
    wb = openpyxl.load_workbook(path, read_only=True)
    rows = wb.active.iter_rows(values_only=True)
    header = next(rows)
    idx = {}
    for i, h in enumerate(header):
        if not h:
            continue
        code, _, rest = str(h).partition(" ")
        if code in MAP and not rest.startswith(("Datenherkunft", "Referenz")) and code not in idx:
            idx[code] = i
    missing = set(MAP) - set(idx)
    if missing:
        sys.exit(f"Spalten fehlen: {missing}")
    foods = []
    for r in rows:
        if not r or not r[0]:
            continue
        n = {}
        for code, (key, f) in MAP.items():
            v = num(r[idx[code]])
            if v is not None:
                n[key] = round(v * f, 4)
        foods.append({"id": str(r[0]), "de": str(r[1] or ""), "en": str(r[2] or ""), "n": n})
    out = Path(__file__).resolve().parent.parent / "bls.json.gz"
    with gzip.open(out, "wt", encoding="utf-8") as fh:
        json.dump({"source": "BLS 4.0 (Max Rubner-Institut), CC BY 4.0", "foods": foods}, fh, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(foods)} Lebensmittel → {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main(sys.argv[1])
