#!/usr/bin/env python3
"""Content Quality Gate and verification ledger (schema v3: levels → units → items).

An item is PUBLISHABLE only when all are true:
  - verification == "verified"  (automated checks alone can never set this)
  - "source" is a non-empty string (authoritative reference for the sensitive fact)
  - ar_egy (Egyptian Arabic) and ipa (pronunciation) are non-empty
  - example_es is non-empty; word/phrase/verb items also need example_fusha and example_egy
Unpublishable items are kept in the ledger (never deleted) so work can continue and
the honest status of every item stays visible.

Status taxonomy: pending → machine_checked → source_checked → sound_verified → verified.

Usage:
  python tools/content_gate.py [content.json] [--ledger releases/CONTENT_LEDGER.csv]
                               [--require-published N] [--require-verified N]
Exit code 1 if a --require-* threshold is not met.
"""
import csv
import json
import sys

BASE_REQUIRED = ("ar_egy", "ipa", "example_es", "source")
# Items that are full sentences carry their own translation instead of an example sentence.
FULL_EXAMPLE_TYPES = {"phrase", "verb", "word"}


def kind_of(item):
    if item.get("type") == "sentence":
        return "sentence"
    return item.get("pos") or item.get("type") or "word"


def required_for(item):
    req = [f for f in BASE_REQUIRED]
    if kind_of(item) in FULL_EXAMPLE_TYPES:
        req += ["example_fusha", "example_egy"]
    return req


def missing_fields(item):
    return [k for k in required_for(item) if not (isinstance(item.get(k), str) and item.get(k).strip())]


def is_publishable(item):
    if item.get("verification") != "verified":
        return False
    return not missing_fields(item)


def gate(data, ledger_path=None):
    rows, total, publishable, verified = [], 0, 0, 0
    for level in data.get("levels", []):
        for unit in level.get("units", []):
            for item in unit.get("items", []):
                total += 1
                ok = is_publishable(item)
                publishable += ok
                if item.get("verification") == "verified":
                    verified += 1
                rows.append({
                    "id": item.get("id", ""),
                    "level": level.get("id", ""),
                    "unit": unit.get("id", ""),
                    "es": item.get("es", ""),
                    "ar_fusha": item.get("ar", ""),
                    "ar_egy": item.get("ar_egy", ""),
                    "ipa": item.get("ipa", ""),
                    "verification": item.get("verification", ""),
                    "source": item.get("source") or "",
                    "missing": ";".join(missing_fields(item)),
                    "publishable": "yes" if ok else "no",
                })
    if ledger_path:
        with open(ledger_path, "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys()) if rows else ["id"])
            w.writeheader()
            w.writerows(rows)
    return total, publishable, verified


def main(argv):
    path = next((a for a in argv if not a.startswith("--") and a.endswith(".json")),
                "app/src/main/assets/content.json")
    ledger = argv[argv.index("--ledger") + 1] if "--ledger" in argv else None
    need = int(argv[argv.index("--require-published") + 1]) if "--require-published" in argv else None
    need_verified = int(argv[argv.index("--require-verified") + 1]) if "--require-verified" in argv else None
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    total, pub, verified = gate(data, ledger)
    print(f"items: {total} • verified: {verified} • publishable: {pub}")
    print("Unpublished items stay in the ledger; automated checks never promote an item to verified.")
    if need is not None and pub < need:
        print(f"GATE FAIL: need {need} publishable items, have {pub}")
        return 1
    if need_verified is not None and verified < need_verified:
        print(f"GATE FAIL: need {need_verified} verified items, have {verified}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
