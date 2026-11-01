#!/usr/bin/env python3
"""Build Spanish 1000 (Habla) APK without Gradle.

The sandbox used for this project has no Android SDK / Gradle distribution,
but an APK can be produced with the individual AOSP tools.  `tools/fetch_toolchain.py`
downloads a JDK runtime (jdk4py), android.jar (Sable/android-platforms),
aapt2 (aaptjs3) and d8/apksigner/ecj (minapk) into `.tools/`.

Pipeline:
    aapt2 compile  res/  ->  compiled resources
    aapt2 link     manifest + android.jar + assets -> unsigned APK + R.java
    ecj (javac)    java sources + R.java -> .class files
    d8             .class files -> classes.dex
    zip            unsigned APK + classes.dex
    apksigner      v1 + v2 + v3 signature
"""
import argparse
import glob
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, ".tools")
APP = os.path.join(ROOT, "app")
BUILD = os.path.join(ROOT, "build")
JAVA = os.path.join(TOOLS, "jdkpkg", "jdk4py", "java-runtime", "bin", "java")
KEYTOOL = os.path.join(TOOLS, "jdkpkg", "jdk4py", "java-runtime", "bin", "keytool")
AAPT2 = os.path.join(TOOLS, "aapt2pkg", "package", "bin", "x64", "linux", "aapt2")
ANDROID_JAR = os.path.join(TOOLS, "platforms", "android-36", "android.jar")
# android.jar ships stubs for java.*/javax.* which clash with java.base when used as a
# plain classpath entry; compile against a copy that only keeps the android/organic APIs.
ANDROID_API_JAR = os.path.join(TOOLS, "platforms", "android-36", "android-api.jar")
ECJ = os.path.join(TOOLS, "minapk", "package", "tools", "ecj-3.45.0.jar")
D8 = os.path.join(TOOLS, "minapk", "package", "tools", "d8.jar")
APKSIGNER = os.path.join(TOOLS, "minapk", "package", "tools", "apksigner.jar")
SIGNING = os.path.join(TOOLS, "signing")

VERSION_CODE = "12"
VERSION_NAME = "8.2"
MIN_SDK = "23"
TARGET_SDK = "36"

STORE_PASS = "habla-local-only"
KEY_ALIAS = "habla"


def run(cmd, **kw):
    print("+", " ".join(os.path.basename(c) if i == 0 else c for i, c in enumerate(cmd)))
    proc = subprocess.run(cmd, **kw)
    if proc.returncode != 0:
        sys.exit(f"command failed ({proc.returncode}): {' '.join(cmd)}")
    return proc


def check_toolchain():
    missing = [p for p in (JAVA, AAPT2, ANDROID_JAR, ANDROID_API_JAR, ECJ, D8, APKSIGNER) if not os.path.exists(p)]
    if missing:
        sys.exit(
            "toolchain missing:\n  "
            + "\n  ".join(missing)
            + "\nRun: python3 tools/fetch_toolchain.py"
        )


def keystore(variant):
    os.makedirs(SIGNING, exist_ok=True)
    path = os.path.join(SIGNING, "habla-release.jks" if variant == "release" else "habla-debug.jks")
    if not os.path.exists(path):
        run([
            KEYTOOL, "-genkeypair", "-keystore", path, "-storetype", "JKS",
            "-alias", KEY_ALIAS, "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000", "-storepass", STORE_PASS, "-keypass", STORE_PASS,
            "-dname", "CN=Spanish 1000, O=Personal use, L=Cairo, C=EG",
        ])
    return path


def compile_resources(out):
    res_zip = os.path.join(out, "resources.zip")
    run([AAPT2, "compile", "--dir", os.path.join(APP, "src", "main", "res"), "-o", res_zip])
    return res_zip


