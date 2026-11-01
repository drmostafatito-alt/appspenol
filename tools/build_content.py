#!/usr/bin/env python3
"""Build app/src/main/assets/content.json (schema v3) from the authored curriculum.

Source format (`content/*.curriculum`, one record per line, `|` separated):

    L | level id | title | title_es | goal_egy | desc_egy
    U | unit id | title | title_es | goal_egy
    W | spanish | fusha | egyptian | pos | topic | note_egy | ipa(optional)
    S | spanish | fusha | egyptian            # model sentence, also an item of type "sentence"
    D | id | section | level | title_es | title_ar | title_egy | context_egy | context_fusha
    DL | speaker | spanish | fusha | egyptian
    DQ | prompt_egy | option1 | option2 | option3 [| option4] | answer index | explain_egy
    G | id | level | title_ar | rule_egy | rule_fusha
    GE | spanish | fusha | egyptian

IPA is generated with `tools/ipa.py` when the record does not provide it.
Example sentences are attached to the words they contain, so each word shown in a
lesson can be accompanied by a real sentence instead of a synthetic one.
"""
import argparse
import glob
import json
import os
import re
import sys
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ipa import ipa as to_ipa  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT_DIR = os.path.join(ROOT, "content")
OUTPUT = os.path.join(ROOT, "app", "src", "main", "assets", "content.json")
REPORT = os.path.join(ROOT, "releases", "CONTENT_REPORT.md")

LESSON_SIZE = 6
ARABIC_RE = re.compile(r"[\u0600-\u06FF]")
LATIN_RE = re.compile(r"[A-Za-z]")
WORD_RE = re.compile(r"[^\W\d_]+", re.UNICODE)

problems = []


def fail(message):
    problems.append(message)


def norm_es(text):
    import unicodedata
    text = text.lower().replace("ñ", "\u0001")
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return text.replace("\u0001", "ñ")


def tokens(text):
    return [t for t in WORD_RE.findall(norm_es(text)) if t]


def read_records(path):
    records = []
    with open(path, encoding="utf-8") as handle:
        for number, raw in enumerate(handle, 1):
            line = raw.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = [p.strip() for p in line.split("|")]
            records.append((number, parts))
    return records


class Unit:
    def __init__(self, uid, title, title_es, goal):
        self.id = uid
        self.title = title
        self.title_es = title_es
        self.goal = goal
        self.items = []
        self.dialogue_lessons = []


class Level:
    def __init__(self, lid, title, title_es, goal, desc):
        self.id = lid
        self.title = title
        self.title_es = title_es
        self.goal = goal
        self.desc = desc
        self.units = []


def parse_level_file(path, level):
    current_unit = None
    for number, parts in read_records(path):
        tag = parts[0]
        where = f"{os.path.basename(path)}:{number}"
        if tag == "U":
            if len(parts) < 5:
                fail(f"{where}: unit needs id,title,title_es,goal")
                continue
            current_unit = Unit(parts[1], parts[2], parts[3], parts[4])
            level.units.append(current_unit)
        elif tag == "W":
            if current_unit is None:
                fail(f"{where}: word outside a unit")
                continue
            es = parts[1] if len(parts) > 1 else ""
            ar = parts[2] if len(parts) > 2 else ""
            egy = parts[3] if len(parts) > 3 else ""
            pos = parts[4] if len(parts) > 4 else ""
            topic = parts[5] if len(parts) > 5 else ""
            note = parts[6] if len(parts) > 6 else ""
            given_ipa = parts[7] if len(parts) > 7 else ""
            current_unit.items.append({
                "es": es, "ar": ar, "ar_egy": egy, "pos": pos or "word",
                "topic": topic or current_unit.title, "note_egy": note,
                "ipa": given_ipa, "type": "word",
            })
        elif tag == "S":
            if current_unit is None:
                fail(f"{where}: sentence outside a unit")
                continue
            es = parts[1] if len(parts) > 1 else ""
            ar = parts[2] if len(parts) > 2 else ""
            egy = parts[3] if len(parts) > 3 else ""
            current_unit.items.append({
                "es": es, "ar": ar, "ar_egy": egy, "pos": "sentence",
                "topic": current_unit.title, "note_egy": "",
                "ipa": "", "type": "sentence",
            })
        elif tag == "L":
            continue
        else:
            fail(f"{where}: unknown record tag '{tag}'")
    return level


