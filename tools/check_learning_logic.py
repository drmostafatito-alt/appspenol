"""Compile and run focused regression assertions for the production Java logic.

Requires only Python 3 and a JDK on PATH. No Android SDK or Python packages.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/habla/espanol/LearningLogic.java"
JAVA_HOME = Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None
JAVAC = str(JAVA_HOME / "bin" / ("javac.exe" if os.name == "nt" else "javac")) if JAVA_HOME else shutil.which("javac")
JAVA = str(JAVA_HOME / "bin" / ("java.exe" if os.name == "nt" else "java")) if JAVA_HOME else shutil.which("java")

TEST_SOURCE = r'''package com.habla.espanol;
import java.util.Arrays;
import java.util.List;
public final class LearningLogicCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static boolean has(List<LearningLogic.Step> steps, int type, int index) {
        return steps.stream().anyMatch(step -> step.type == type && step.itemIndex == index);
    }
    public static void main(String[] args) {
        check(LearningLogic.matches("¿Cómo estás?", "como estas"), "accents and punctuation");
        check(LearningLogic.matches("Estoy cansado/a", "estoy cansada"), "female form");
        check(LearningLogic.matches("Estoy cansado/a", "estoy cansado"), "male form");
        check(LearningLogic.matches("Me llamo...", "me llamo Tito"), "filled trailing template");
        check(!LearningLogic.matches("Me llamo...", "me llamo"), "empty trailing template rejected");
        check(LearningLogic.matches("Tengo ... años", "tengo veinte años"), "filled middle template");
        check(!LearningLogic.matches("Tengo ... años", "tengo años"), "empty middle template rejected");
        check(!LearningLogic.matches("Hola", "adios"), "wrong answer rejected");

        List<LearningLogic.Step> duplicates = LearningLogic.lessonPlan(
                Arrays.asList("uno", "dos", "tres", "cuatro", "cinco"),
                Arrays.asList("واحد", "اثنان", "ثلاثة", "ما رأيك؟", "ما رأيك؟"));
        check(has(duplicates, LearningLogic.MATCH, 2), "matching waits for three unique pairs");
        List<LearningLogic.Step> distinct = LearningLogic.lessonPlan(
                Arrays.asList("uno", "dos", "tres", "cuatro"),
                Arrays.asList("واحد", "اثنان", "ثلاثة", "أربعة"));
        check(has(distinct, LearningLogic.MATCH, 2), "matching available with three unique pairs");
        List<LearningLogic.Step> tooFew = LearningLogic.lessonPlan(
                Arrays.asList("uno", "dos"), Arrays.asList("واحد", "اثنان"));
        check(tooFew.stream().noneMatch(step -> step.type == LearningLogic.MATCH),
                "matching omitted when fewer than three pairs are available");

        check(!LearningLogic.canProduce("Me llamo..."), "templates use fallback exercise");
        check(LearningLogic.canProduce("Buenos días"), "ordinary phrase can be produced");
        long day = 86_400_000L;
        check(LearningLogic.flashcardInterval(5 * day, 2.5f, 0) == 0, "forgotten item resets");
        check(LearningLogic.flashcardInterval(5 * day, 2.5f, 1) == day, "hard item interval");
        check(LearningLogic.flashcardInterval(2 * day, 2.5f, 2) == 3 * day, "medium item interval");
        check(LearningLogic.flashcardInterval(day, 2.5f, 3) == (long)(2.5 * day), "easy item interval");
        check(LearningLogic.flashcardInterval(30 * day, 3f, 3) == 30 * day, "interval cap");
        System.out.println("Learning logic checks passed.");
    }
}'''


def main():
    if not SOURCE.is_file():
        raise SystemExit(f"Production source not found: {SOURCE}")
    if not JAVAC or not JAVA or not Path(JAVAC).is_file() or not Path(JAVA).is_file():
        raise SystemExit("A JDK is required: add its bin directory to PATH or set JAVA_HOME.")
    with tempfile.TemporaryDirectory(prefix="habla-learning-test-") as temp:
        output = Path(temp)
        test_file = output / "LearningLogicCheck.java"
        test_file.write_text(TEST_SOURCE, encoding="utf-8")
        subprocess.run(
            [JAVAC, "-encoding", "UTF-8", "-d", str(output), str(SOURCE), str(test_file)],
            check=True,
        )
        subprocess.run(
            [JAVA, "-cp", str(output), "com.habla.espanol.LearningLogicCheck"],
            check=True,
        )


if __name__ == "__main__":
    main()
