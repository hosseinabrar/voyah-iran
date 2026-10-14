#!/usr/bin/env python3
"""macOS: build signed Android -> optional DPT -> verified payload -> native .app."""
import argparse
import fcntl
import getpass
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
ANDROID = REPO / "voyah-iran"
WINDOWS = REPO / "voyah-iran-installer"
BACKEND = ROOT / "backend/VoyahIran.Installer.Mac.csproj"
SETTINGS = ROOT / "build/BuildSettings.local.json"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def run(argv, *, env=None, cwd=ROOT, capture=False):
    # Never log argv/environment: subprocesses may receive signing credentials via env.
    result = subprocess.run([str(a) for a in argv], cwd=cwd, env=env, check=True,
                            stdout=subprocess.PIPE if capture else None,
                            text=True)
    return result.stdout.strip() if capture else ""


def sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def output_stem(label, development=False):
    require(bool(re.fullmatch(r"[A-Za-z0-9._-]+", label)), "Invalid release label")
    return f"voyah-iran-installer_{label}" + ("-development" if development else "")


def replace_outputs(staged, dist, names):
    """Promote only completed artifacts; roll back an ordinary promotion failure."""
    dist.mkdir(exist_ok=True)
    backup = staged / "previous-output"
    backup.mkdir()
    saved, installed = [], []
    for name in names:
        require(Path(name).name == name and name not in (".", ".."), "Invalid output name")
        source, target = staged / name, dist / name
        require(source.exists() and not source.is_symlink(), "Missing staged output: " + name)
        require(not target.is_symlink(), "Refusing to replace symlink: " + str(target))
        require(not target.exists() or source.is_dir() == target.is_dir(), "Unexpected output type: " + str(target))
    try:
        for name in names:
            target = dist / name
            if target.exists():
                target.replace(backup / name)
                saved.append(name)
            (staged / name).replace(target)
            installed.append(name)
    except OSError:
        for name in reversed(installed):
            (dist / name).replace(staged / name)
        for name in reversed(saved):
            (backup / name).replace(dist / name)
        raise
    # Only the exact previous artifacts moved above are removed, never dist itself.
    for name in saved:
        previous = backup / name
        if previous.is_dir(): shutil.rmtree(previous)
        else: previous.unlink()
    backup.rmdir()


def preferences(path=SETTINGS):
    value = json.loads(path.read_text()) if path.exists() else {"ProtectionEnabled": False, "MinifyEnabled": True}
    require(type(value.get("ProtectionEnabled")) is bool and type(value.get("MinifyEnabled")) is bool, "Invalid build settings")
    require(not value["ProtectionEnabled"] or value["MinifyEnabled"], "Protection requires R8")
    return value


def version_info():
    gradle = (ANDROID / "app/build.gradle.kts").read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1))
    project = ET.parse(WINDOWS / "src/VoyahIran.Installer/VoyahIran.Installer.csproj")
    label = project.findtext(".//InformationalVersion") or project.findtext(".//Version")
    require(bool(re.fullmatch(r"[A-Za-z0-9._-]+", label or "")), "Invalid release label")
    return version, code, label


def certificate(java, signer, apk, env):
    text = run([java, "-jar", signer, "verify", "--print-certs", apk], env=env, capture=True)
    certs = set(re.findall(r"(?m)^\s*(?:Signer #\d+|V[1-4](?:\.\d+)? Signer): certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$", text))
    require(len(certs) == 1, "Expected exactly one verified APK signing certificate")
    return next(iter(certs)).lower()


