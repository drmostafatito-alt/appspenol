#!/usr/bin/env python3
"""Content Quality Gate and verification ledger.

An item is PUBLISHABLE only when all are true:
  - verification == "verified"
  - source is a non-empty string (reference to an authoritative source)
  - ar_egy is a non-empty string (Egyptian translation written and verified)
  - example_es, example_fusha, example_egy are non-empty
Unpublishable items are kept in the ledger (not deleted) so work can continue.

Usage:
  python tools/content_gate.py [content.json] [--ledger releases/CONTENT_LEDGER.csv] [--require-published N]
Exit code 1 if --require-published N is given and fewer than N items are publishable.
"""
import csv
import json
import sys

REQUIRED_FOR_PUBLISH = ("ar_egy", "example_es", "example_fusha", "example_egy", "source")


def is_publishable(item):
    if item.get("verification") != "verified":
        return False
    return all(isinstance(item.get(k), str) and item.get(k).strip() for k in REQUIRED_FOR_PUBLISH)


def gate(data, ledger_path=None):
    rows, total, publishable = [], 0, 0
    for ti, track in enumerate(data["tracks"]):
        for ui, unit in enumerate(track["units"]):
            for ii, item in enumerate(unit["items"]):
                total += 1
                ok = is_publishable(item)
                publishable += ok
                missing = [k for k in REQUIRED_FOR_PUBLISH if not (isinstance(item.get(k), str) and item.get(k).strip())]
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