def build_levels():
    levels = []
    paths = sorted(glob.glob(os.path.join(CONTENT_DIR, "levels", "*.curriculum")))
    if not paths:
        sys.exit("no curriculum files found in content/levels")
    for path in paths:
        records = read_records(path)
        if not records or records[0][1][0] != "L":
            sys.exit(f"{path}: must start with an L record")
        parts = records[0][1]
        level = Level(parts[1], parts[2], parts[3], parts[4], parts[5] if len(parts) > 5 else "")
        parse_level_file(path, level)
        levels.append(level)
    order = {"A0": 0, "A1": 1, "A2": 2, "B1": 3, "B2": 4, "C1": 5}
    levels.sort(key=lambda lv: order.get(lv.id, 99))
    return levels


def build_dialogues():
    dialogues = []
    path = os.path.join(CONTENT_DIR, "dialogues.curriculum")
    if not os.path.exists(path):
        return dialogues
    current = None
    for number, parts in read_records(path):
        tag = parts[0]
        where = f"dialogues.curriculum:{number}"
        if tag == "D":
            if len(parts) < 7:
                fail(f"{where}: dialogue needs id,section,level,title_es,title_ar,title_egy")
                continue
            current = {
                "id": parts[1], "section": parts[2], "level": parts[3],
                "title_es": parts[4], "title_ar": parts[5], "title_egy": parts[6],
                "context_egy": parts[7] if len(parts) > 7 else "",
                "context_fusha": parts[8] if len(parts) > 8 else "",
                "lines": [], "questions": [],
            }
            dialogues.append(current)
        elif tag == "DL":
            if current is None:
                fail(f"{where}: line outside a dialogue")
                continue
            current["lines"].append({
                "who": parts[1], "es": parts[2],
                "ar": parts[3] if len(parts) > 3 else "",
                "egy": parts[4] if len(parts) > 4 else "",
            })
        elif tag == "DQ":
            if current is None:
                fail(f"{where}: question outside a dialogue")
                continue
            body = parts[1:]
            if len(body) < 4:
                fail(f"{where}: question needs prompt, options, answer")
                continue
            prompt = body[0]
            explain = body[-1]
            answer = body[-2]
            options = body[1:-2]
            if not answer.isdigit():
                fail(f"{where}: answer index must be a number")
                continue
            current["questions"].append({
                "id": f"{current['id']}_q{len(current['questions']) + 1}",
                "type": "comprehend",
                "prompt_egy": prompt,
                "options": options,
                "answer": int(answer),
                "explain_egy": explain,
            })
        else:
            fail(f"{where}: unknown record tag '{tag}'")
    return dialogues


def build_grammar():
    rules = []
    path = os.path.join(CONTENT_DIR, "grammar.curriculum")
    if not os.path.exists(path):
        return rules
    current = None
    for number, parts in read_records(path):
        tag = parts[0]
        if tag == "G":
            current = {
                "id": parts[1], "level": parts[2], "title_ar": parts[3],
                "rule_egy": parts[4], "rule_fusha": parts[5] if len(parts) > 5 else "",
                "examples": [],
            }
            rules.append(current)
        elif tag == "GE":
            if current is None:
                fail(f"grammar.curriculum:{number}: example outside a rule")
                continue
            current["examples"].append({"es": parts[1], "ar": parts[2], "egy": parts[3] if len(parts) > 3 else ""})
        else:
            fail(f"grammar.curriculum:{number}: unknown tag '{tag}'")
    return rules


