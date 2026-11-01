#!/usr/bin/env python3
"""Content Quality Gate and verification ledger.

An item is PUBLISHABLE only when all are true:
  - verification == "verified"
  - source is a non-empty string (reference to an authoritative source)
  - ar_egy and pronunciation are non-empty
  - example_es is non-empty; phrase/verb/question items also need example_fusha and example_egy
Unpublishable items are kept in the ledger (not deleted) so work can continue.

Required fields depend on item type (see required_for).
Usage:
  python tools/content_gate.py [content.json] [--ledger releases/CONTENT_LEDGER.csv] [--require-published N]
Exit code 1 if --require-published N is given and fewer than N items are publishable.
"""
import csv
import json
import sys

BASE_REQUIRED = ("ar_egy", "pronunciation", "example_es", "source")
# Full sentence-level items need examples in all three languages.
FULL_EXAMPLE_TYPES = {"phrase", "verb", "question"}


def required_for(item):
    req = list(BASE_REQUIRED)
    if item.get("type", item.get("pos")) in FULL_EXAMPLE_TYPES:
        req += ["example_fusha", "example_egy"]
    return req


def missing_fields(item):
    return [k for k in required_for(item) if not (isinstance(item.get(k), str) and item.get(k).strip())]


def is_publishable(item):
    if item.get("verification") != "verified":
        return False
    return not missing_fields(item)


def gate(data, ledger_path=None):
    rows, total, publishable = [], 0, 0
    for ti, track in enumerate(data["tracks"]):
        for ui, unit in enumerate(track["units"]):
            for ii, item in enumerate(unit["items"]):
                total += 1
                ok = is_publishable(item)
                publishable += ok
                missing = missing_fields(item)
                rows.append({
                    "id": f"{ti}_{ui}_{ii}",
                    "es": item.get("es", ""),
                    "ar_fusha": item.get("ar", ""),
                    "verification": item.get("verification", ""),
                    "source": item.get("source") or "",
                    "missing": ";".join(missing),
                    "publishable": "yes" if ok else "no",
                })
    if ledger_path:
        with open(ledger_path, "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys()) if rows else ["id"])
            w.writeheader()
            w.writerows(rows)
    return total, publishable


def main(argv):
    path = next((a for a in argv if not a.startswith("--") and a.endswith(".json")), "app/src/main/assets/content.json")
    ledger = argv[argv.index("--ledger") + 1] if "--ledger" in argv else None
    need = int(argv[argv.index("--require-published") + 1]) if "--require-published" in argv else None
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    total, pub = gate(data, ledger)
    print(f"Publishable: {pub} of {total} items. Unpublished items stay in the ledger.")
    if need is not None and pub < need:
        print(f"GATE FAIL: need {need} publishable items, have {pub}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
