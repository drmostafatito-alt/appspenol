#!/usr/bin/env python3
"""Checks that upgrading the app cannot orphan saved progress.

Progress keys are item ids and lesson ids. If an id disappears from content.json, the learner's
XP stays but the lesson/item can never be opened again. This compares the previous release's
content (read straight from git) with the current file and reports every removed id.

Usage: python3 tools/check_upgrade_compat.py [--base 1864c9b] [--write releases/UPGRADE_COMPAT_REPORT.md]
Exit code 1 when a removed id is found.
"""
import argparse
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(ROOT, "app", "src", "main", "assets", "content.json")


def ids_from(model):
    items, lessons, units = set(), set(), set()
    for level in model["levels"]:
        for unit in level["units"]:
            units.add(unit["id"])
            for item in unit["items"]:
                items.add(item["id"])
            for lesson in unit["lessons"]:
                lessons.add(lesson["id"])
    return items, lessons, units


def load_revision(rev):
    result = subprocess.run(
        ["git", "-C", ROOT, "show", f"{rev}:app/src/main/assets/content.json"],
        capture_output=True, text=True)
    if result.returncode != 0:
        return None
    return json.loads(result.stdout)


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="1864c9b", help="previous released revision (8.0)")
    parser.add_argument("--write", default="")
    args = parser.parse_args(argv)

    previous = load_revision(args.base)
    current = json.load(open(CONTENT, encoding="utf-8"))
    if previous is None:
        print(f"cannot read content.json at {args.base}", file=sys.stderr)
        return 2

    old_items, old_lessons, old_units = ids_from(previous)
    new_items, new_lessons, new_units = ids_from(current)
    removed_items = sorted(old_items - new_items)
    removed_lessons = sorted(old_lessons - new_lessons)
    removed_units = sorted(old_units - new_units)
    added_items = len(new_items - old_items)
    added_lessons = len(new_lessons - old_lessons)

    # Nothing that a learner could have progress on may vanish.
    problems = []
    problems += [f"item id removed: {name}" for name in removed_items]
    problems += [f"lesson id removed: {name}" for name in removed_lessons]
    problems += [f"unit id removed: {name}" for name in removed_units]

    lines = ["# تقرير توافق الترقية", ""]
    lines.append(f"المقارنة: `{args.base}` (الإصدار 8.0) → الملف الحالي.")
    lines.append("")
    lines.append(f"- عناصر قديمة: {len(old_items)} • جديدة: {len(new_items)} • أُضيف {added_items} • حُذف {len(removed_items)}")
    lines.append(f"- دروس قديمة: {len(old_lessons)} • جديدة: {len(new_lessons)} • أُضيف {added_lessons} • حُذف {len(removed_lessons)}")
    lines.append(f"- وحدات: {len(old_units)} → {len(new_units)} (محذوفة: {len(removed_units)})")
    lines.append("")
    lines.append("## النتيجة")
    lines.append("")
    if problems:
        lines += [f"- ❌ {problem}" for problem in problems]
        lines.append("")
        lines.append("أي معرّف محذوف يعني أن تقدم المستخدم عليه لم يعد له مكان في التطبيق. "
                     "الحل الصحيح هو إعادة المعرّف أو تحويله صراحةً، لا تركه يسقط.")
    else:
        lines.append("- ✅ لا معرّف محذوف: كل تقدم محفوظ من الإصدار 8.0 ما زال يشير إلى عنصر/درس موجود. "
                     "الترقية إضافية بالكامل ولا تحتاج أي هجرة.")
    text = "\n".join(lines) + "\n"
    if args.write:
        os.makedirs(os.path.dirname(args.write) or ".", exist_ok=True)
        open(args.write, "w", encoding="utf-8").write(text)
    print(text)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
