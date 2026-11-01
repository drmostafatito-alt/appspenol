"""Resource and manifest validation for the Android app. Standard library only.

Checks:
- every XML file under app/src/main/res and AndroidManifest.xml is well-formed;
- every @drawable/@mipmap/@string/@color/@style reference resolves to a resource;
- every mipmap launcher name has an density-specific PNG or an adaptive XML;
- every font listed in assets exists and has a license file beside it;
- every drawable R.* reference in Java exists (android.R.* is ignored).
Exits non-zero on any ERROR.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
RES = MAIN / "res"
errors = []


def err(msg):
    errors.append(msg)


def parse_xml(path):
    try:
        return ET.parse(path).getroot()
    except ET.ParseError as e:
        err(f"malformed XML {path.relative_to(ROOT)}: {e}")
        return None


def resource_names():
    names = {"drawable": set(), "mipmap": set(), "string": set(), "color": set(), "style": set()}
    for folder in RES.iterdir() if RES.exists() else []:
        kind = folder.name.split("-")[0]
        if kind == "drawable":
            names["drawable"].update(p.stem for p in folder.iterdir() if p.suffix in (".xml", ".png"))
        elif kind == "mipmap":
            names["mipmap"].update(p.stem for p in folder.iterdir() if p.suffix in (".xml", ".png"))
        elif kind == "values":
            for xml in folder.glob("*.xml"):
                root = parse_xml(xml)
                if root is None:
                    continue
                for child in root:
                    name = child.get("name")
                    if not name:
                        continue
                    if child.tag == "string":
                        names["string"].add(name)
                    elif child.tag == "color":
                        names["color"].add(name)
                    elif child.tag == "style":
                        names["style"].add(name)
    return names


def check_references(names):
    ref = re.compile(r"@(drawable|mipmap|string|color|style)/([A-Za-z0-9_]+)")
    files = list(RES.rglob("*.xml")) + [MAIN / "AndroidManifest.xml"]
    for f in files:
        text = f.read_text(encoding="utf-8")
        for kind, name in ref.findall(text):
            if name not in names[kind]:
                err(f"{f.relative_to(ROOT)}: unresolved @{kind}/{name}")


def check_wellformed():
    for f in list(RES.rglob("*.xml")) + [MAIN / "AndroidManifest.xml"]:
        parse_xml(f)


def check_launcher_icons():
    for name in ("ic_launcher", "ic_launcher_round"):
        if not (RES / "mipmap-anydpi-v26" / f"{name}.xml").exists():
            err(f"missing adaptive icon mipmap-anydpi-v26/{name}.xml")
        for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
            if not (RES / f"mipmap-{density}" / f"{name}.png").exists():
                err(f"missing mipmap-{density}/{name}.png")


def check_fonts():
    fonts = MAIN / "assets/fonts"
    for f in ("cairo-regular.ttf", "cairo-bold.ttf", "nunito-regular.ttf", "nunito-bold.ttf"):
        if not (fonts / f).exists():
            err(f"missing font assets/fonts/{f}")
    for lic in ("Cairo-OFL.txt", "Nunito-OFL.txt"):
        if not (MAIN / "assets" / lic).exists():
            err(f"missing font license assets/{lic}")


def check_java_refs(names):
    java = list(MAIN.rglob("*.java"))
    pat = re.compile(r"(?<!android\.)\bR\.(drawable|mipmap|string|color|style)\.([A-Za-z0-9_]+)")
    for f in java:
        for kind, name in pat.findall(f.read_text(encoding="utf-8")):
            if name not in names[kind]:
                err(f"{f.relative_to(ROOT)}: R.{kind}.{name} not found")


def check_manifest():
    root = parse_xml(MAIN / "AndroidManifest.xml")
    if root is None:
        return
    ns = "{http://schemas.android.com/apk/res/android}"
    app = root.find("application")
    if app is None:
        err("manifest has no <application>")
        return
    for act in app.iter("activity"):
        name = act.get(ns + "name", "")
        if name.startswith(".") and not (MAIN / "java/com/habla/espanol" / f"{name[1:]}.java").exists():
            err(f"activity {name} has no source file")
    for rcv in app.iter("receiver"):
        name = rcv.get(ns + "name", "")
        if name.startswith(".") and not (MAIN / "java/com/habla/espanol" / f"{name[1:]}.java").exists():
            err(f"receiver {name} has no source file")


def main():
    check_wellformed()
    names = resource_names()
    check_references(names)
    check_launcher_icons()
    check_fonts()
    check_java_refs(names)
    check_manifest()
    for e in errors:
        print("ERROR:", e)
    counts = {k: len(v) for k, v in names.items()}
    print(f"Resources: {counts}. Errors: {len(errors)}.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
