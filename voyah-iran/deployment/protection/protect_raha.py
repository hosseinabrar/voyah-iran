"""Experimental local-only Raha protection. Python 3.10+ and JDK 17+.

Never builds the app, signs it, replaces the release APK, or updates the installer.
The reference APK and its matching R8 mapping are mandatory inputs.
"""
import argparse
import hashlib
import io
import json
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION = "v2.21.0"
ARCHIVE_SHA = "11d07c39ab5061e361f8df1b0665defdb29cab8b5314781a1bdab383f48517f3"
JAR_SHA = "8b5a93d3452d90ab06bce4e15e5bc701de63885acb7e3b68375b576a1b246a92"
SOURCE_SHA = "a387e669ca09b900ed4b43779e8d2b0cea662a06b2fc566366a9b402786caa30"
REPO = "https://github.com/luoyesiqiu/dpt-shell"
CORE = re.compile(r"com\.abrar\.raha\.(?:RahaCommandRecognizer|RahaNavigationRecognizer|RahaPhase\w+Recognizer|Raha\w*Vocabulary)(?:\$.*)?$")
BRIDGE = "Lcom/abrar/voyahiran/core/bridge/BridgeServer;"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def fetch(url, expected, path):
    if not path.exists():
        print("Download:", url, flush=True)
        req = urllib.request.Request(url, headers={"User-Agent": "VoyahIran-DPT-setup"})
        with urllib.request.urlopen(req, timeout=120) as response:
            data = response.read()
        require(digest(data) == expected, "Download hash mismatch: " + path.name)
        path.write_bytes(data)
    require(digest(path.read_bytes()) == expected, "Cached file hash mismatch: " + str(path))
    return path.read_bytes()


def run(args, cwd, logfile, timeout=600):
    # No signing passwords ever enter this process or its log.
    with logfile.open("ab") as log:
        log.write((json.dumps([str(a) for a in args]) + "\n").encode())
        result = subprocess.run([str(a) for a in args], cwd=cwd, stdout=log,
                                stderr=subprocess.STDOUT, timeout=timeout)
    require(result.returncode == 0, "Tool failed; see " + str(logfile))


def prepare_tool(java, cache, log):
    cache.mkdir(parents=True, exist_ok=True)
    archive = fetch(f"{REPO}/releases/download/{VERSION}/dpt-shell-{VERSION}.zip",
                    ARCHIVE_SHA, cache / "upstream.zip")
    # Fresh extraction avoids trusting previously modified local shell binaries.
    directory = cache / ("tool-" + uuid.uuid4().hex)
    directory.mkdir()
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            dest = (directory / name).resolve()
            require(dest.is_relative_to(directory.resolve()), "Invalid archive path")
        z.extractall(directory)
    tool = directory / "executable"
    original = tool / "dpt.jar"
    require(digest(original.read_bytes()) == JAR_SHA, "Unexpected upstream JAR")
    source = fetch(f"https://raw.githubusercontent.com/luoyesiqiu/dpt-shell/{VERSION}/"
                   "dpt/src/main/java/com/luoye/dpt/dex/ReflectionClinitInjector.java",
                   SOURCE_SHA, cache / "ReflectionClinitInjector.upstream.java").decode("utf-8")
    old = "return SKIPPED_CLASS_TYPES.contains(classType);"
    require(source.count(old) == 1, "DPT source changed; review compatibility patch")
    source = source.replace(old, "return SKIPPED_CLASS_TYPES.contains(classType)\n"
                            "                || com.luoye.dpt.config.ProtectRules.getInstance().matchRules(classType);")
    src = directory / "ReflectionClinitInjector.java"
    src.write_text(source, encoding="utf-8")
    classes = directory / "patch-classes"
    classes.mkdir()
    javac = java.with_name("javac.exe" if sys.platform == "win32" else "javac")
    require(javac.exists(), "A full JDK is required, not only a Java runtime")
    run([javac, "-encoding", "UTF-8", "--release", "17", "-cp", original,
         "-d", classes, src], directory, log)
    patched = tool / "dpt-voyah.jar"
    replacements = {p.relative_to(classes).as_posix(): p.read_bytes()
                    for p in classes.rglob("*.class")}
    require("com/luoye/dpt/dex/ReflectionClinitInjector.class" in replacements, "Missing patch class")
    with zipfile.ZipFile(original) as zin, zipfile.ZipFile(patched, "w") as zout:
        for item in zin.infolist():
            if item.filename not in replacements:
                zout.writestr(item, zin.read(item.filename))
        for name, data in replacements.items():
            zout.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
    return patched


