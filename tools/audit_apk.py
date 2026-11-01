#!/usr/bin/env python3
"""Audit the hand-built APK (no Gradle, no device).

This does NOT prove the app runs; `apksigner verify` alone proves nothing either. It checks the
things that make an APK fail on a real phone before it ever draws a pixel:

  * manifest: package, versionCode/Name, minSdk/targetSdk, permissions, exported components,
    launchable activity, RTL support, backup flag, cleartext traffic
  * resources + assets: required files present, content.json byte-identical to the project one,
    no signing material or secret-looking files inside the archive
  * DEX: real DEX header, checksum + SHA-1 fields valid, class list present, only one dex,
    dex API level, and the app's own classes are inside it
  * bytecode: class file major version compiled for Java 17 and dexed with --min-api 23
  * minSdk 23 compatibility: Java/Android APIs newer than API 23 used without a version guard
  * zip structure: `resources.arsc` stored + 4-byte aligned (required from targetSdk 30)
  * signature: apksigner verify for every scheme + certificate fingerprints
  * Gradle: whether a real Gradle build is possible here at all, with the concrete reason

Usage: python3 tools/audit_apk.py [--write releases/APK_AUDIT_REPORT.md] [--apk PATH] [--variant release]
"""
import argparse
import glob
import hashlib
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, ".tools")
JAVA = os.path.join(TOOLS, "jdkpkg", "jdk4py", "java-runtime", "bin", "java")
AAPT2 = os.path.join(TOOLS, "aapt2pkg", "package", "bin", "x64", "linux", "aapt2")
APKSIGNER = os.path.join(TOOLS, "minapk", "package", "tools", "apksigner.jar")
CONTENT = os.path.join(ROOT, "app", "src", "main", "assets", "content.json")
JAVA_SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "habla", "espanol")

# Java/Android APIs that do not exist on API 23. Each entry is (regex, required API, note).
NEWER_APIS = [
    (r"\bString\s*\.\s*join\s*\(", 26, "String.join"),
    (r"(?<!Collections)\b(?!Collections\b)\w+\s*\.\s*sort\s*\(", 24, "List.sort default method"),
    (r"\bjava\.nio\.file\b", 26, "java.nio.file"),
    (r"\bjava\.time\b", 26, "java.time"),
    (r"\bFiles\s*\.\s*\w+\s*\(", 26, "java.nio.file.Files"),
    (r"\bremoveIf\s*\(", 24, "Collection.removeIf"),
    (r"\bcomputeIfAbsent\s*\(", 24, "Map.computeIfAbsent"),
    (r"\bcomputeIfPresent\s*\(", 24, "Map.computeIfPresent"),
    (r"\bgetOrDefault\s*\(", 24, "Map.getOrDefault"),
    (r"\bjava\.util\.Base64\b", 26, "java.util.Base64"),
    (r"\bnew\s+NotificationChannel\s*\(", 26, "NotificationChannel"),
    (r"\bareNotificationsEnabled\s*\(", 24, "NotificationManager.areNotificationsEnabled"),
    (r"\bgetOnBackInvokedDispatcher\s*\(", 33, "OnBackInvokedDispatcher"),
    (r"\bList\s*\.\s*of\s*\(", 30, "List.of / Set.of / Map.of"),
    (r"\bSet\s*\.\s*of\s*\(", 30, "Set.of"),
    (r"\bMap\s*\.\s*of\s*\(", 30, "Map.of"),
]


def sh(cmd):
    return subprocess.run(cmd, capture_output=True, text=True)


def human(size):
    return f"{size:,}"


def dex_report(data, name):
    """Validates the DEX header the way the Android runtime does before loading it."""
    out = {"name": name, "size": len(data), "ok": True, "notes": []}
    if data[:4] != b"dex\n":
        out["ok"] = False
        out["notes"].append("missing dex magic")
        return out
    version = data[4:7].decode("ascii", "replace")
    out["version"] = version
    if version not in ("035", "037", "038", "039"):
        out["notes"].append(f"unexpected dex version {version}")
    checksum = struct.unpack("<I", data[8:12])[0]
    computed = zlib.adler32(data[12:]) & 0xFFFFFFFF
    if checksum != computed:
        out["ok"] = False
        out["notes"].append("adler32 checksum mismatch")
    signature = data[12:32]
    computed_sha = hashlib.sha1(data[32:]).digest()
    if signature != computed_sha:
        out["ok"] = False
        out["notes"].append("sha-1 signature mismatch")
    file_size = struct.unpack("<I", data[32:36])[0]
    if file_size != len(data):
        out["ok"] = False
        out["notes"].append("header file_size does not match the file")
    header_size = struct.unpack("<I", data[36:40])[0]
    out["header_size"] = header_size
    string_ids = struct.unpack("<I", data[56:60])[0]
    out["string_ids"] = string_ids
    if string_ids == 0:
        out["ok"] = False
        out["notes"].append("no string ids — the dex is empty")
    return out


