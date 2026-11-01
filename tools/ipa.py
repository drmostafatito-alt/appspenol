#!/usr/bin/env python3
"""Rule-based Spanish grapheme to IPA (Castilian) converter.

A full phonetic transcriptor needs a lexicon; this converter implements the
orthographic rules of the RAE (stress placement, digraphs, context dependent
c/z/g/d/b/v, syllabification) so every item in the curriculum gets a consistent,
machine generated transcription. Outputs are bounded by those rules, so the app
labels them `machine_checked` rather than `sound_verified`.
"""
import re
import unicodedata

VOWELS = "aeiouáéíóúü"
STRONG = "aeoáéó"
WEAK = "iuíúü"

DIGRAPHS = ("ch", "ll", "rr", "qu", "gu", "gü")

ONSET_CLUSTERS = {
    "pr", "br", "tr", "dr", "cr", "gr", "fr", "vr",
    "pl", "bl", "cl", "gl", "fl", "tl",
}


def _strip_accents(text):
    return "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")


def _is_vowel(c):
    return c in VOWELS


def syllables(word):
    """Split a Spanish word into syllables (grapheme level)."""
    w = word.lower()
    w = re.sub(r"[^a-záéíóúüñ]", "", w)
    if not w:
        return []
    # mark vowel nuclei groups
    groups = []
    i = 0
    while i < len(w):
        c = w[i]
        if not _is_vowel(c):
            i += 1
            continue
        start = i
        # strong vowel absorbs weak neighbours; two weak vowels form a diphthong
        i += 1
        while i < len(w):
            prev = w[i - 1]
            cur = w[i]
            if not _is_vowel(cur):
                break
            if cur in "íú" or prev in "íú" or prev in "áéó" or cur in "áéó":
                break
            i += 1
        groups.append((start, i))
    if not groups:
        return [w]
    boundaries = []
    for a, b in zip(groups, groups[1:]):
        between = w[a[1]:b[0]]
        if not between:
            cut = b[0]
        elif len(between) == 1:
            cut = b[0] - 1
        elif between in ONSET_CLUSTERS:
            cut = b[0] - 2
        else:
            cut = b[0] - 1
        boundaries.append(cut)
    parts = []
    begin = 0
    for cut in boundaries:
        parts.append(w[begin:cut])
        begin = cut
    parts.append(w[begin:])
    return [p for p in parts if p]


def stressed_syllable(word):
    """Index of the stressed syllable following RAE rules."""
    parts = syllables(word)
    if not parts:
        return 0
    lowered = word.lower()
    for index, part in enumerate(parts):
        if any(v in part for v in "áéíóú"):
            return index
    if any(ch in lowered for ch in ("á", "é", "í", "ó", "ú")):
        return 0
    if lowered.endswith(("a", "e", "i", "o", "u", "n", "s")):
        return max(0, len(parts) - 2)
    return len(parts) - 1


