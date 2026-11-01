#!/usr/bin/env python3
"""Compile and run the pure-Java test suite on the local JVM.

The tests exercise the real shipped content (assets/content.json) and the logic
classes: JSON parser, curriculum loader, lesson planner, answer checker, spaced
repetition scheduler and XP policy. Android UI classes cannot run without a
device, so they are deliberately excluded from this harness.
"""
import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "build", "jvm-tests")
JAVA = os.path.join(ROOT, ".tools", "jdkpkg", "jdk4py", "java-runtime", "bin", "java")
ECJ = os.path.join(ROOT, ".tools", "minapk", "package", "tools", "ecj-3.45.0.jar")
ANDROID_API = os.path.join(ROOT, ".tools", "platforms", "android-36", "android-api.jar")

LOGIC_SOURCES = [
    "Json.java", "Curriculum.java", "TextNormalizers.java", "AnswerChecker.java",
    "ReviewScheduler.java", "Tutor.java", "XpPolicy.java", "LessonPlanner.java", "LearningLogic.java",
    "RealLife.java",
]


def run(cmd):
    print("+", " ".join(cmd))
    proc = subprocess.run(cmd)
    if proc.returncode != 0:
        sys.exit(proc.returncode)


def main():
    if not os.path.exists(JAVA) or not os.path.exists(ECJ):
        sys.exit("toolchain missing — run python3 tools/fetch_toolchain.py")
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT, exist_ok=True)
    sources = [os.path.join(ROOT, "app", "src", "main", "java", "com", "habla", "espanol", name)
               for name in LOGIC_SOURCES]
    sources += sorted(glob.glob(os.path.join(ROOT, "tools", "java_tests", "*.java")))
    argfile = os.path.join(OUT, "sources.txt")
    with open(argfile, "w", encoding="utf-8") as handle:
        handle.write("\n".join(sources))
    run([JAVA, "-cp", ECJ, "org.eclipse.jdt.internal.compiler.batch.Main",
         "-17", "-nowarn", "-proc:none", "-encoding", "UTF-8",
         "-cp", ANDROID_API, "-d", OUT, "@" + argfile])
    content = os.path.join(ROOT, "app", "src", "main", "assets", "content.json")
    run([JAVA, "-cp", OUT, "com.habla.espanol.TestRunner", content])


if __name__ == "__main__":
    main()
