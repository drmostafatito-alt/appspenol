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
        it.update({"verification": "verified", "ar_egy": "أهلاً", "source": "RAE",
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
        self.assertEqual(total, 2000)
        self.assertEqual(pub, 0)


if __name__ == "__main__":
    unittest.main()
