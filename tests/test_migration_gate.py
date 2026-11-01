"""Tests for the v2 schema migration and the content publish gate."""
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load(name, rel):
    spec = importlib.util.spec_from_file_location(name, ROOT / rel)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


mig = load("mig", "tools/migrate_content_schema.py")
gate = load("gate", "tools/content_gate.py")


def sample():
    return {"tracks": [{"title": "t", "units": [{"title": "u", "items": [{"es": "Hola", "ar": "مرحبًا"}]}]}]}


class MigrationTests(unittest.TestCase):
    def test_forward_does_not_copy_ar_into_egyptian(self):
        d = mig.forward(sample())
        it = d["tracks"][0]["units"][0]["items"][0]
        self.assertEqual(it["es"], "Hola")
        self.assertEqual(it["ar"], "مرحبًا")
        self.assertIsNone(it["ar_egy"])
        self.assertEqual(it["verification"], "unverified")
        self.assertEqual(d["schema_version"], 2)

    def test_revert_restores_v1_exactly(self):
        original = sample()
        round_trip = mig.revert(mig.forward(sample()))
        self.assertEqual(round_trip, original)

    def test_forward_keeps_existing_values(self):
        d = sample()
        d["tracks"][0]["units"][0]["items"][0]["verification"] = "verified"
        out = mig.forward(d)
        self.assertEqual(out["tracks"][0]["units"][0]["items"][0]["verification"], "verified")


class GateTests(unittest.TestCase):
    def test_unverified_item_not_publishable(self):
        d = mig.forward(sample())
        self.assertEqual(gate.gate(d)[1], 0)

    def test_fully_verified_item_publishable(self):
        d = mig.forward(sample())
        it = d["tracks"][0]["units"][0]["items"][0]
        it.update({"verification": "verified", "ar_egy": "أهلاً", "source": "RAE", "pronunciation": "أولا",
                   "example_es": "Hola, ¿qué tal?", "example_fusha": "مرحبًا، كيف حالك؟",
                   "example_egy": "أهلاً، عامل إيه؟"})
        self.assertEqual(gate.gate(d)[1], 1)

    def test_missing_source_blocks_publish(self):
        d = mig.forward(sample())
        it = d["tracks"][0]["units"][0]["items"][0]
        it.update({"verification": "verified", "ar_egy": "أهلاً", "example_es": "x",
                   "example_fusha": "y", "example_egy": "z"})
        self.assertEqual(gate.gate(d)[1], 0)


class LiveContentTests(unittest.TestCase):
    def test_bundled_content_is_v2_and_unpublished_items_are_not_marked_verified(self):
        data = json.loads((ROOT / "app/src/main/assets/content.json").read_text(encoding="utf-8"))
        self.assertEqual(data.get("schema_version"), 2)
        total, pub = gate.gate(data)
        self.assertEqual(total, sum(len(u["items"]) for t in data["tracks"] for u in t["units"]))
        self.assertEqual(pub, 0)

    def test_a0_track_is_appended_and_complete(self):
        data = json.loads((ROOT / "app/src/main/assets/content.json").read_text(encoding="utf-8"))
        ids = [t.get("id") for t in data["tracks"]]
        self.assertEqual(ids[:2], [None, None], "legacy tracks must keep their positions")
        self.assertEqual(ids[2], "A0", "A0 must be appended, not inserted before saved tracks")
        items = [it for u in data["tracks"][2]["units"] for it in u["items"]]
        self.assertGreaterEqual(len(items), 80)
        for it in items:
            for k in ("es", "ar", "ar_egy", "pronunciation", "example_es", "source"):
                self.assertTrue(str(it.get(k, "")).strip(), f"{k} empty in {it.get('es')}")
        self.assertTrue(all(it["verification"] in ("pending_verification", "sound_verified") for it in items))
        self.assertTrue(all(it.get("ipa") for it in items), "every A0 item needs an IPA transcription")
        self.assertFalse(any(it["verification"] == "verified" for it in items), "no A0 item may be fully verified yet")
        self.assertEqual(gate.gate(data)[1], 0)

    def test_a0_spanish_text_is_unique(self):
        data = json.loads((ROOT / "app/src/main/assets/content.json").read_text(encoding="utf-8"))
        items = [it["es"] for u in data["tracks"][2]["units"] for it in u["items"]]
        self.assertEqual(len(items), len(set(items)))

    def test_phrase_requires_full_examples_but_letter_does_not(self):
        phrase = {"type": "phrase", "verification": "verified", "ar_egy": "x", "pronunciation": "x",
                  "example_es": "x", "source": "s"}
        letter = dict(phrase, type="letter")
        self.assertIn("example_fusha", gate.missing_fields(phrase))
        self.assertEqual(gate.missing_fields(letter), [])


if __name__ == "__main__":
    unittest.main()