ARTICLE_PREFIXES = ("el ", "la ", "los ", "las ", "un ", "una ")


def headword(item):
    """The lexical core of an entry: articles and parenthetical glosses removed."""
    es = re.sub(r"\([^)]*\)", "", item["es"]).strip().lower()
    for prefix in ARTICLE_PREFIXES:
        if es.startswith(prefix):
            es = es[len(prefix):]
            break
    return es


def inflection_hit(word, token, pos=""):
    """True when a sentence token is a defensible inflection of a single-token headword.

    Verbs match their conjugation stem, adjectives may swap gender, and nouns match
    number only — a sentence about «el hijo» must not be used as the example for «la hija».
    """
    if token == word:
        return True
    if pos == "verb" and len(word) > 5 and word[-2:] in ("ar", "er", "ir"):
        stem = word[:-2]
        endings = ("o", "as", "a", "amos", "áis", "an", "é", "aste", "ó", "aron", "aba", "ía",
                   "ando", "ado", "ido", "ar", "er", "ir", "e", "es", "en", "ió", "ieron")
        if len(stem) >= 4 and token.startswith(stem) and token[len(stem):] in endings:
            return True
    if item_plural(token) == word or item_plural(word) == token:
        return True
    if pos == "adj" and word[-1:] in ("o", "a") and len(word) > 3:
        if token in (word[:-1] + "o", word[:-1] + "a"):
            return True
    return False


def item_plural(word):
    if not word:
        return word
    if word[-1] in "aeiou":
        return word + "s"
    if word[-1] == "z":
        return word[:-1] + "ces"
    return word + "es"


# Dialogue lines and grammar examples are real sentences too; they are merged into the pool
# used to illustrate vocabulary items (they ship with MSA + Egyptian translations).
EXTRA_EXAMPLES = []


def collect_extra_examples(dialogues, grammar):
    for dialogue in dialogues:
        for line in dialogue["lines"]:
            EXTRA_EXAMPLES.append({"es": line["es"], "ar": line["ar"], "ar_egy": line["egy"]})
    for rule in grammar:
        for example in rule["examples"]:
            EXTRA_EXAMPLES.append({"es": example["es"], "ar": example["ar"], "ar_egy": example["egy"]})


def attach_examples(items):
    """Attach the first model sentence that uses the word, allowing simple inflections."""
    sentences = [i for i in items if i["type"] == "sentence"] + EXTRA_EXAMPLES
    for item in items:
        if item["type"] == "sentence":
            continue
        word_tokens = tokens(item["es"])
        if not word_tokens:
            continue
        core = headword(item)
        simple = len(core.split()) == 1
        for sentence in sentences:
            sentence_tokens = tokens(sentence["es"])
            if all(token in sentence_tokens for token in word_tokens):
                hit = True
            elif simple and core.isalpha():
                hit = any(inflection_hit(core, token, item["pos"]) for token in sentence_tokens)
            else:
                hit = False
            if hit:
                item["example_es"] = sentence["es"]
                item["example_fusha"] = sentence["ar"]
                item["example_egy"] = sentence["ar_egy"]
                break


FREQ_PATH = os.path.join(CONTENT_DIR, "sources", "es_frequency_opensubtitles_50k.txt")
FREQ_SOURCE = "hermitdave/FrequencyWords · OpenSubtitles 2018 (es_50k)"


def load_frequency():
    """Word → rank in an OpenSubtitles-derived Spanish frequency corpus (attestation of spelling)."""
    if not os.path.exists(FREQ_PATH):
        return {}
    ranks = {}
    with open(FREQ_PATH, encoding="utf-8") as handle:
        for index, line in enumerate(handle, 1):
            parts = line.split()
            if not parts:
                continue
            ranks.setdefault(parts[0].lower(), index)
    return ranks


FREQUENCY = load_frequency()


