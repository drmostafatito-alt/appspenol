"""Content validation for app/src/main/assets/content.json (schema v3: levels → units → items).

Checks structure, empty fields, duplicates per level, missing examples, Arabic script
presence, balanced Spanish question/exclamation marks, and cross-level overlap.
Exits non-zero on any ERROR. WARN entries are reported but do not fail.
Standard library only.
"""
from pathlib import Path
import json
import re
import sys
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
CONTENT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/assets/content.json"
EXPECTED_LEVELS = ["A0", "A1", "A2", "B1", "B2", "C1"]
EXPECTED_TRACK_ITEMS = 1000
ARABIC = re.compile(r"[\u0600-\u06FF]")


def norm(text):
    text = text.lower().replace("ñ", "\x01")
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    text = text.replace("\x01", "ñ")
    text = re.sub(r"\([^)]*\)", "", text)
    text = re.sub(r"[^\w\s/]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def check_item(item, where, errors, warnings):
    es = (item.get("es") or "").strip()
    ar = (item.get("ar") or "").strip()
    egy = (item.get("ar_egy") or "").strip()
    if not es:
        errors.append(f"{where}: empty Spanish text")
        return None
    if not ar:
        errors.append(f"{where}: empty Arabic (MSA) translation for '{es}'")
    elif not ARABIC.search(ar):
        errors.append(f"{where}: MSA translation has no Arabic letters: '{es}'")
    if not egy:
        warnings.append(f"{where}: no Egyptian Arabic translation: '{es}'")
    elif not ARABIC.search(egy):
        errors.append(f"{where}: Egyptian translation has no Arabic letters: '{es}'")
    if len(es) > 120:
        warnings.append(f"{where}: long entry ({len(es)} chars)")
    if es.count("¿") != es.count("?"):
        errors.append(f"{where}: unbalanced ¿ ?: '{es}'")
    if es.count("¡") != es.count("!"):
        errors.append(f"{where}: unbalanced ¡ !: '{es}'")
    if item.get("type") != "sentence":
        if not (item.get("example_es") or "").strip():
            warnings.append(f"{where}: word without usage example: '{es}'")
    key = norm(es)
    if not key:
        errors.append(f"{where}: Spanish text has no letters: '{es}'")
        return None
    return key


def main():
    errors, warnings = [], []
    data = json.loads(CONTENT.read_text(encoding="utf-8"))
    levels = data.get("levels")
    if not isinstance(levels, list) or not levels:
        print("ERROR: 'levels' missing or empty")
        return 1

    seen_levels = [level.get("id") for level in levels]
    for level_id in EXPECTED_LEVELS:
        if level_id not in seen_levels:
            errors.append(f"missing level {level_id}")

    all_es = {}
    for level in levels:
        level_id = level.get("id", "")
        units = level.get("units", [])
        if not units:
            errors.append(f"level {level_id}: no units")
        total = 0
        keys_in_level = {}
        for unit in units:
            unit_id = unit.get("id", "")
            if not (unit.get("title") or "").strip():
                errors.append(f"{level_id}/{unit_id}: empty unit title")
            items = unit.get("items", [])
            if not items and not unit.get("lessons"):
                errors.append(f"{level_id}/{unit_id}: no items and no lessons")
            if not unit.get("lessons"):
                errors.append(f"{level_id}/{unit_id}: no lessons")
            for index, item in enumerate(items):
                total += 1
                where = f"{level_id}/{unit_id}#{index}"
                key = check_item(item, where, errors, warnings)
                if not key:
                    continue
                if key in keys_in_level:
                    errors.append(f"{where}: duplicate Spanish inside {level_id}: '{item['es']}' "
                                  f"(also {keys_in_level[key]})")
                keys_in_level[key] = where
                all_es.setdefault(key, []).append((level_id, item.get("es"), item.get("ar")))
            for lesson in unit.get("lessons", []):
                if not lesson.get("id"):
                    errors.append(f"{level_id}/{unit_id}: lesson without id")
                for exercise in lesson.get("exercises", []):
                    options = exercise.get("options") or []
                    answer = exercise.get("answer", -1)
                    if exercise.get("type") in {"choose", "listen", "fill", "final"}:
                        if len(options) < 3:
                            errors.append(f"{lesson['id']}/{exercise.get('id')}: fewer than 3 options")
                        if not (0 <= answer < len(options)):
                            errors.append(f"{lesson['id']}/{exercise.get('id')}: answer index out of range")
                        if len(set(options)) != len(options):
                            errors.append(f"{lesson['id']}/{exercise.get('id')}: duplicate options")
        if total < EXPECTED_TRACK_ITEMS:
            warnings.append(f"level {level_id}: {total} items (target {EXPECTED_TRACK_ITEMS})")

    overlap = [k for k, v in all_es.items() if len({x[0] for x in v}) > 1]
    if overlap:
        warnings.append(f"{len(overlap)} Spanish entries repeat across levels (allowed, review wording)")
        for key in sorted(overlap)[:15]:
            warnings.append("  overlap: '" + all_es[key][0][1] + "'")

    for dialogue in data.get("dialogues", []):
        if len(dialogue.get("lines", [])) < 4:
            errors.append(f"dialogue {dialogue.get('id')}: fewer than 4 lines")
        if len(dialogue.get("questions", [])) < 2:
            errors.append(f"dialogue {dialogue.get('id')}: fewer than 2 questions")
        for line in dialogue.get("lines", []):
            if not (line.get("egy") or line.get("ar")):
                errors.append(f"dialogue {dialogue.get('id')}: line without Arabic: {line.get('es')}")
    if not data.get("dialogues"):
        errors.append("no dialogues")
    if not data.get("grammar"):
        errors.append("no grammar rules")

    for w in warnings:
        print("WARN:", w)
    for e in errors:
        print("ERROR:", e)
    items = sum(len(u.get("items", [])) for lv in levels for u in lv.get("units", []))
    print(f"Summary: {len(errors)} error(s), {len(warnings)} warning(s), {items} items, "
          f"{len(data.get('dialogues', []))} dialogues, {len(data.get('grammar', []))} grammar rules.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