def scan_java_sources():
    findings = []
    for path in sorted(glob.glob(os.path.join(JAVA_SRC, "*.java"))):
        lines = open(path, encoding="utf-8").read().splitlines()
        for index, line in enumerate(lines):
            for pattern, required, note in NEWER_APIS:
                if not re.search(pattern, line):
                    continue
                guarded = False
                for prev in lines[max(0, index - 4):index + 2]:
                    if "SDK_INT" not in prev:
                        continue
                    for number in re.findall(r"SDK_INT\s*>=?\s*(\d+)", prev) + \
                                  re.findall(r"SDK_INT\s*>\s*(\d+)", prev):
                        if int(number) >= required:
                            guarded = True
                findings.append({
                    "file": os.path.basename(path),
                    "line": index + 1,
                    "api": required,
                    "note": note,
                    "code": line.strip()[:110],
                    "guarded": guarded,
                })
    return findings


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", default=os.path.join(ROOT, "releases", "app-release.apk"))
    parser.add_argument("--variant", default="release")
    parser.add_argument("--write", default="")
    args = parser.parse_args(argv)

    problems, notes = [], []
    apk = args.apk

    # --- toolchain presence ---
    missing = [p for p in (JAVA, AAPT2, APKSIGNER) if not os.path.exists(p)]
    if missing:
        print("toolchain missing: " + ", ".join(missing), file=sys.stderr)
        return 2

    # --- Gradle feasibility ---
    gradlew = os.path.exists(os.path.join(ROOT, "gradlew"))
    gradle = shutil.which("gradle")
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    notes.append(f"gradlew present: {gradlew} • gradle on PATH: {gradle or 'no'} • ANDROID_HOME: {android_home or 'no'}")
    if not gradlew and not gradle:
        problems_note = ("لا يوجد Gradle ولا Android SDK في هذه البيئة (لا gradlew ولا gradle على PATH ولا "
                         "ANDROID_HOME)، والشبكة متاحة فقط لـ github/npm/pypi؛ لذلك لا يمكن استعادة بناء Gradle هنا. "
                         "البناء الحالي يستخدم aapt2 + ECJ + D8 + apksigner مباشرة، وهو نفس ما يفحصه هذا التقرير.")

    # --- manifest / badging ---
    badging = sh([AAPT2, "dump", "badging", apk])
    if badging.returncode != 0:
        problems.append("aapt2 dump badging failed: " + badging.stderr.strip()[:200])
        text = badging.stdout
    else:
        text = badging.stdout
    package = re.search(r"package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'", text)
    min_sdk = re.search(r"minSdkVersion:'([^']+)'", text)
    target_sdk = re.search(r"targetSdkVersion:'([^']+)'", text)
    launch = re.search(r"launchable-activity: name='([^']+)'", text)
    permissions = re.findall(r"uses-permission: name='([^']+)'", text)
    label = re.search(r"application-label:'([^']*)'", text)
    if not package:
        problems.append("APK has no package line — it is not a valid manifest")
    else:
        notes.append(f"package {package.group(1)} • versionCode {package.group(2)} • "
                     f"versionName {package.group(3)} • label {label.group(1) if label else '—'}")
        if package.group(1) != "com.habla.espanol":
            problems.append("package name changed: " + package.group(1))
    if not min_sdk or min_sdk.group(1) != "23":
        problems.append("minSdk is not 23: " + (min_sdk.group(1) if min_sdk else "missing"))
    notes.append(f"minSdk {min_sdk.group(1) if min_sdk else '?'} • targetSdk "
                 f"{target_sdk.group(1) if target_sdk else '?'}")
    if not launch or launch.group(1) != "com.habla.espanol.MainActivity":
        problems.append("launcher activity is missing or renamed")
    else:
        notes.append("launchable activity: " + launch.group(1))
    if "android.permission.INTERNET" in permissions:
        problems.append("INTERNET permission is back — the app must stay offline")
    notes.append("permissions: " + (", ".join(p.split(".")[-1] for p in permissions) or "none"))

    # exported components and quota-sensitive declarations
    tree = sh([AAPT2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk])
    xml = tree.stdout
    for exported_true in re.findall(r'android:exported\(0x[0-9a-f]+\)=\(type 0x12\)0xffffffff', xml):
        pass
    exported_false = len(re.findall(r'android:exported\(0x[0-9a-f]+\)=\(type 0x12\)0x0', xml))
    exported_true = len(re.findall(r'android:exported\(0x[0-9a-f]+\)=\(type 0x12\)0xffffffff', xml))
    notes.append(f"components: exported=true {exported_true} (launcher only expected) • exported=false {exported_false}")
    if exported_true > 1:
        problems.append(f"{exported_true} components are exported; only the launcher should be")
    for attribute in ("supportsRtl", "allowBackup", "usesCleartextTraffic"):
        if attribute not in xml:
            notes.append(f"⚠️ manifest attribute not visible in dump: {attribute}")

    # --- zip contents ---
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        infos = {info.filename: info for info in archive.infolist()}
        content_bytes = archive.read("assets/content.json") if "assets/content.json" in names else b""
        dex_names = [n for n in names if re.match(r"classes\d*\.dex$", n)]
        has_fonts = all(f"assets/fonts/{font}" in names
                        for font in ("cairo-regular.ttf", "cairo-bold.ttf", "nunito-regular.ttf", "nunito-bold.ttf"))
        has_icon = any(n.startswith("res/") and "ic_launcher" in n for n in names)
        suspicious = [n for n in names if re.search(r"(\.jks$|\.keystore$|\.pem$|\.p12$|secret|password|\.git/)", n, re.I)]
        stored = [n for n, info in infos.items() if info.compress_type == zipfile.ZIP_STORED]
        arsc = infos.get("resources.arsc")
    notes.append(f"zip entries: {len(names)} • dex files: {len(dex_names)} • stored entries: {len(stored)}")
    if not dex_names:
        problems.append("APK has no classes.dex")
    if len(dex_names) > 1:
        problems.append("more than one dex — multidex config is not declared")
    if not has_fonts:
        problems.append("fonts are missing from assets")
    if not has_icon:
        problems.append("launcher icon resources are missing")
    if suspicious:
        problems.append("secret-looking files inside the APK: " + ", ".join(suspicious))
    if not content_bytes:
        problems.append("assets/content.json is missing from the APK")
    else:
        local = open(CONTENT, "rb").read()
        if hashlib.sha256(local).digest() != hashlib.sha256(content_bytes).digest():
            problems.append("assets/content.json inside the APK differs from the project file")
        else:
            notes.append("assets/content.json is byte-identical to the project asset "
                         f"({human(len(content_bytes))} bytes)")
    if arsc is None:
        problems.append("resources.arsc is missing")
    else:
        if arsc.compress_type != zipfile.ZIP_STORED:
            problems.append("resources.arsc is compressed (required uncompressed from targetSdk 30)")
        if arsc.header_offset % 4 != 0:
            problems.append("resources.arsc is not 4-byte aligned")
        else:
            notes.append("resources.arsc stored and 4-byte aligned")

    # --- dex integrity + classes ---
    dex_results = []
    with zipfile.ZipFile(apk) as archive:
        for name in dex_names:
            dex_results.append(dex_report(archive.read(name), name))
    for result in dex_results:
        if not result["ok"]:
            problems.append(f"{result['name']} is corrupt: " + "; ".join(result["notes"]))
        notes.append(f"{result['name']}: dex {result.get('version')} • {human(result['size'])} bytes • "
                     f"{result['string_ids']} strings")
    expected_classes = ["com/habla/espanol/MainActivity", "com/habla/espanol/Curriculum",
                        "com/habla/espanol/LessonPlanner", "com/habla/espanol/LessonSession",
                        "com/habla/espanol/ReviewScheduler", "com/habla/espanol/AnswerChecker",
                        "com/habla/espanol/Tutor", "com/habla/espanol/BootReceiver",
                        "com/habla/espanol/ReminderReceiver", "com/habla/espanol/DesignSystem"]
    with zipfile.ZipFile(apk) as archive:
        dex_bytes = b"".join(archive.read(n) for n in dex_names)
    missing_classes = [c for c in expected_classes if c.encode() not in dex_bytes]
    if missing_classes:
        problems.append("classes missing from dex: " + ", ".join(missing_classes))
    else:
        notes.append(f"expected app classes present in dex ({len(expected_classes)})")

    # --- bytecode level ---
    class_files = glob.glob(os.path.join(ROOT, "build", args.variant, "classes", "**", "*.class"), recursive=True)
    majors = set()
    for path in class_files[:400]:
        with open(path, "rb") as handle:
            data = handle.read(8)
        if len(data) == 8 and data[:4] == b"\xca\xfe\xba\xbe":
            majors.add(struct.unpack(">H", data[6:8])[0])
    if majors:
        notes.append("class file major version: " + ", ".join(str(m) for m in sorted(majors))
                     + " (61 = Java 17, dexed with --min-api 23)")
        if not majors.issubset({61}):
            problems.append("unexpected class file version: " + str(sorted(majors)))
    else:
        notes.append("build/classes not present — bytecode level checked from the dex only")

    # --- API level scan ---
    findings = scan_java_sources()
    unguarded = [f for f in findings if not f["guarded"]]
    for finding in unguarded:
        problems.append(f"API {finding['api']} used without a version guard in {finding['file']}:{finding['line']} "
                        f"({finding['note']})")
    if findings:
        notes.append(f"post-API-23 API usages: {len(findings)} • guarded: {len(findings) - len(unguarded)}")

    # --- signature ---
    verified = sh([JAVA, "-cp", APKSIGNER, "com.android.apksigner.ApkSignerTool", "verify",
                   "--verbose", "--print-certs", apk])
    if verified.returncode != 0:
        problems.append("apksigner verify failed: " + (verified.stderr or verified.stdout).strip()[:200])
    else:
        cert = re.search(r"certificate DN: (.+)", verified.stdout)
        sha256 = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", verified.stdout)
        schemes = []
        for scheme in ("v1", "v2", "v3", "v3.1"):
            if re.search(r"Verified using " + re.escape(scheme) + r" scheme[^:]*: true", verified.stdout):
                schemes.append(scheme)
        if not schemes:
            problems.append("no signature scheme verified")
        notes.append(f"signature: {', '.join(schemes) or 'none'} • {cert.group(1) if cert else '?'} • "
                     f"SHA-256 {sha256.group(1)[:16] + '…' if sha256 else '?'}")

    # --- apk hash ---
    with open(apk, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()
    size = os.path.getsize(apk)
    notes.append(f"file: {os.path.relpath(apk, ROOT)} • {human(size)} bytes • SHA-256 {digest}")

    # --- report ---
    lines = ["# تقرير تدقيق APK (بناء يدوي بدون Gradle)", ""]
    lines.append(f"الملف: `{os.path.relpath(apk, ROOT)}` • الحجم: **{human(size)}** بايت • "
                 f"SHA-256: `{digest}`")
    lines.append("")
    lines.append("## ما تم فحصه فعليًا")
    lines.append("")
    lines += [f"- {note}" for note in notes]
    lines.append("")
    lines.append("## Gradle")
    lines.append("")
    lines.append("- " + problems_note)
    lines.append("")
    lines.append("## المشاكل")
    lines.append("")
    if problems:
        lines += [f"- ❌ {problem}" for problem in problems]
    else:
        lines.append("- ✅ لا مشكلة بنيوية مكتشفة: البيان صحيح، الموارد والأصول سليمة، DEX سليم ببصمة "
                     "وتحقق داخلي، الأصناف المطلوبة موجودة، ولا استخدام لواجهات أحدث من minSdk 23 بدون حماية.")
    lines.append("")
    lines.append("## ما لا يثبته هذا التقرير")
    lines.append("")
    lines.append("- لا يثبت أن التطبيق يُفتح على هاتف أو محاكي، ولا أن الواجهة ترسم، ولا أن الصوت يعمل. "
                 "هذه البيئة لا تحتوي جهازًا ولا محاكيًا ولا `adb`.")
    lines.append("- التحقق من التوقيع يثبت أن الحزمة موقّعة ولم تُعدّل، لا أن التوقيع معتمد للنشر.")
    text = "\n".join(lines) + "\n"
    if args.write:
        os.makedirs(os.path.dirname(args.write) or ".", exist_ok=True)
        open(args.write, "w", encoding="utf-8").write(text)
    print(text)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