def _phonemes(word):
    """Per-character phoneme list for a lower-case Spanish word."""
    w = word.lower()
    w = re.sub(r"[^a-záéíóúüñ]", "", w)
    n = len(w)
    out = [""] * n
    i = 0

    def prev_char(idx):
        return w[idx - 1] if idx > 0 else ""

    def next_char(idx):
        return w[idx + 1] if idx + 1 < n else ""

    def vowel_phoneme(c):
        return {"á": "a", "é": "e", "í": "i", "ó": "o", "ú": "u", "ü": "u"}.get(c, c)

    while i < n:
        c = w[i]
        nxt = next_char(i)
        prev = prev_char(i)
        # digraphs
        if w.startswith("ch", i):
            out[i] = "t͡ʃ"
            out[i + 1] = ""
            i += 2
            continue
        if w.startswith("ll", i):
            out[i] = "ʝ"
            out[i + 1] = ""
            i += 2
            continue
        if w.startswith("rr", i):
            out[i] = "r"
            out[i + 1] = ""
            i += 2
            continue
        if w.startswith("qu", i) and nxt and next_char(i + 1) in "eéií":
            out[i] = "k"
            out[i + 1] = ""
            i += 2
            continue
        if w.startswith("gu", i) and next_char(i + 1) in "eéií":
            out[i] = "ɣ" if _between_vowels(w, i) else "g"
            out[i + 1] = ""
            i += 2
            continue
        if w.startswith("gü", i):
            out[i] = "ɡ"
            out[i + 1] = "w"
            i += 2
            continue
        if _is_vowel(c):
            phoneme = vowel_phoneme(c)
            # glides inside diphthongs
            if c == "i" and nxt and nxt in STRONG:
                phoneme = "j"
            elif c == "u" and nxt and nxt in STRONG:
                phoneme = "w"
            out[i] = phoneme
            i += 1
            continue
        if c == "ñ":
            out[i] = "ɲ"
        elif c == "h":
            out[i] = ""
        elif c == "j":
            out[i] = "x"
        elif c == "x":
            out[i] = "ks"
        elif c == "z":
            out[i] = "θ"
        elif c == "c":
            if nxt in "eéií":
                out[i] = "θ"
            elif prev == "c":
                out[i] = ""
            else:
                out[i] = "k"
        elif c == "s":
            out[i] = "s"
        elif c == "g":
            if nxt in "eéií":
                out[i] = "x"
            elif _between_vowels(w, i):
                out[i] = "ɣ"
            else:
                out[i] = "ɡ"
        elif c in "b":
            out[i] = "β" if _lenites(w, i) else "b"
        elif c == "v":
            out[i] = "β" if _lenites(w, i) else "b"
        elif c == "d":
            if _between_vowels(w, i):
                out[i] = "ð"
            elif i == n - 1:
                out[i] = "θ"
            else:
                out[i] = "d"
        elif c == "r":
            if i == 0 or prev in "nl s":
                out[i] = "r"
            elif nxt == "r":
                out[i] = "r"
            else:
                out[i] = "ɾ"
        elif c == "w":
            out[i] = "w"
        elif c == "y":
            if i == n - 1 and prev:
                out[i] = "i"
            else:
                out[i] = "ʝ"
        elif c == "ü":
            out[i] = "w"
        else:
            out[i] = c
        i += 1
    return out


def _between_vowels(word, index):
    return (index > 0 and _is_vowel(word[index - 1])
            and index + 1 < len(word) and _is_vowel(word[index + 1]))


def _lenites(word, index):
    """b/v/d/g fricatives between vowels (or after /ɾ/ in rapid speech)."""
    if index == 0:
        return False
    prev = word[index - 1]
    return _is_vowel(prev)


def ipa(word):
    """IPA transcription with syllable dots and stress mark."""
    if not word:
        return ""
    raw = word.strip()
    if " " in raw:
        return " ".join(ipa(part) for part in raw.split())
    parts = syllables(raw)
    if not parts:
        return ""
    phonemes = _phonemes(raw)
    # map syllable boundaries onto phoneme list positions
    boundaries = []
    cursor = 0
    for part in parts[:-1]:
        cursor += len(re.sub(r"[^a-záéíóúüñ]", "", part))
        boundaries.append(cursor)
    pieces = []
    previous = 0
    stress = stressed_syllable(raw)
    # count phoneme lengths ignoring dropped characters
    starts = []
    pos = 0
    for part in parts:
        clean = re.sub(r"[^a-záéíóúüñ]", "", part)
        starts.append(pos)
        pos += len(clean)
    for index, part in enumerate(parts):
        start = starts[index]
        end = starts[index + 1] if index + 1 < len(parts) else len(phonemes)
        chunk = "".join(p for p in phonemes[start:end] if p)
        if index == stress:
            chunk = "ˈ" + chunk
        pieces.append(chunk)
    transcription = ".".join(p for p in pieces if p)
    return transcription


if __name__ == "__main__":
    samples = ["hola", "gracias", "agua", "casa", "español", "perro", "pero",
               "llave", "queso", "guitarra", "ahora", "cómo estás", "mañana",
               "trabajo", "ciudad", "elegir", "hijo", "buenos días", "¿dónde está?"]
    for sample in samples:
        print(sample.ljust(18), ipa(sample))
