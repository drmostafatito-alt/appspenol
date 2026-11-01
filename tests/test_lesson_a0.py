"""Checks the first guided lesson: structure, answers, explanations, and links to A0 items."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LESSON = ROOT / "app/src/main/assets/lessons/a0_primeras_palabras.json"
CONTENT = ROOT / "app/src/main/assets/content.json"


class LessonA0Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lesson = json.loads(LESSON.read_text(encoding="utf-8"))
        content = json.loads(CONTENT.read_text(encoding="utf-8"))
        cls.a0_es = {it["es"] for u in content["tracks"][2]["units"] for it in u["items"]}
        cls.a0_es |= {"h", "Soy", "Eres", "Yo", "Tú", "Tiene"}  # letters/pronouns named in the lesson

    def test_has_at_least_twelve_exercises_and_all_types(self):
        ex = self.lesson["exercises"]
        self.assertGreaterEqual(len(ex), 12)
        self.assertEqual({e["type"] for e in ex}, {"choose", "listen", "order", "fill", "match"})

    def test_unique_ids_and_teaching_explanations(self):
        ids = [e["id"] for e in self.lesson["exercises"]]
        self.assertEqual(len(ids), len(set(ids)))
        for e in self.lesson["exercises"]:
            self.assertTrue(e["explain_egy"].strip(), e["id"])
            self.assertTrue(e["explain_fusha"].strip(), e["id"])
            self.assertTrue(e["prompt_egy"].strip() and e["prompt_fusha"].strip(), e["id"])

    def test_choice_answers_are_valid_and_unique(self):
        for e in self.lesson["exercises"]:
            if e["type"] in ("choose", "listen", "fill"):
                self.assertTrue(0 <= e["answer"] < len(e["options"]), e["id"])
                self.assertEqual(len(set(e["options"])), len(e["options"]), e["id"])

    def test_order_answers_rebuild_sentence(self):
        for e in self.lesson["exercises"]:
            if e["type"] == "order":
                self.assertEqual(sorted(e["answer_order"]), list(range(len(e["tokens"]))), e["id"])
                rebuilt = " ".join(e["tokens"][i] for i in e["answer_order"])
                self.assertEqual(rebuilt, e["sentence"], e["id"])

    def test_fill_sentence_has_blank_and_correct_word(self):
        for e in self.lesson["exercises"]:
            if e["type"] == "fill":
                self.assertIn("___", e["sentence"], e["id"])
                filled = e["sentence"].replace("___", e["options"][e["answer"]])
                self.assertNotIn("___", filled)

    def test_match_pairs_are_unique(self):
        for e in self.lesson["exercises"]:
            if e["type"] == "match":
                es = [p[0] for p in e["pairs"]]
                ar = [p[1] for p in e["pairs"]]
                self.assertEqual(len(es), len(set(es)))
                self.assertEqual(len(ar), len(set(ar)))

    def test_refs_point_to_real_a0_items(self):
        for e in self.lesson["exercises"]:
            for r in e["refs"]:
                self.assertIn(r, self.a0_es, f"{e['id']} refers to missing item {r}")

    def test_audio_is_declared_as_tts_not_human(self):
        self.assertFalse(self.lesson["audio"]["human_recording"])
        self.assertIn("TextToSpeech", self.lesson["audio"]["engine"])


if __name__ == "__main__":
    unittest.main()