def attest(item):
    """Record whether the Spanish spelling is attested in the corpus, with its rank."""
    if item["type"] != "word" and item["type"] != "sentence":
        return
    core = headword(item) if item["type"] == "word" else item["es"]
    candidates = [core, item_plural(core), item_plural(core)[:-1] if item_plural(core).endswith("es") else core]
    for candidate in candidates:
        rank = FREQUENCY.get(candidate)
        if rank:
            item["freq_rank"] = rank
            item["source"] = f"{FREQ_SOURCE} · rank {rank}"
            item["attestation"] = "opensubtitles-2018"
            item["source_fields"] = ["orthography", "frequency"]
            item["source_status"] = "spelling_attested"
            return
    item.setdefault("freq_rank", 0)
    item.setdefault("source_status", "unattested")


def noun_gender(entry):
    low = entry.strip().lower()
    if low.startswith(("el ", "los ")):
        return "m"
    if low.startswith(("la ", "las ")):
        return "f"
    return ""


# Nouns whose bare meaning makes even a "talking about X" frame unnatural.
SKIP_PATTERN_NAMES = {"la gente", "el señor", "la señora", "don", "doña"}


def de_article(entry):
    """«de + el» → «del», «de + la» stays «de la»."""
    low = entry.strip()
    lower = low.lower()
    if lower.startswith("el "):
        return "del " + low[3:]
    return "de " + low


def fill_phrase_examples(unit):
    """An expression is its own usage example: keep the phrase as the model sentence.

    This lets the planner build word-order and fill-in drills from collocations
    («hacer la compra») without inventing a sentence around them.
    """
    for item in unit.items:
        if item["type"] != "word" or item.get("example_es"):
            continue
        if item["pos"] != "phrase":
            continue
        if len(item["es"].split()) < 2:
            continue
        item["example_es"] = item["es"]
        item["example_fusha"] = item["ar"]
        item["example_egy"] = item["ar_egy"]
        item["example_source"] = "phrase_self"


def fill_pattern_examples(unit):
    """Give a noun one short, grammatical usage sentence when no real sentence matched.

    The frames («Es importante hablar de …», «Quiero aprender más sobre …») stay natural for
    concrete and abstract nouns alike and only need article agreement, so no invented collocation
    is claimed. Provenance is recorded as example_source = "pattern" and never mixed with
    sentences written by hand or taken from the curriculum.
    """
    for item in unit.items:
        if item["type"] != "word" or item.get("example_es"):
            continue
        if item["pos"] != "noun":
            continue
        entry = item["es"].strip()
        if entry.lower() in SKIP_PATTERN_NAMES:
            continue
        if not entry.lower().startswith(("el ", "la ", "los ", "las ")):
            continue
        if "(" in entry or len(entry.split()) > 3:
            continue
        if not noun_gender(entry):
            continue
        variant = sum(ord(c) for c in entry) % 3
        if variant == 0:
            example = "Es importante hablar " + de_article(entry) + "."
            fusha = "من المهم الحديث عن " + item["ar"] + "."
            egy = "مهم نتكلم عن " + item["ar_egy"] + "."
        elif variant == 1:
            example = "En clase hablamos " + de_article(entry) + "."
            fusha = "في الحصة نتكلم عن " + item["ar"] + "."
            egy = "في الكلاس بنتكلم عن " + item["ar_egy"] + "."
        else:
            example = "Quiero aprender más sobre " + entry + "."
            fusha = "أريد أن أعرف أكثر عن " + item["ar"] + "."
            egy = "عايز أعرف أكتر عن " + item["ar_egy"] + "."
        item["example_es"] = example
        item["example_fusha"] = fusha
        item["example_egy"] = egy
        item["example_source"] = "pattern"


