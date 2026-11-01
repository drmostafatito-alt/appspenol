#!/usr/bin/env python3
"""Remove duplicate vocabulary lines inside each level file (keeps the first occurrence).

The generator refuses duplicate Spanish entries per level, so a batch that repeats a word
already taught earlier must lose the later line. This tool prints exactly what it drops so
nothing disappears silently.

Usage: python3 tools/dedupe_curriculum.py [--dry-run]
"""
import glob
import os
import re
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEVELS = os.path.join(ROOT, "content", "levels")


def norm(text):
    text = text.strip().lower().replace("ñ", "\x01")
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", text.replace("\x01", "ñ")).strip()


def main(argv):
    dry = "--dry-run" in argv
    total_dropped = 0
    for path in sorted(glob.glob(os.path.join(LEVELS, "*.curriculum"))):
        seen = {}
        out = []
        dropped = []
        with open(path, encoding="utf-8") as handle:
            for number, line in enumerate(handle.read().split("\n"), 1):
                if line.startswith("W|"):
                    parts = line.split("|")
                    key = norm(parts[1] if len(parts) > 1 else "")
                    if key and key in seen:
                        dropped.append((number, parts[1], seen[key]))
                        continue
                    seen[key] = number
                out.append(line)
        if dropped:
            total_dropped += len(dropped)
            print(f"{os.path.basename(path)}: dropping {len(dropped)} duplicate line(s)")
            for number, word, first in dropped:
                print(f"  line {number}: {word} (first seen line {first})")
            if not dry:
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write("\n".join(out))
    print(f"total dropped: {total_dropped}{' (dry run)' if dry else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
