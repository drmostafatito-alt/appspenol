"""Content validation for app/src/main/assets/content.json.

Checks structure, empty fields, duplicates, missing examples, and cross-track
overlap. Exits non-zero on any ERROR. WARN items are reported but do not fail.
Standard library only.
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "app/src/main/assets/content.json"
EXPECTED_TRACK_ITEMS = 1000


def norm(text):
    text = text.lower()
    text = re.sub(r"\([^)]*\)", "", text)
    text = re.sub(r"[^\w\s/]", " ", text)
    text = text.replace("/as", "").replace("/a", "").replace("/o", "")
    return re.sub(r"\s+", " ", text).strip()


def main():
    errors, warnings = [], []
    data = json.loads(CONTENT.read_text(encoding="utf-8"))
    tracks = data.get("tracks")
    if not isinstance(tracks, list) or not tracks:
        print("ERROR: 'tracks' missing or empty")
        return 1

    all_es = {}  # normalized Spanish -> list of (track, unit, index)
    track_keys = []
    for ti, track in enumerate(tracks):
        title = track.get("title", "").strip()
        if not title:
            errors.append(f"track {ti}: empty title")
        units = track.get("units", [])
        total = 0
        keys_in_track = set()
        for ui, unit in enumerate(units):
            utitle = unit.get("title", "").strip()
            if not utitle:
                errors.append(f"track {ti} unit {ui}: empty title")
            items = unit.get("items", [])
            if not items:
                errors.append(f"track {ti} unit {ui}: no items")
            for ii, item in enumerate(items):
                total += 1
                where = f"track {ti} unit {ui} item {ii}"
                es = (item.get("es") or "").strip()
                ar = (item.get("ar") or "").strip()
                if not es:
                    errors.append(f"{where}: empty Spanish text")
                    continue
                if not ar:
                    errors.append(f"{where}: empty Arabic translation for '{es}'")
                if len(es) > 100:
                    warnings.append(f"{where}: long entry ({len(es)} chars)")
                key = norm(es)
                if not key:
                    errors.append(f"{where}: Spanish text has no letters: '{es}'")
                    continue
                if key in keys_in_track:
                    warnings.append(f"{where}: duplicate inside track {ti}: '{es}'")
                keys_in_track.add(key)
                all_es.setdefault(key, []).append((ti, ui, ii, es, ar))
                if len(es.split()) <= 2 and not re.search(r"[a-záéíóúñü]", es.lower()):
                    errors.append(f"{where}: no Spanish letters: '{es}'")
        if total != EXPECTED_TRACK_ITEMS:
            warnings.append(f"track {ti} '{title}': {total} items (target {EXPECTED_TRACK_ITEMS})")
        track_keys.append(keys_in_track)

    # Cross-track overlap: same Spanish phrase in both tracks is allowed only
    # if it is intentional. Report it so a reviewer can decide.
    overlap = [k for k, v in all_es.items() if len({t for t, *_ in v}) > 1]
    if overlap:
        warnings.append(f"{len(overlap)} Spanish phrases appear in more than one track")
        for k in sorted(overlap)[:20]:
            entries = all_es[k]
            warnings.append(f"  overlap: '{entries[0][3]}' -> " + " | ".join(e[4] for e in entries))

    for w in warnings:
        print("WARN:", w)
    for e in errors:
        print("ERROR:", e)
    print(f"Summary: {len(errors)} error(s), {len(warnings)} warning(s), "
          f"{sum(len(u['items']) for t in tracks for u in t.get('units', []))} items.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