def validate_items(level, unit, items, seen):
    for item in items:
        label = f"{level.id}/{unit.id}: {item['es'] or '<empty>'}"
        if not item["es"].strip():
            fail(f"{label}: empty Spanish")
        if not ARABIC_RE.search(item["ar"]):
            fail(f"{label}: fusha translation missing or not Arabic")
        if not ARABIC_RE.search(item["ar_egy"]):
            fail(f"{label}: Egyptian translation missing or not Arabic")
        if item["type"] == "word" and item["pos"] != "letter" and LATIN_RE.search(item["ar"]):
            fail(f"{label}: Latin letters inside an Arabic translation")
        if item["es"].count("¿") != item["es"].count("?"):
            fail(f"{label}: unbalanced question marks")
        if item["es"].count("¡") != item["es"].count("!"):
            fail(f"{label}: unbalanced exclamation marks")
        key = norm_es(item["es"])
        if item["type"] == "word":
            if key in seen:
                fail(f"{label}: duplicate Spanish entry (also in {seen[key]})")
            else:
                seen[key] = f"{unit.id}"
        if item["type"] == "sentence" and len(tokens(item["es"])) < 2:
            fail(f"{label}: sentence needs at least two words")
        if not item.get("ipa"):
            item["ipa"] = to_ipa(item["es"])
        if item["type"] == "word" and not item["ipa"]:
            fail(f"{label}: cannot generate IPA")
        if item["es"] != item["es"].strip():
            fail(f"{label}: leading/trailing spaces")


def chunk(items, size):
    return [items[i:i + size] for i in range(0, len(items), size)]