def check_apk(apk, mapping, aapt, version, code, env):
    badging = run([aapt, "dump", "badging", apk], env=env, capture=True)
    require(re.search(r"^package: name='com\.abrar\.voyahiran' versionCode='" + str(code) + r"' versionName='" + re.escape(version) + "'", badging) is not None,
            "APK package/version does not match this branch")
    descriptor = "Lcom/abrar/voyahiran/core/bridge/RoaHookManager;"
    if mapping:
        match = re.search(r"(?m)^com\.abrar\.voyahiran\.core\.bridge\.RoaHookManager -> ([^:]+):\r?$", mapping.read_text())
        require(match is not None, "ROA mapping missing")
        descriptor = "L" + match.group(1).replace(".", "/") + ";"
    markers = [b"VI_NESHAN_CONFIG_ENGINE_V1", b"Lcom/abrar/voyahiran/core/bridge/AppInventory;", b"voyah_iran_ime_override", descriptor.encode()]
    with zipfile.ZipFile(apk) as archive:
        manifest = archive.read("AndroidManifest.xml")
        for permission in ("android.permission.RECORD_AUDIO", "android.permission.SYSTEM_ALERT_WINDOW"):
            require(permission.encode() in manifest or permission.encode("utf-16-le") in manifest, "Missing permission: " + permission)
        found = set()
        for name in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                dex = archive.read(name)
                found.update(m for m in markers if m in dex)
        require(len(found) == len(markers), "APK is missing current bridge/keyboard/navigation contracts")


