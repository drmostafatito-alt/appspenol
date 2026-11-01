#!/usr/bin/env python3
"""Static audit of screen wiring in MainActivity.java (no device needed).

It cannot prove a button works when tapped, but it does catch the failure modes that make an
app feel unfinished: a screen method nobody calls, a back-navigation case that has no matching
screen, a screen that back-navigation cannot leave, a declared permission the code never uses,
and button factories that outnumber listeners.

Usage: python3 tools/audit_wiring.py [--write releases/WIRING_AUDIT.md]
Exit code 1 when a hard problem (unreachable screen / unmatched navigation case) is found.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ACTIVITY = os.path.join(ROOT, "app", "src", "main", "java", "com", "habla", "espanol", "MainActivity.java")
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def main(argv):
    source = read(ACTIVITY)
    manifest = read(MANIFEST)
    # permission usage may live in helper classes (receivers, notifications, speech), not only in the activity
    all_java = ""
    java_dir = os.path.dirname(ACTIVITY)
    for name in sorted(os.listdir(java_dir)):
        if name.endswith(".java"):
            all_java += read(os.path.join(java_dir, name))
    problems, notes = [], []

    # 1) screen methods and their call sites
    methods = sorted(set(re.findall(r"private\s+void\s+(show[A-Za-z0-9_]+)\s*\(", source)))
    entry_points = {"showWelcome", "onCreate", "home"}
    unreachable = []
    for method in methods:
        calls = len(re.findall(r"\b" + method + r"\s*\(", source))
        if calls <= 1 and method not in entry_points:
            unreachable.append(method)
    if unreachable:
        problems += ["screen method never called: " + name for name in unreachable]
    notes.append(f"screen methods: {len(methods)} ({', '.join(methods[:6])} …)")

    # 2) screen ids vs navigation switch
    screens = sorted(set(re.findall(r'screen\s*=\s*"([a-z_]+)"', source)))
    back_switch = re.search(r"private void navigateBack\(\).*?\n    \}", source, re.S)
    cases = set(re.findall(r'case "([a-z_]+)"', back_switch.group(0) if back_switch else ""))
    if not back_switch:
        problems.append("navigateBack() not found — hardware back handling is missing")
    else:
        unhandled = [screen for screen in screens if screen not in cases]
        # home and the tab screens are intentionally terminal states
        terminal = {"home", "welcome", "onboarding"}
        unhandled = [screen for screen in unhandled if screen not in terminal]
        if unhandled:
            problems += ["screen has no back-navigation case: " + name for name in unhandled]
        unknown_cases = [case for case in cases if case not in screens and case not in terminal]
        if unknown_cases:
            problems += ["back-navigation case without a screen assignment: " + name for name in unknown_cases]
    notes.append(f"screen ids: {len(screens)} • back-navigation cases: {len(cases)}")

    # 3) buttons vs listeners (factory call sites, ignoring the factory definition itself)
    button_calls = len(re.findall(r"=\s*button\(", source)) + len(re.findall(r"new Button\(", source))
    listeners = len(re.findall(r"setOnClickListener\(", source))
    notes.append(f"buttons created: {button_calls} • click listeners attached: {listeners}")
    if listeners < button_calls:
        problems.append(f"{button_calls - listeners} button(s) created without a click listener")

    # 4) permissions actually used
    permissions = re.findall(r'android:name="android\.permission\.([A-Z_]+)"', manifest)
    usage = {
        "RECORD_AUDIO": ["SpeechRecognizer"],
        "INTERNET": ["java.net", "HttpURLConnection", "URL("],
        "POST_NOTIFICATIONS": ["NotificationManager", "NotificationChannel"],
        "RECEIVE_BOOT_COMPLETED": ["BOOT_COMPLETED", "BootReceiver"],
        "ACCESS_NETWORK_STATE": ["ConnectivityManager"],
        "VIBRATE": ["Vibrator"],
    }
    for permission in permissions:
        hints = usage.get(permission, [permission])
        if not any(hint in all_java for hint in hints):
            problems.append(f"permission declared but unused in code: {permission}")
    notes.append("declared permissions: " + (", ".join(permissions) if permissions else "none"))

    # 5) TTS / speech paths present and clearly separated
    has_tts = "TextToSpeech" in source
    has_speech = "SpeechRecognizer" in source
    notes.append(f"TTS path: {'yes' if has_tts else 'no'} • speech recognition path: {'yes' if has_speech else 'no'}")
    if not has_tts:
        problems.append("no TextToSpeech path found")
    if "onInit" in source and "LANG_NOT_SUPPORTED" not in source and "LANG_MISSING_DATA" not in source:
        problems.append("TTS initialisation does not handle a missing Spanish voice")
    if "speechRecognizer" in source.lower() and "RecognizerIntent" not in source:
        problems.append("speech path without a recognizer intent")

    # 6) persistence keys untouched
    for key in ("habla_progress", "done_lessons", "xp", "streak"):
        if key not in source:
            problems.append(f"expected preference key missing from code: {key}")

    report = ["# تقرير تدقيق ربط الشاشات (ثابت، بدون جهاز)", "",
              f"المصدر: `app/src/main/java/com/habla/espanol/MainActivity.java`", ""]
    report += [f"- {note}" for note in notes]
    report += ["", "## النتيجة", ""]
    if problems:
        report += [f"- ❌ {problem}" for problem in problems]
    else:
        report += ["- ✅ لا توجد مشكلة ربط مكتشفة ثابتًا: كل شاشة مُستدعاة، وكل شاشة لها حالة رجوع، "
                   "وعدد المستمعين لا يقل عن عدد الأزرار، وكل إذن مستخدم في الكود."]
    text = "\n".join(report) + "\n"
    if "--write" in argv:
        target = argv[argv.index("--write") + 1]
        with open(target, "w", encoding="utf-8") as handle:
            handle.write(text)
    print(text)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