def build_lessons(level, unit):
    lessons = []
    groups = chunk(unit.items, LESSON_SIZE)
    for index, group in enumerate(groups, 1):
        lessons.append({
            "id": f"{unit.id}_l{index}",
            "title": f"{unit.title} — درس {index}",
            "title_es": f"{unit.title_es} · parte {index}",
            "goal_egy": unit.goal,
            "items": [],
        })
    # ids are assigned later once the global item ids exist
    return lessons


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="validate only, do not write")
    args = parser.parse_args()

    levels = build_levels()
    dialogues = build_dialogues()
    grammar = build_grammar()
    collect_extra_examples(dialogues, grammar)

    output_levels = []
    item_counter = 0
    core_rank = 0

    for level in levels:
        out_units = []
        seen = {}
        for unit in level.units:
            attach_examples(unit.items)
            fill_phrase_examples(unit)
            fill_pattern_examples(unit)
            validate_items(level, unit, unit.items, seen)
            out_items = []
            for index, item in enumerate(unit.items, 1):
                item_counter += 1
                item_id = f"{unit.id}_{index:03d}"
                item["id"] = item_id
                item["freq"] = item_counter
                item["verification"] = "machine_checked"
                item.setdefault("example_source", "curriculum" if item.get("example_es") else "")
                attest(item)
                if item["type"] == "word":
                    core_rank += 1
                    item["core_rank"] = core_rank
                item.setdefault("level", level.id)
                out_items.append(item)
            groups = chunk(out_items, LESSON_SIZE)
            lessons = []
            for index, group in enumerate(groups, 1):
                lessons.append({
                    "id": f"{unit.id}_l{index}",
                    "title": f"{unit.title} — درس {index}",
                    "title_es": f"{unit.title_es} · parte {index}",
                    "goal_egy": unit.goal,
                    "items": [i["id"] for i in group],
                })
            out_units.append({
                "id": unit.id,
                "title": unit.title,
                "title_es": unit.title_es,
                "goal_egy": unit.goal,
                "items": out_items,
                "lessons": lessons,
            })
        # dialogue comprehension unit for this level
        level_dialogues = [d for d in dialogues if d["level"] == level.id]
        if level_dialogues:
            unit_id = f"{level.id.lower()}_dialogues"
            d_items = []
            d_lessons = []
            for index, dialogue in enumerate(level_dialogues, 1):
                if not dialogue["lines"]:
                    fail(f"dialogue {dialogue['id']}: no lines")
                if len(dialogue["lines"]) < 4:
                    fail(f"dialogue {dialogue['id']}: needs at least four lines")
                if len(dialogue["questions"]) < 2:
                    fail(f"dialogue {dialogue['id']}: needs at least two questions")
                for q_index, question in enumerate(dialogue["questions"]):
                    if not (0 <= question["answer"] < len(question["options"])):
                        fail(f"dialogue {dialogue['id']}: answer out of range")
                    question["id"] = f"{dialogue['id']}_q{q_index + 1}"
                exercises = []
                listen_line = dialogue["lines"][0]
                exercises.append({
                    "id": f"{dialogue['id']}_listen",
                    "type": "listen",
                    "audio": listen_line["es"],
                    "prompt_egy": "اسمع أول جملة في المكالمة واختار الشكل الإسباني الصحيح.",
                    "options": [listen_line["es"]] + [l["es"] for l in dialogue["lines"][1:4]],
                    "answer": 0,
                    "explain_egy": f"أول جملة: {listen_line['es']} — {listen_line['egy'] or listen_line['ar']}",
                })
                # meaning question built from the dialogue itself (Arabic gloss of a real line)
                target = dialogue["lines"][1] if len(dialogue["lines"]) > 1 else dialogue["lines"][0]
                target_text = target["egy"] or target["ar"]
                glosses = [target_text]
                for line in dialogue["lines"]:
                    value = line["egy"] or line["ar"]
                    if value and value not in glosses:
                        glosses.append(value)
                    if len(glosses) == 3:
                        break
                if len(glosses) == 3:
                    slot = len(dialogue["id"]) % 3
                    ordered = glosses[1:slot + 1] + [target_text] + glosses[slot + 1:]
                    exercises.append({
                        "id": f"{dialogue['id']}_meaning",
                        "type": "choose",
                        "prompt_egy": "اقرأ (أو اسمع) هذه الجملة من المكالمة، معناها إيه؟ — «" + target["es"] + "»",
                        "options": ordered,
                        "answer": slot,
                        "explain_egy": "المعنى الصحيح: " + target_text,
                    })
                exercises.extend(dialogue["questions"])
                d_lessons.append({
                    "id": f"{dialogue['id']}_lesson",
                    "title": dialogue["title_ar"] or dialogue["title_egy"],
                    "title_es": dialogue["title_es"],
                    "goal_egy": dialogue["context_egy"],
                    "items": [],
                    "exercises": exercises,
                    "dialogue": dialogue["id"],
                })
            if d_lessons:
                out_units.append({
                    "id": unit_id,
                    "title": "استماع وفهم المحادثات",
                    "title_es": "Comprensión auditiva",
                    "goal_egy": "تسمع حوارات حقيقية وتجاوب على أسئلة الفهم.",
                    "items": d_items,
                    "lessons": d_lessons,
                    "kind": "dialogue",
                })
        output_levels.append({
            "id": level.id,
            "title": level.title,
            "title_es": level.title_es,
            "goal_egy": level.goal,
            "desc_egy": level.desc,
            "units": out_units,
        })

    for dialogue in dialogues:
        for line in dialogue["lines"]:
            if not ARABIC_RE.search(line["ar"]) or not ARABIC_RE.search(line["egy"]):
                fail(f"dialogue {dialogue['id']}: line without Arabic translations: {line['es']}")
        for question in dialogue["questions"]:
            for option in question["options"]:
                if not option.strip():
                    fail(f"dialogue {dialogue['id']}: empty option")

    for rule in grammar:
        if not rule["examples"]:
            fail(f"grammar {rule['id']}: needs at least one example")

    total_items = sum(len(u["items"]) for level in output_levels for u in level["units"])
    total_words = sum(1 for level in output_levels for u in level["units"] for i in u["items"] if i["type"] == "word")
    total_sentences = sum(1 for level in output_levels for u in level["units"] for i in u["items"] if i["type"] == "sentence")
    total_lessons = sum(len(u["lessons"]) for level in output_levels for u in level["units"])
    stats = {
        "items": total_items,
        "words": total_words,
        "sentences": total_sentences,
        "lessons": total_lessons,
        "dialogues": len(dialogues),
        "grammar_rules": len(grammar),
        "levels": len(output_levels),
    }

    if problems:
        print(f"{len(problems)} content problem(s):", file=sys.stderr)
        for problem in problems[:60]:
            print("  -", problem, file=sys.stderr)
        sys.exit(1)

    model = {
        "schema_version": 3,
        "generated": date.today().isoformat(),
        "stats": stats,
        "levels": output_levels,
        "dialogues": dialogues,
        "grammar": grammar,
        "sources": [
            {
                "id": "frequency_words",
                "title": "hermitdave/FrequencyWords — es_50k (OpenSubtitles 2018)",
                "url": "https://github.com/hermitdave/FrequencyWords",
                "license": "MIT (repository) · corpus: OpenSubtitles 2018",
                "supports": ["orthography", "frequency"],
                "does_not_support": ["meaning", "ipa", "cefr_level", "example_sentence"],
            }
        ],
        "source_status_note": "spelling/frequency only: an item is source-attested when its Spanish form appears in the frequency corpus. Arabic meanings, examples and IPA are machine-generated/authored and are NOT source-verified. No item is marked verified.",
        "audio_note": "النطق داخل التطبيق يستخدم محرك TTS في الجهاز بصوت es-ES عند توفره، ولا يوجد تسجيل بشري.",
        "verification_note": "المحتوى مفحوص آليًا (تهجئة، ترقيم، ازدواجية، اكتمال الترجمة الفصحى والمصرية) لكنه لا يحمل حالة verified بمراجعة بشرية أو مصدر مستقل لكل عنصر.",
    }

    if args.check:
        print(json.dumps(stats, ensure_ascii=False))
        return

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        json.dump(model, handle, ensure_ascii=False, separators=(",", ":"))
    size = os.path.getsize(OUTPUT)
    print(json.dumps(stats, ensure_ascii=False))
    print(f"wrote {OUTPUT} ({size / 1024:.0f} KB)")

    write_report(model, stats, size)


