"""Regression tests for the repository validators. Run: python -m unittest discover -s tests"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def item(es, ar):
    return {"es": es, "ar": ar}


def write_content(tracks):
    f = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
    json.dump({"tracks": tracks}, f, ensure_ascii=False)
    f.close()
    return f.name


class ContentValidationTests(unittest.TestCase):
    def run_validator(self, path):
        return subprocess.run([sys.executable, str(ROOT / "tools/validate_content.py"), path],
                              capture_output=True, text=True)

    def test_empty_translation_fails(self):
        path = write_content([{"title": "t", "units": [{"title": "u", "items": [item("Hola", "")]}]}])
        result = self.run_validator(path)
        self.assertEqual(result.returncode, 1)
        self.assertIn("empty Arabic translation", result.stdout)

    def test_item_without_spanish_letters_fails(self):
        path = write_content([{"title": "t", "units": [{"title": "u", "items": [item("¿¡?", "x")]}]}])
        self.assertEqual(self.run_validator(path).returncode, 1)

    def test_valid_minimal_content_passes_with_warning_only(self):
        path = write_content([{"title": "t", "units": [{"title": "u", "items": [item("Hola", "مرحبًا")]}]}])
        result = self.run_validator(path)
        self.assertEqual(result.returncode, 0)
        self.assertIn("target 1000", result.stdout)

    def test_real_content_has_no_errors(self):
        result = self.run_validator(str(ROOT / "app/src/main/assets/content.json"))
        self.assertEqual(result.returncode, 0, result.stdout)


class ResourceValidationTests(unittest.TestCase):
    def test_repository_resources_are_valid(self):
        result = subprocess.run([sys.executable, str(ROOT / "tools/validate_resources.py")],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout)


class LearningLogicTests(unittest.TestCase):
    def test_java_logic_checks(self):
        result = subprocess.run([sys.executable, str(ROOT / "tools/check_learning_logic.py")],
                                capture_output=True, text=True)
        if result.returncode != 0 and "JDK is required" in result.stderr:
            self.skipTest("no JDK on PATH")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