def read_dex(data):
    """Read class/method inventory for static verification, not a decompiler."""
    require(data.startswith(b"dex\n"), "Unsupported DEX format")
    u32 = lambda offset: struct.unpack_from("<I", data, offset)[0]

    def uleb(offset):
        value = shift = 0
        while True:
            part = data[offset]
            offset += 1
            value |= (part & 127) << shift
            if part < 128:
                return value, offset
            shift += 7
            require(shift < 35, "Invalid DEX integer")

    strings = []
    for i in range(u32(56)):
        off = u32(u32(60) + i * 4)
        _, off = uleb(off)
        strings.append(data[off:data.index(b"\0", off)].decode("utf-8", errors="replace"))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    method_names = [strings[u32(u32(92) + i * 8 + 4)] for i in range(u32(88))]
    classes = {}
    for i in range(u32(96)):
        off = u32(100) + i * 32
        name, cdata = types[u32(off)], u32(off + 24)
        methods = []
        if cdata:
            sizes = []
            for _ in range(4):
                size, cdata = uleb(cdata)
                sizes.append(size)
            for _ in range(sizes[0] + sizes[1]):
                _, cdata = uleb(cdata)
                _, cdata = uleb(cdata)
            for count in sizes[2:]:
                index = 0
                for _ in range(count):
                    delta, cdata = uleb(cdata)
                    index += delta
                    flags, cdata = uleb(cdata)
                    code, cdata = uleb(cdata)
                    methods.append((method_names[index], flags, u32(code + 12) if code else 0))
        classes[name] = sorted(methods)
    return classes, strings


def apk_inventory(path):
    classes, strings = {}, []
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                found, texts = read_dex(z.read(name))
                require(not classes.keys() & found.keys(), "Duplicate DEX class")
                classes.update(found)
                strings.extend(texts)
    return classes, strings


def synthetic_descriptors(mapping):
    """R8 lambda holders can be shared across unrelated classes/processes."""
    result = set()
    owner = None
    class_metadata = False
    for line in mapping.splitlines():
        match = re.fullmatch(r"(\S+) -> (\S+):", line)
        if match:
            owner = "L" + match[2].replace(".", "/") + ";"
            class_metadata = True
            if "$$ExternalSynthetic" in match[1]:
                result.add(owner)
        elif class_metadata and line.startswith("#"):
            if '"id":"com.android.tools.r8.synthesized"' in line.replace(" ", ""):
                result.add(owner)
        elif line.strip():
            class_metadata = False
    return result


def protected_original(name):
    return CORE.fullmatch(name) and "$$ExternalSynthetic" not in name