def link(res_zip, out):
    linked = os.path.join(out, "linked.apk")
    gen = os.path.join(out, "gen")
    os.makedirs(gen, exist_ok=True)
    run([
        AAPT2, "link", "-o", linked,
        "-I", ANDROID_JAR,
        "--manifest", os.path.join(APP, "src", "main", "AndroidManifest.xml"),
        "-R", res_zip,
        "-A", os.path.join(APP, "src", "main", "assets"),
        "--java", gen,
        "--min-sdk-version", MIN_SDK,
        "--target-sdk-version", TARGET_SDK,
        "--version-code", VERSION_CODE,
        "--version-name", VERSION_NAME,
        "--auto-add-overlay",
    ])
    return linked, gen


def compile_java(gen, out):
    classes = os.path.join(out, "classes")
    os.makedirs(classes, exist_ok=True)
    sources = []
    for base in (os.path.join(APP, "src", "main", "java"), gen):
        for dirpath, _dirnames, filenames in os.walk(base):
            sources += [os.path.join(dirpath, f) for f in filenames if f.endswith(".java")]
    argfile = os.path.join(out, "sources.txt")
    with open(argfile, "w", encoding="utf-8") as handle:
        handle.write("\n".join(sources))
    run([
        JAVA, "-Xmx1g", "-cp", ECJ,
        "org.eclipse.jdt.internal.compiler.batch.Main",
        "-17", "-nowarn", "-proc:none", "-encoding", "UTF-8",
        "-cp", ANDROID_API_JAR, "-d", classes, "@" + argfile,
    ])
    return classes


def dex(classes, out):
    dexdir = os.path.join(out, "dex")
    os.makedirs(dexdir, exist_ok=True)
    class_files = glob.glob(os.path.join(classes, "**", "*.class"), recursive=True)
    if not class_files:
        sys.exit("no class files produced")
    argfile = os.path.join(out, "classes.txt")
    with open(argfile, "w", encoding="utf-8") as handle:
        handle.write("\n".join(class_files))
    run([
        JAVA, "-Xmx1g", "-cp", D8, "com.android.tools.r8.D8",
        "--release", "--min-api", MIN_SDK, "--lib", ANDROID_JAR,
        "--output", dexdir, "@" + argfile,
    ])
    return dexdir