def write_report(model, stats, size):
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    lines = ["# تقرير المحتوى — Spanish 1000", "", f"تم التوليد: {model['generated']}", ""]
    lines.append(f"- إجمالي العناصر: **{stats['items']}**")
    lines.append(f"- كلمات وتعبيرات: **{stats['words']}**")
    lines.append(f"- جمل استخدام: **{stats['sentences']}**")
    lines.append(f"- دروس: **{stats['lessons']}**")
    lines.append(f"- حوارات: **{stats['dialogues']}** • قواعد: **{stats['grammar_rules']}**")
    lines.append(f"- حجم الملف: {size / 1024:.0f} KB")
    lines.append("")
    lines.append("| المستوى | الوحدات | الدروس | العناصر | كلمات | جمل |")
    lines.append("|---|---|---|---|---|---|")
    for level in model["levels"]:
        units = len(level["units"])
        lessons = sum(len(u["lessons"]) for u in level["units"])
        items = sum(len(u["items"]) for u in level["units"])
        words = sum(1 for u in level["units"] for i in u["items"] if i["type"] == "word")
        sentences = sum(1 for u in level["units"] for i in u["items"] if i["type"] == "sentence")
        lines.append(f"| {level['id']} | {units} | {lessons} | {items} | {words} | {sentences} |")
    lines.append("")
    lines.append("## حالة التحقق")
    lines.append("كل عنصر: `machine_checked` — مر بفحص آلي للتهجئة والترقيم والتكرار واكتمال الترجمة الفصحى والمصرية. "
                 "لا يوجد عنصر `verified`، لأن التحقق النهائي يحتاج مراجعة لغوية بشرية أو مصدر مستقل لكل مدخل.")
    with open(REPORT, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