def signing_environment(base, keystore, alias):
    env = base.copy()
    def secret(variable, service):
        if env.get(variable):
            return env[variable]
        found = subprocess.run(["/usr/bin/security", "find-generic-password", "-s", service, "-a", alias, "-w"],
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        if found.returncode == 0:
            return found.stdout.rstrip("\n")
        require(sys.stdin.isatty(), "Signing password missing. Run Build Settings.command, or set VI_DPT_STORE_PASSWORD and VI_DPT_KEY_PASSWORD.")
        return getpass.getpass("Existing Android signing password (hidden): ")
    env["VI_DPT_STORE_PASSWORD"] = secret("VI_DPT_STORE_PASSWORD", "VoyahIran.Android.StorePassword")
    if not env.get("VI_DPT_KEY_PASSWORD"):
        found = subprocess.run(["/usr/bin/security", "find-generic-password", "-s", "VoyahIran.Android.KeyPassword", "-a", alias, "-w"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        env["VI_DPT_KEY_PASSWORD"] = found.stdout.rstrip("\n") if found.returncode == 0 else env["VI_DPT_STORE_PASSWORD"]
    require(bool(env["VI_DPT_STORE_PASSWORD"]), "Signing password is empty")
    env["VI_PUBLISH_KEYSTORE"] = str(keystore)
    env["VI_PUBLISH_ALIAS"] = alias
    return env


def build_android(args, work, env, version, code, prefs):
    java_home = Path(args.java_home).resolve()
    java = java_home / "bin/java"
    sdk = Path(args.sdk).resolve()
    tools = sdk / "build-tools/37.0.0"
    for path in [java, java_home / "bin/javac", java_home / "bin/jlink", tools / "lib/apksigner.jar", tools / "zipalign", tools / "aapt2", sdk / "platforms/android-36/android.jar"]:
        require(path.is_file(), "Build prerequisite missing: " + str(path))
    probe = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    require(re.search(r'version "25[.\"]', probe.stderr + probe.stdout), "Use JDK 25 to match the Gradle daemon toolchain")
    env = env.copy()
    env.update(JAVA_HOME=str(java_home), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    env = signing_environment(env, Path(args.keystore).resolve(), args.alias)
    print("1/4 Building a fresh signed Android release…", flush=True)
    # No prebuilt APK fallback. A failed build cannot package yesterday's APK.
    run(["/bin/sh", ANDROID / "gradlew", "--no-daemon", "--console=plain",
         "-Pvoyah.minify=" + str(prefs["MinifyEnabled"]).lower(), ":app:assembleRelease"], cwd=ANDROID, env=env)
    built = ANDROID / "app/build/outputs/apk/release/app-release.apk"
    require(built.is_file(), "Signed release APK missing")
    release = work / "app-release.apk"
    shutil.copy2(built, release)
    mapping = ANDROID / "app/build/outputs/mapping/release/mapping.txt" if prefs["MinifyEnabled"] else None
    signer = tools / "lib/apksigner.jar"
    original_cert = certificate(java, signer, release, env)
    check_apk(release, mapping, tools / "aapt2", version, code, env)
    # The same original keystore used by Gradle is retained; protection never creates a key.
    if prefs["ProtectionEnabled"]:
        protected = work / "protected"
        print("Protecting the application and checking root dependency boundaries…", flush=True)
        no_secrets = {k: v for k, v in env.items() if k not in ("VI_DPT_STORE_PASSWORD", "VI_DPT_KEY_PASSWORD")}
        run([sys.executable, ANDROID / "deployment/protection/protect_raha.py", "--java", java,
             "--apk", release, "--mapping", mapping, "--output", protected], env=no_secrets)
        report = json.loads((protected / "report.json").read_text())
        require(report.get("static_checks") == "passed" and report.get("r8_boundaries_checked") is True and
                report.get("protection_scope") == "application-v2" and report.get("root_dependency_closure_checked") is True,
                "Protection checks failed")
        unsigned = protected / "raha-protected-unsigned.apk"
        require(sha(unsigned) == report["unsigned_sha256"] and sha(release) == report["input_sha256"], "Protection input/output mismatch")
        aligned, signed = protected / "aligned.apk", protected / "raha-protected.apk"
        run([tools / "zipalign", "-f", "-P", "16", "4", unsigned, aligned], env=no_secrets)
        run([java, "-jar", signer, "sign", "--ks", args.keystore, "--ks-key-alias", args.alias,
             "--ks-pass", "env:VI_DPT_STORE_PASSWORD", "--key-pass", "env:VI_DPT_KEY_PASSWORD",
             "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v4-signing-enabled", "false",
             "--out", signed, aligned], env=env)
        require(certificate(java, signer, signed, env) == original_cert, "Protected APK signing certificate changed")
        run([tools / "zipalign", "-c", "-P", "16", "4", signed], env=no_secrets)
        check_apk(signed, mapping, tools / "aapt2", version, code, no_secrets)
        (protected / "signed-report.json").write_text(json.dumps({"sha256": sha(signed), "certificate": original_cert, "runtime_tested": False}, indent=2))
        release = signed
    # Password variables are scoped to this function's child environment only.
    return release, java_home, original_cert


def payload_sources(required, apk, java_runtime, adb_dir):
    deployment = ANDROID / "deployment"
    special = {"app.apk": apk, "adb": adb_dir / "adb", "NOTICE.txt": adb_dir / "NOTICE.txt",
               "install-bridge.sh": deployment / "install-bridge.sh", "bridge.rc": deployment / "voyahiran-bridge.rc",
               "manage-keyboard-framework.sh": WINDOWS / "tools/patch/manage-dynamic-framework.sh",
               "roa/frida-inject": deployment / "roa/frida-inject", "roa/hook.js": deployment / "roa/hook_camera_front_roa_switch.js",
               "roa/frida-inject.sha256": deployment / "roa/frida-inject", "roa/hook.sha256": deployment / "roa/hook_camera_front_roa_switch.js",
               "roa/FRIDA-COPYING.txt": deployment / "roa/FRIDA-COPYING.txt"}
    files = {}
    for name in required:
        files[name] = special.get(name, java_runtime / name[5:] if name.startswith("java/") else WINDOWS / "tools" / name)
    for file in sorted(java_runtime.rglob("*")):
        if file.is_file():
            files["java/" + file.relative_to(java_runtime).as_posix()] = file
    for name, file in files.items():
        require(file.is_file(), "Payload source missing: " + str(file))
    return files


def make_payload(destination, files, version, runtime):
    hashes = {}
    with zipfile.ZipFile(destination, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
        for name, source in files.items():
            if name.endswith(".sha256"):
                data = sha(source).encode("ascii")
            elif name.endswith((".sh", ".rc")):
                data = source.read_text(encoding="utf-8-sig").replace("\r\n", "\n").encode("utf-8")
            else:
                # Stream large APK/JDK files instead of keeping an entire bundle in RAM.
                hashes[name] = sha(source)
                archive.write(source, name)
                continue
            hashes[name] = hashlib.sha256(data).hexdigest()
            archive.writestr(name, data)
        archive.writestr("manifest.json", json.dumps({"AppVersion": version, "PatchSchema": 1, "Files": hashes, "TargetRuntime": runtime}))


def main():
    toolchain_path = ROOT / "build/Toolchain.local.json"
    toolchain = json.loads(toolchain_path.read_text()) if toolchain_path.exists() else {}
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--development", action="store_true", help="Build UI/backend only; cannot install on a vehicle")
    parser.add_argument("--java-home", default=toolchain.get("JavaHome") or os.environ.get("JAVA_HOME", ""))
    parser.add_argument("--sdk", default=toolchain.get("AndroidSdk") or os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
    parser.add_argument("--dotnet", default=os.environ.get("VI_DOTNET", toolchain.get("Dotnet", "dotnet")))
    parser.add_argument("--keystore", default=str(ANDROID / "Keystore/voyahiran"))
    parser.add_argument("--alias", default="voyahiran")
    parser.add_argument("--protection", action=argparse.BooleanOptionalAction, default=None)
    parser.add_argument("--minify", action=argparse.BooleanOptionalAction, default=None)
    parser.add_argument("--identity", default="-", help="Apple Developer ID Application identity, or '-' for local ad-hoc testing")
    args = parser.parse_args()
    require(sys.platform == "darwin", "Publish must run on macOS")
    version, code, label = version_info()
    prefs = preferences()
    if args.protection is not None: prefs["ProtectionEnabled"] = args.protection
    if args.minify is not None: prefs["MinifyEnabled"] = args.minify
    require(not prefs["ProtectionEnabled"] or prefs["MinifyEnabled"], "Protection requires R8")
    require(not prefs["ProtectionEnabled"] or sys.version_info >= (3, 10), "DPT protection requires Python 3.10+")
    runtime = "osx-arm64" if os.uname().machine == "arm64" else "osx-x64"
    arch = "arm64" if runtime == "osx-arm64" else "x86_64"
    # Build all caches within this project; no changes to global SDK/JDK configuration.
    cache = ROOT / ".build"
    cache.mkdir(exist_ok=True)
    with (cache / "publish.lock").open("a") as publish_lock:
        try:
            fcntl.flock(publish_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("Another Publish is running in this checkout; wait for it to finish.")
        publish(args, toolchain, version, code, label, prefs, runtime, arch, cache)


def publish(args, toolchain, version, code, label, prefs, runtime, arch, cache):
    work = Path(tempfile.mkdtemp(prefix="publish-", dir=cache))
    for name in ("tmp", "cli", "nuget", "gradle", "clang"):
        (cache / name).mkdir(exist_ok=True)
    env = os.environ.copy()
    env.update(DOTNET_CLI_HOME=str(cache / "cli"), DOTNET_CLI_TELEMETRY_OPTOUT="1", DOTNET_SKIP_FIRST_TIME_EXPERIENCE="1",
               DOTNET_GENERATE_ASPNET_CERTIFICATE="false",
               NUGET_PACKAGES=str(cache / "nuget"), GRADLE_USER_HOME=str(toolchain.get("GradleUserHome", cache / "gradle")), TMPDIR=str(cache / "tmp"),
               CLANG_MODULE_CACHE_PATH=str(cache / "clang"))
    require(run([args.dotnet, "--version"], env=env, capture=True).startswith("8."), ".NET SDK 8 is required; set VI_DOTNET")
    if not args.development:
        require(bool(args.java_home), "Set JAVA_HOME to JDK 25 (or use --java-home)")
        apk, java_home, cert = build_android(args, work, env, version, code, prefs)
    print("Building the shared installer backend…", flush=True)
    backend = work / "backend"
    run([args.dotnet, "publish", BACKEND, "-c", "Release", "-r", runtime, "--self-contained", "true",
         "-p:PublishSingleFile=false", "--nologo", "-o", backend], env=env)
    executable = backend / "VoyahIran.Installer.Mac"
    if not args.development:
        print("2/4 Preparing and verifying the offline payload…", flush=True)
        java_runtime = work / "java"
        run([java_home / "bin/jlink", "--add-modules", "java.base,java.desktop,java.logging,java.xml,java.compiler,jdk.compiler,jdk.zipfs,jdk.unsupported",
             "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2", "--output", java_runtime], env=env)
        for name in ("NOTICE", "LICENSE"):
            if (java_home / name).is_file(): shutil.copy2(java_home / name, java_runtime / name)
        require(arch in run(["/usr/bin/file", java_runtime / "bin/java"], capture=True), "Bundled Java architecture differs from the app")
        adb_dir = Path(args.sdk).resolve() / "platform-tools"
        require(arch in run(["/usr/bin/file", adb_dir / "adb"], capture=True), "ADB must support the target Mac architecture")
        required = json.loads(run([executable, "required-files", runtime], env=env, capture=True))
        bundle = work / "bundle.zip"
        make_payload(bundle, payload_sources(required, apk, java_runtime, adb_dir), version, runtime)
        run([executable, "verify-payload", bundle, work / "payload-check"], env=env, capture=True)
        # Execute the extracted runtime too: validates source launcher, compiler and dylib permissions.
        run([work / "payload-check/java/bin/java", "--list-modules"], env=env, capture=True)
        run([work / "payload-check/adb", "version"], env=env, capture=True)
    print("3/4 Building the native macOS application…", flush=True)
    stem = output_stem(label, args.development)
    app = work / (stem + ".app")
    macos, resources = app / "Contents/MacOS", app / "Contents/Resources"
    macos.mkdir(parents=True); resources.mkdir(parents=True)
    run(["/usr/bin/xcrun", "swiftc", "-parse-as-library", "-O", "-target", f"{arch}-apple-macosx13.0",
         "-module-cache-path", cache / "clang", ROOT / "Sources/App.swift", "-o", macos / "VoyahIranInstaller"], env=env)
    shutil.copytree(backend, resources / "backend")
    if not args.development: shutil.copy2(bundle, resources / "bundle.zip")
    info = {"CFBundleExecutable": "VoyahIranInstaller", "CFBundleIdentifier": "com.abrar.voyahiran.installer.osx",
            "CFBundleName": "Voyah Iran Installer", "CFBundleDisplayName": "نصاب وویا ایران", "CFBundlePackageType": "APPL",
            "CFBundleShortVersionString": version, "CFBundleVersion": str(code), "LSMinimumSystemVersion": "13.0",
            "NSHighResolutionCapable": True, "NSPrincipalClass": "NSApplication", "LSMultipleInstancesProhibited": True}
    (app / "Contents/Info.plist").write_bytes(plistlib.dumps(info))
    # Sign nested .NET code before the outer bundle. No sandbox: ADB needs USB/process access.
    for file in sorted((resources / "backend").rglob("*")):
        if file.is_file() and (file.suffix == ".dylib" or file.name == "VoyahIran.Installer.Mac"):
            run(["/usr/bin/codesign", "--force", "--sign", args.identity, file], env=env)
    run(["/usr/bin/codesign", "--force", "--sign", args.identity, app], env=env)
    run(["/usr/bin/codesign", "--verify", "--deep", "--strict", app], env=env)
    print("4/4 Packaging…", flush=True)
    archive = work / (stem + ".zip")
    run(["/usr/bin/ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", app, archive], env=env)
    report = work / (stem + ".build-report.json")
    report.write_text(json.dumps({"version": version, "versionCode": code, "label": label,
        "runtime": runtime, "developmentOnly": args.development, "protection": prefs["ProtectionEnabled"], "minify": prefs["MinifyEnabled"],
        "apkSha256": None if args.development else sha(apk), "certificate": None if args.development else cert,
        "zipSha256": sha(archive), "vehicleTested": False, "notarized": False}, indent=2))
    dist = ROOT / "dist"
    replace_outputs(work, dist, [app.name, archive.name, report.name])
    print("App: " + str(dist / app.name), flush=True)
    print("ZIP: " + str(dist / archive.name), flush=True)
    print("Local/ad-hoc build; public distribution needs Apple signing/notarization and vehicle testing.")


if __name__ == "__main__":
    try: main()
    except (RuntimeError, OSError, subprocess.CalledProcessError, ValueError) as error:
        print("Publish failed: " + str(error), file=sys.stderr)
        sys.exit(1)
