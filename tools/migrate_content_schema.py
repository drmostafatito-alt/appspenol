#!/usr/bin/env python3
"""Safe, reversible schema migration for content.json (v1 -> v2).

Forward: adds metadata fields to every item without changing es/ar values.
  - ar_egy: None  (NOT copied from ar: Egyptian translation must be written and verified)
  - pronunciation, example_es, example_fusha, example_egy, grammar_note: None
  - pos, topic, difficulty: None / derived placeholders
  - source: None, verification: "unverified"
Reverse: strips the v2 fields and the schema_version key, restoring v1 exactly.
Usage:
  python tools/migrate_content_schema.py [path] [--revert]
"""
import json
import sys

V2_FIELDS = {
    "ar_egy": None,
    "pronunciation": None,
    "example_es": None,
    "example_fusha": None,
    "example_egy": None,
    "grammar_note": None,
    "pos": None,
    "topic": None,
    "difficulty": None,
    "source": None,
    "verification": "unverified",
}


def forward(data):
    data["schema_version"] = 2
    for track in data["tracks"]:
        for unit in track["units"]:
            for item in unit["items"]:
                for key, value in V2_FIELDS.items():
                    item.setdefault(key, value)
    return data


def revert(data):
    data.pop("schema_version", None)
    for track in data["tracks"]:
        for unit in track["units"]:
            for item in unit["items"]:
                for key in V2_FIELDS:
                    item.pop(key, None)
    return data


def main(argv):
    path = next((a for a in argv if not a.startswith("--")), "app/src/main/assets/content.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    data = revert(data) if "--revert" in argv else forward(data)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("reverted" if "--revert" in argv else "migrated", path)


if __name__ == "__main__":
    main(sys.argv[1:])
