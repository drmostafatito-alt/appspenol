# Spanish 1000 (Habla)

An offline-first Android app for practicing Spanish with Arabic explanations. The application is Java-based and stores learning progress locally with Android `SharedPreferences`.

## Project map

- `app/src/main/java/com/habla/espanol/` — Android screens and learning logic. `LearningLogic.java` contains platform-independent answer matching, lesson scheduling, and review interval logic.
- `app/src/main/assets/content.json` — generated curriculum loaded by the app at runtime (schema v3: `levels` → `units` → `items`, plus `dialogues` and `grammar`).
- `content/levels/*.curriculum` — human-editable curriculum source (A0…C1); `content/dialogues.curriculum` and `content/grammar.curriculum` hold dialogues and grammar rules.
- `tools/build_content.py` — builds and validates `content.json` from the `.curriculum` sources.
- `tools/build_apk.py` — signs and packages Debug/Release APKs without Gradle (aapt2 + ECJ + D8 + apksigner).
- `tools/run_java_tests.py` — pure-JVM harness for the Android-free logic classes.
- `app/src/main/res/` — Android resources.
- `app/src/main/assets/fonts/` — bundled fonts and their licenses.
- `releases/` — previously built APKs and the delivery report; these are artifacts, not build inputs.
- `COMPLETION_REPORT.md` — current known implementation gaps and verification limits. Read it before describing the project as finished or release-ready.

## Requirements

- A JRE 17+ (`java`) and the tools bundled under `.tools/` (aapt2, ECJ, D8, apksigner, `android.jar`) — see `tools/build_apk.py`.
- Android platform resources for API 36 (`android.jar`).
- Python 3 standard library only; no third-party packages are needed.
- Gradle is optional: this checkout has been built and signed with the direct toolchain in `tools/build_apk.py`.

The repository can still be opened in Android Studio, but the verified build path is the script below. Do not commit local SDK configuration, credentials, or generated build directories.

## Build and verify

From this directory:

```sh
python3 tools/build_content.py            # regenerate + validate content.json
python3 tools/validate_content.py         # independent structural/content checks
python3 tools/content_gate.py --ledger releases/CONTENT_LEDGER.csv
python3 tools/run_java_tests.py           # JVM logic tests (no device needed)
python3 tools/build_apk.py --variant debug
python3 tools/build_apk.py --variant release
```

`tools/build_content.py` writes `app/src/main/assets/content.json` and `releases/CONTENT_REPORT.md`.
`tools/run_java_tests.py` compiles the Android-free logic classes with ECJ and runs
`com.habla.espanol.TestRunner`, which validates content, lesson planning, answer grading,
spaced repetition, XP and the role-play scenarios.

The JVM harness needs no device and no Android runtime: it exercises the same Java classes the app
ships (`Curriculum`, `LessonPlanner`, `AnswerChecker`, `ReviewScheduler`, `Tutor`, `XpPolicy`,
`Json`, `TextNormalizers`, `RealLife`). It currently reports `PASS — 57052 checks`.

A successful build or logic test does **not** verify the Android UI, device speech services, notifications, import/export, persistence across an upgrade, or behavior on a physical device/emulator. Run and inspect the app on a device before making claims about those behaviors. Check `releases/FINAL_REPORT.md` for the exact status of the currently delivered APKs.

## Guidance for AI coding agents and contributors

- Read this README, `COMPLETION_REPORT.md`, the relevant source, and existing tests before editing. Treat the implementation and reports as the source of truth; do not invent features, completed checks, or curriculum content.
- Keep changes focused, follow existing Java/Android conventions, and avoid adding dependencies unless necessary and explicitly justified.
- Preserve offline behavior and existing preference keys/data. Treat imported backup data as untrusted input; validate it before applying it.
- Keep tests deterministic and runnable on a clean checkout. Do not depend on local SDK/JDK paths or developer machines.
- After changing learning logic or curriculum scheduling, run `python3 tools/run_java_tests.py`. For Android changes, run the narrowest applicable Gradle build/lint task when the Android SDK and network/dependencies are available.
- Do not commit API keys, signing credentials, `local.properties`, `.tools/`, `.gradle/`, or `build/` output. Do not overwrite history or push to another branch without the repository owner's request.
- Report precisely what was tested and what could not be tested. A compiled APK is not proof of runtime or UI correctness.

## Content changes

Edit the `.curriculum` sources under `content/`, then run `python3 tools/build_content.py`
(it refuses duplicates per level and Arabic-script violations) and `python3 tools/validate_content.py`.

`content.json` is consumed directly by the app. Preserve its schema (`levels` → `units` → `items`
with `es`, `ar` (MSA) and `ar_egy` (Egyptian) strings, plus `dialogues` and `grammar`). Every item
carries a `verification` status; automated checks only ever set `machine_checked`, never `verified`.
Do not claim language review without a qualified human review and a recorded source.