def package_apk(linked, dexdir, out):
    unsigned = os.path.join(out, "app-unsigned.apk")
    with zipfile.ZipFile(linked) as src, zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            info = zipfile.ZipInfo(item.filename, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = item.compress_type
            dst.writestr(info, data)
        for name in sorted(os.listdir(dexdir)):
            if name.endswith(".dex"):
                info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(os.path.join(dexdir, name), "rb") as handle:
                    dst.writestr(info, handle.read())
    return unsigned


def zipalign(src_apk, dst_apk):
    """Store resources.arsc uncompressed/4-byte aligned, page-align .so, pad stored entries."""
    with zipfile.ZipFile(src_apk) as src, open(dst_apk, "wb") as handle:
        central = []
        for item in src.infolist():
            data = src.read(item.filename)
            name = item.filename
            align = 4
            compress = zipfile.ZIP_DEFLATED
            if name == "resources.arsc" or name.endswith(".dex") or name.endswith(".so"):
                compress = zipfile.ZIP_STORED
            if name.endswith(".so"):
                align = 4096
            header = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            header.compress_type = compress
            header.external_attr = 0o600 << 16
            chunk = data if compress == zipfile.ZIP_STORED else None
            if chunk is None:
                import zlib
                compressor = zlib.compressobj(9, zlib.DEFLATED, -15)
                chunk = compressor.compress(data) + compressor.flush()
                header.compress_size = len(chunk)
                header.file_size = len(data)
                header.CRC = zlib.crc32(data) & 0xFFFFFFFF
                method_flag = 8
            else:
                header.compress_size = len(chunk)
                header.file_size = len(data)
                header.CRC = zlib.crc32(data) & 0xFFFFFFFF
                method_flag = 0
            offset = handle.tell()
            header.header_offset = offset
            name_bytes = name.encode("utf-8")
            padding = b""
            if align > 1:
                # zipalign semantics: the file data must start on an `align` boundary.
                # The local extra field is the padding; it must be 0 or at least 4 bytes,
                # otherwise the extra field itself would be malformed.
                base = offset + 30 + len(name_bytes)
                pad = (-base) % align
                if 0 < pad < 4:
                    pad += align
                padding = b"\0" * pad
            local = bytearray()
            local += (0x04034B50).to_bytes(4, "little")
            local += (20).to_bytes(2, "little")
            local += (0x0800).to_bytes(2, "little")
            local += method_flag.to_bytes(2, "little")
            local += (0).to_bytes(2, "little")
            local += (0x21).to_bytes(2, "little")
            local += header.CRC.to_bytes(4, "little")
            local += header.compress_size.to_bytes(4, "little")
            local += header.file_size.to_bytes(4, "little")
            local += len(name_bytes).to_bytes(2, "little")
            local += len(padding).to_bytes(2, "little")
            handle.write(bytes(local) + name_bytes + padding + chunk)
            header.extra = b""
            central.append((header, name_bytes, method_flag))
        cd_start = handle.tell()
        for header, name_bytes, method_flag in central:
            entry = bytearray()
            entry += (0x02014B50).to_bytes(4, "little")
            entry += (20).to_bytes(2, "little")
            entry += (20).to_bytes(2, "little")
            entry += (0x0800).to_bytes(2, "little")
            entry += method_flag.to_bytes(2, "little")
            entry += (0).to_bytes(2, "little")
            entry += (0x21).to_bytes(2, "little")
            entry += header.CRC.to_bytes(4, "little")
            entry += header.compress_size.to_bytes(4, "little")
            entry += header.file_size.to_bytes(4, "little")
            entry += len(name_bytes).to_bytes(2, "little")
            entry += (0).to_bytes(2, "little")
            entry += (0).to_bytes(2, "little")
            entry += (0).to_bytes(2, "little")
            entry += (0).to_bytes(2, "little")
            entry += (0o600 << 16).to_bytes(4, "little")
            entry += header.header_offset.to_bytes(4, "little")
            entry += name_bytes
            handle.write(bytes(entry))
        cd_size = handle.tell() - cd_start
        eocd = bytearray()
        eocd += (0x06054B50).to_bytes(4, "little")
        eocd += (0).to_bytes(2, "little")
        eocd += (0).to_bytes(2, "little")
        eocd += len(central).to_bytes(2, "little")
        eocd += len(central).to_bytes(2, "little")
        eocd += cd_size.to_bytes(4, "little")
        eocd += cd_start.to_bytes(4, "little")
        eocd += (0).to_bytes(2, "little")
        handle.write(bytes(eocd))


def sign(apk, variant, out):
    ks = keystore(variant)
    signed = os.path.join(out, "app-%s.apk" % variant)
    run([
        JAVA, "-Xmx1g", "-cp", APKSIGNER, "com.android.apksigner.ApkSignerTool", "sign",
        "--ks", ks, "--ks-pass", "pass:" + STORE_PASS, "--key-pass", "pass:" + STORE_PASS,
        "--ks-key-alias", KEY_ALIAS, "--min-sdk-version", MIN_SDK,
        "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
        "--out", signed, apk,
    ])
    return signed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--variant", choices=("debug", "release"), default="debug")
    parser.add_argument("--no-sign", action="store_true")
    args = parser.parse_args()
    check_toolchain()
    out = os.path.join(BUILD, args.variant)
    if os.path.isdir(out):
        shutil.rmtree(out)
    os.makedirs(out, exist_ok=True)
    res_zip = compile_resources(out)
    linked, gen = link(res_zip, out)
    classes = compile_java(gen, out)
    dexdir = dex(classes, out)
    unsigned = package_apk(linked, dexdir, out)
    aligned = os.path.join(out, "app-aligned.apk")
    zipalign(unsigned, aligned)
    if args.no_sign:
        print("unsigned APK:", aligned)
        return
    signed = sign(aligned, args.variant, out)
    dest = os.path.join(ROOT, "releases", "app-%s.apk" % args.variant)
    shutil.copy2(signed, dest)
    print("built:", dest, os.path.getsize(dest), "bytes")


if __name__ == "__main__":
    main()