def choose_targets(mapping, strings, inventory):
    match = re.search(r"^# pg_map_id: (\S+)", mapping, re.M)
    require(match, "R8 mapping ID missing")
    markers = [json.loads(s[4:]) for s in strings if s.startswith("~~R8{")]
    require(any(m.get("pg-map-id") == match[1] for m in markers),
            "APK and R8 mapping do not match. Use mapping from this exact APK build.")
    targets = {}
    shared_synthetics = synthetic_descriptors(mapping)
    for line in mapping.splitlines():
        m = re.fullmatch(r"(\S+) -> (\S+):", line)
        if m and protected_original(m[1]) and "REMOVED" not in m[2]:
            descriptor = "L" + m[2].replace(".", "/") + ";"
            if descriptor in inventory and descriptor not in shared_synthetics:
                targets.setdefault(descriptor, []).append(m[1])
    require(any("com.abrar.raha.RahaCommandRecognizer" in names for names in targets.values()),
            "Main Raha recognizer missing from target list")
    require(len(targets) >= 6, "Unexpectedly small Raha target list")
    require(BRIDGE not in targets and BRIDGE in inventory, "Root bridge missing or selected for protection")
    # Fail closed on a merged non-Raha class sharing a selected output class.
    for line in mapping.splitlines():
        m = re.fullmatch(r"(\S+) -> (\S+):", line)
        if m and "L" + m[2].replace(".", "/") + ";" in targets:
            require(protected_original(m[1]), "R8 merged protected and shared code: " + m[1])
    # Merged lambdas can have a single class header; also check method owners.
    owner = None
    for line in mapping.splitlines():
        m = re.fullmatch(r"(\S+) -> (\S+):", line)
        if m:
            owner = "L" + m[2].replace(".", "/") + ";"
        elif owner in targets:
            for original in re.findall(r"(com\.abrar\.[\w.$]+)\.[\w$<>]+\(", line):
                require(not original.startswith("com.abrar.voyahiran."),
                        "Protected class contains shared app method: " + original)
    # R8 may inline an original method into a class bearing an unrelated name.
    # Type mentions in fields/parameters alone are fine; only method owners count.
    owner = None
    leaks = set()
    for line in mapping.splitlines():
        m = re.fullmatch(r"(\S+) -> (\S+):", line)
        if m:
            owner = "L" + m[2].replace(".", "/") + ";"
        elif owner in inventory and owner not in targets:
            for original in re.findall(r"(com\.abrar\.raha\.[\w$]+)\.[\w$<>]+\(", line):
                if protected_original(original):
                    leaks.add(owner)
    require(not leaks, "Raha methods were inlined/merged into unprotected classes: "
            + ", ".join(sorted(leaks)) + ". Rebuild Release with the updated protection keep rules.")
    return targets, match[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/release/app-release.apk")
    parser.add_argument("--mapping", type=Path, default=ROOT / "app/build/outputs/mapping/release/mapping.txt")
    parser.add_argument("--cache", type=Path, default=ROOT / "build/dpt-cache")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    apk, java = args.apk.resolve(), args.java.resolve()
    require(apk.is_file() and java.is_file(), "Input APK or Java executable missing")
    inventory, strings = apk_inventory(apk)
    mapping = args.mapping.read_text(encoding="utf-8")
    targets, map_id = choose_targets(mapping, strings, inventory)
    output = (args.output or ROOT / "build/raha-protected" /
              (datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8])).resolve()
    require(not output.exists(), "Output directory already exists; choose a new one")
    output.mkdir(parents=True)
    log = output / "protection.log"
    source_hash = digest(apk.read_bytes())
    shutil.copy2(apk, output / "input.apk")
    shutil.copy2(args.mapping, output / "mapping.txt")
    # Java regex matches the whole descriptor. Everything except these classes is excluded.
    rules = output / "exclude.rules"
    rules.write_text("(?!(?:" + "|".join(re.escape(t) for t in sorted(targets)) + ")$).*\n", encoding="utf-8")
    tool = prepare_tool(java, args.cache.resolve(), log)
    unsigned = output / "raha-protected-unsigned.apk"
    print(f"Protecting {len(targets)} Raha classes; original APK preserved.", flush=True)
    run([java, "-jar", tool, "-f", output / "input.apk", "-K", "-r", rules,
         "-x", "-o", unsigned], tool.parent, log)
    require(unsigned.is_file(), "DPT did not produce an output APK")
    # Preserve the runtime's MIT notice in the redistributed APK. The signing
    # step runs zipalign again after this ZIP update.
    license_text = Path(__file__).with_name("DPT-LICENSE.txt").read_text(encoding="utf-8")
    with zipfile.ZipFile(unsigned, "a") as z:
        z.writestr("assets/NOTICE-dpt-shell.txt", f"dpt-shell {VERSION}\n{REPO}\n\n" + license_text)
    after, _ = apk_inventory(unsigned)
    require(not targets.keys() & after.keys(), "Protected Raha classes are still in ordinary DEX")
    for name, methods in inventory.items():
        if name not in targets:
            require(name in after, "Unprotected class disappeared: " + name)
            # The compatibility patch must leave bridge/vendor/other method inventories intact.
            require(after[name] == methods, "Unprotected class was modified: " + name)
    require(any(n == "main" and flags & 8 and size > 0 for n, flags, size in after[BRIDGE]),
            "Root bridge static main is missing")
    with zipfile.ZipFile(apk) as original, zipfile.ZipFile(unsigned) as protected:
        for name in original.namelist():
            if name.startswith(("lib/", "assets/", "voi-g2p/")) and not name.endswith("/"):
                require(name in protected.namelist() and original.read(name) == protected.read(name),
                        "Existing model/native asset changed: " + name)
    require(digest(apk.read_bytes()) == source_hash, "Original APK changed")
    report = {"dpt_version": VERSION, "upstream_archive_sha256": ARCHIVE_SHA,
              "patched_tool_sha256": digest(tool.read_bytes()), "r8_mapping_id": map_id,
              "input_sha256": source_hash, "unsigned_sha256": digest(unsigned.read_bytes()),
              "protected_classes": targets, "preserved_classes": len(inventory) - len(targets),
              "static_checks": "passed", "r8_boundaries_checked": True,
              "shared_synthetic_classes_preserved": sorted(synthetic_descriptors(mapping) & inventory.keys()),
              "runtime_tested": False, "signed": False,
              "limitations": ["No runtime compatibility or anti-extraction guarantee",
                              "Static mapping checks are not a proof against runtime extraction",
                              "Models, constants and app-process bridge are not encrypted",
                              "DPT runtime signature verification is not enabled"]}
    (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("STATIC CHECKS PASSED; unsigned, NOT ready to install:", unsigned)
    print("Sign with the existing app keystore, then test on hardware. Do not distribute mapping.txt.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAILED:", error, file=sys.stderr)
        sys.exit(1)
