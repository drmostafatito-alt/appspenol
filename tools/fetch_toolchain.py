#!/usr/bin/env python3
"""Rebuild the local Android/JVM toolchain into `.tools/` (git-ignored, not shipped).

This sandbox has no Gradle and no system JDK, so the build uses:

  * a Temurin JRE from the `jdk4py` wheel on PyPI          → `.tools/jdkpkg/jdk4py/java-runtime`
  * `android.jar` (API 36) from the Sable/android-platforms → `.tools/platforms/android-36`
  * aapt2 from the npm package `aaptjs3`                    → `.tools/aapt2pkg`
  * ECJ + D8 + apksigner from the npm package `@drxiaozhi/minapk` → `.tools/minapk`
  * local signing keystores generated with keytool          → `.tools/signing`

Usage: python3 tools/fetch_toolchain.py [--force]
Safe to re-run: every step is skipped when its output already exists.
"""
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, ".tools")
JRE_HOME = os.path.join(TOOLS, "jdkpkg", "jdk4py", "java-runtime")
JAVA = os.path.join(JRE_HOME, "bin", "java")
KEYTOOL = os.path.join(JRE_HOME, "bin", "keytool")
NPM_REGISTRY = "https://registry.npmjs.org"


def log(message):
    print(f"[toolchain] {message}")


def run(cmd, cwd=None):
    log(" ".join(cmd))
    subprocess.run(cmd, cwd=cwd, check=True)


def have(path):
    return os.path.exists(path)


def pip_jre(force=False):
    if have(JAVA) and not force:
        log("JRE already present")
        return
    target = os.path.join(TOOLS, "jdkpkg")
    os.makedirs(target, exist_ok=True)
    run([sys.executable, "-m", "pip", "install", "--quiet", "--target", target, "jdk4py"])
    if not have(JAVA):
        sys.exit("jdk4py installed but java binary not found")


def android_jar(force=False):
    target = os.path.join(TOOLS, "platforms", "android-36", "android.jar")
    if have(target) and not force:
        log("android.jar already present")
        return
    os.makedirs(os.path.dirname(target), exist_ok=True)
    clone_dir = os.path.join(TOOLS, "android-platforms")
    if not have(os.path.join(clone_dir, ".git")):
        run(["git", "clone", "--filter=blob:none", "--no-checkout", "--depth", "1",
             "https://github.com/Sable/android-platforms.git", clone_dir])
        run(["git", "sparse-checkout", "set", "android-36"], cwd=clone_dir)
        run(["git", "checkout"], cwd=clone_dir)
    source = os.path.join(clone_dir, "android-36", "android.jar")
    if not have(source):
        generated = find_file(os.path.join(clone_dir, "android-36"), "android.jar")
        source = generated or os.path.join(clone_dir, "android.jar")
    if not have(source):
        sys.exit("android.jar missing after clone")
    shutil.copy2(source, target)
    strip_java_packages(target, os.path.join(os.path.dirname(target), "android-api.jar"))



# android.jar also ships java.*/javax.* stubs. On a JDK 9+ module system those clash with
# java.base ("accessible from more than one module"), so ECJ gets a stripped copy.
DROPPED_PREFIXES = ("java/", "javax/", "jdk/", "sun/", "com/sun/")


def strip_java_packages(source, destination):
    with zipfile.ZipFile(source) as source_zip, zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as out:
        for item in source_zip.infolist():
            if item.filename.startswith(DROPPED_PREFIXES):
                continue
            out.writestr(item, source_zip.read(item.filename))
    log(f"android-api.jar: {os.path.getsize(destination)} bytes")

def npm_pack(name, destination):
    """Download and unpack an npm tarball through the registry API (npm binary not required)."""
    os.makedirs(destination, exist_ok=True)
    url = f"{NPM_REGISTRY}/{name.replace('/', '%2f')}"
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "arena-agent"}), timeout=120) as response:
        meta = __import__("json").load(response)
    tarball = meta["versions"][meta["dist-tags"]["latest"]]["dist"]["tarball"]
    with tempfile.TemporaryDirectory() as tmp:
        archive = os.path.join(tmp, "package.tgz")
        urllib.request.urlretrieve(tarball, archive)
        with tarfile.open(archive) as tar:
            tar.extractall(destination)


def find_file(root, name):
    for base, _dirs, files in os.walk(root):
        if name in files:
            return os.path.join(base, name)
    return ""


def aapt2(force=False):
    expected = os.path.join(TOOLS, "aapt2pkg", "package", "bin", "x64", "linux", "aapt2")
    if have(expected) and not force:
        log("aapt2 already present")
        return
    npm_pack("aaptjs3", os.path.join(TOOLS, "aapt2pkg"))
    linux = os.path.join(TOOLS, "aapt2pkg", "package", "bin", "x64", "linux", "aapt2")
    found = linux if have(linux) else find_file(os.path.join(TOOLS, "aapt2pkg"), "aapt2")
    if not found:
        sys.exit("aapt2 binary not found inside aaptjs3")
    os.chmod(found, 0o755)
    log(f"aapt2: {found}")


def android_tools(force=False):
    tools_dir = os.path.join(TOOLS, "minapk", "package", "tools")
    needed = ["ecj-3.45.0.jar", "d8.jar", "apksigner.jar"]
    if all(have(os.path.join(tools_dir, name)) for name in needed) and not force:
        log("ECJ/D8/apksigner already present")
        return
    npm_pack("@drxiaozhi/minapk", os.path.join(TOOLS, "minapk"))
    for name in needed:
        if not have(os.path.join(tools_dir, name)):
            log(f"warning: {name} not found in @drxiaozhi/minapk")
    if not have(os.path.join(tools_dir, "ecj-3.45.0.jar")):
        npm_pack("@ctxo/lang-java-analyzer", os.path.join(TOOLS, "ecjpkg"))
        found = find_file(os.path.join(TOOLS, "ecjpkg"), "ecj.jar")
        if found:
            shutil.copy2(found, os.path.join(tools_dir, "ecj-3.45.0.jar"))


def keystores(force=False):
    signing = os.path.join(TOOLS, "signing")
    os.makedirs(signing, exist_ok=True)
    specs = [
        ("habla-debug.jks", "CN=Spanish 1000 Debug, O=Personal use, L=Cairo, C=EG"),
        ("habla-release.jks", "CN=Spanish 1000, O=Personal use, L=Cairo, C=EG"),
    ]
    for name, dname in specs:
        path = os.path.join(signing, name)
        if have(path) and not force:
            continue
        run([KEYTOOL, "-genkeypair", "-keystore", path, "-storetype", "JKS", "-alias", "habla",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-storepass", "habla-local-only", "-keypass", "habla-local-only", "-dname", dname])


def main(argv):
    force = "--force" in argv
    os.makedirs(TOOLS, exist_ok=True)
    for step in (pip_jre, android_jar, aapt2, android_tools, keystores):
        try:
            step(force)
        except Exception as error:  # keep going so the user sees every failing step
            log(f"FAILED {step.__name__}: {error}")
    ok = have(JAVA) and have(os.path.join(TOOLS, "platforms", "android-36", "android.jar"))
    log("ready" if ok else "incomplete — see failures above")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
