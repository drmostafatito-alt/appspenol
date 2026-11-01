# Spanish 1000 (Habla)

An offline-first Android app for practicing Spanish with Arabic explanations. The application is Java-based and stores learning progress locally with Android `SharedPreferences`.

## Project map

- `app/src/main/java/com/habla/espanol/` — Android screens and learning logic. `LearningLogic.java` contains platform-independent answer matching, lesson scheduling, and review interval logic.
- `app/src/main/assets/content.json` — lesson curriculum loaded by the app at runtime.
- `app/src/main/res/` — Android resources.
- `app/src/main/assets/fonts/` — bundled fonts and their licenses.
- `releases/` — previously built APKs and the delivery report; these are artifacts, not build inputs.
- `COMPLETION_REPORT.md` — current known implementation gaps and verification limits. Read it before describing the project as finished or release-ready.

## Requirements

- JDK 17 or newer (the project targets Java 17).
- Android SDK with Android platform/build tools 36.
- Gradle/Android Gradle Plugin versions supported by this project. The plugin version is declared in `build.gradle`; do not change tool versions without checking compatibility and preserving reproducibility.
- Python 3 only for the portable logic test; no third-party Python packages are needed.

Open this directory as an Android Studio project or use the Gradle wrapper if one is present. On a clean checkout, first run `gradle tasks` (or `./gradlew tasks` when a wrapper has been added) to check the environment and resolve the declared Android Gradle Plugin. Do not commit local SDK configuration, credentials, or generated build directories.

## Build and verify

From this directory:

```sh
gradle :app:assembleDebug
gradle :app:lintDebug
python tools/check_learning_logic.py
```

The portable logic test compiles the actual production `LearningLogic.java` with the local JDK into a temporary directory and runs assertions for phrase/template answers, matching-pair scheduling with duplicate translations, lesson progression, and flashcard intervals. It needs no Android SDK or dependencies. On Windows, run it with `py tools/check_learning_logic.py` or `python tools/check_learning_logic.py`.

A successful build or logic test does **not** verify the Android UI, device speech services, notifications, import/export, persistence across an upgrade, or behavior on a physical device/emulator. Run and inspect the app on a device before making claims about those behaviors. Check `releases/FINAL_REPORT.md` for the exact status of the currently delivered APKs.

## Guidance for AI coding agents and contributors

- Read this README, `COMPLETION_REPORT.md`, the relevant source, and existing tests before editing. Treat the implementation and reports as the source of truth; do not invent features, completed checks, or curriculum content.
- Keep changes focused, follow existing Java/Android conventions, and avoid adding dependencies unless necessary and explicitly justified.
- Preserve offline behavior and existing preference keys/data. Treat imported backup data as untrusted input; validate it before applying it.
- Keep tests deterministic and runnable on a clean checkout. Do not depend on local SDK/JDK paths or developer machines.
- After changing learning logic or curriculum scheduling, run `python tools/check_learning_logic.py`. For Android changes, run the narrowest applicable Gradle build/lint task when the Android SDK and network/dependencies are available.
- Do not commit API keys, signing credentials, `local.properties`, `.tools/`, `.gradle/`, or `build/` output. Do not overwrite history or push to another branch without the repository owner's request.
- Report precisely what was tested and what could not be tested. A compiled APK is not proof of runtime or UI correctness.

## Content changes

`content.json` is consumed directly by the app. Preserve its JSON schema (`tracks` → `units` → `items`, with Spanish `es` and Arabic `ar` strings). If curriculum content changes, validate the JSON and confirm both tracks remain complete; do not make up translations or claim language review without a qualified review.
