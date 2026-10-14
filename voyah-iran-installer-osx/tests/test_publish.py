import importlib.util
import contextlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("publish", Path(__file__).resolve().parents[1] / "build/publish.py")
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class PublishTests(unittest.TestCase):
    def test_mac_icon_preserves_windows_png_frames(self):
        with tempfile.TemporaryDirectory() as tmp:
            resources = Path(tmp)
            icon = (resources / p.install_icon(resources)).read_bytes()
            self.assertEqual(icon[:4], b"icns")
            self.assertEqual(p.struct.unpack_from(">I", icon, 4)[0], len(icon))
            source = (p.WINDOWS / "src/VoyahIran.Installer/Assets/voyah-iran.ico").read_bytes()
            offset, types = 8, []
            while offset < len(icon):
                kind, length = p.struct.unpack_from(">4sI", icon, offset)
                png = icon[offset + 8:offset + length]
                self.assertTrue(png.startswith(b"\x89PNG\r\n\x1a\n"))
                self.assertIn(png, source)
                types.append(kind)
                offset += length
            self.assertEqual(offset, len(icon))
            self.assertIn(b"ic08", types)

    def test_output_names_match_windows(self):
        self.assertEqual(p.output_stem("launcher"), "voyah-iran-installer_launcher")
        self.assertEqual(p.output_stem("2.0.3"), "voyah-iran-installer_2.0.3")
        self.assertEqual(p.output_stem("2.0.3", True), "voyah-iran-installer_2.0.3-development")
        with self.assertRaises(RuntimeError): p.output_stem("../outside")

    def test_repeated_publish_replaces_same_outputs_and_removes_stale_app_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dist = root / "dist"
            names = ["installer.app", "installer.zip", "installer.build-report.json"]
            for version in ("old", "new"):
                staged = root / version
                (staged / names[0]).mkdir(parents=True)
                (staged / names[0] / version).write_text(version)
                for name in names[1:]: (staged / name).write_text(version)
                p.replace_outputs(staged, dist, names)
            self.assertEqual(sorted(x.name for x in dist.iterdir()), sorted(names))
            self.assertFalse((dist / names[0] / "old").exists())
            self.assertEqual((dist / names[0] / "new").read_text(), "new")
            self.assertEqual((dist / names[1]).read_text(), "new")

    def test_failed_promotion_restores_previous_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            staged, dist = root / "staged", root / "dist"
            staged.mkdir(); dist.mkdir()
            names = ["installer.zip", "installer.build-report.json"]
            for name in names:
                (staged / name).write_text("new")
                (dist / name).write_text("old")
            original = Path.replace
            def fail_report(source, target):
                if source == staged / names[1]: raise OSError("disk error")
                return original(source, target)
            with patch.object(Path, "replace", fail_report):
                with self.assertRaisesRegex(OSError, "disk error"):
                    p.replace_outputs(staged, dist, names)
            for name in names: self.assertEqual((dist / name).read_text(), "old")

    def test_output_symlink_is_not_followed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            staged, dist = root / "staged", root / "dist"
            staged.mkdir(); dist.mkdir()
            outside = root / "unrelated"
            outside.write_text("preserve")
            (staged / "installer.zip").write_text("new")
            (dist / "installer.zip").symlink_to(outside)
            with self.assertRaises(RuntimeError): p.replace_outputs(staged, dist, ["installer.zip"])
            self.assertEqual(outside.read_text(), "preserve")

    def test_payload_normalizes_scripts_and_hashes_actual_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            script = root / "script.sh"
            script.write_bytes(b"\xef\xbb\xbfline\r\n")
            binary = root / "binary"
            binary.write_bytes(b"binary fixture")
            bundle = root / "bundle.zip"
            p.make_payload(bundle, {"install-bridge.sh": script, "roa/hook.sha256": binary, "app.apk": binary}, "2.0.3", "osx-arm64")
            with zipfile.ZipFile(bundle) as z:
                manifest = json.loads(z.read("manifest.json"))
                self.assertEqual(z.read("install-bridge.sh"), b"line\n")
                self.assertEqual(z.read("roa/hook.sha256").decode(), p.sha(binary))
                self.assertEqual(manifest["TargetRuntime"], "osx-arm64")
                for name, digest in manifest["Files"].items():
                    self.assertEqual(p.hashlib.sha256(z.read(name)).hexdigest(), digest)

    def test_preferences_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "settings.json"
            for value in ({"ProtectionEnabled": True, "MinifyEnabled": False}, {"ProtectionEnabled": "false", "MinifyEnabled": True}):
                path.write_text(json.dumps(value))
                with self.assertRaises(RuntimeError): p.preferences(path)

    def test_failed_android_build_never_packages_stale_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(p, "ROOT", Path(tmp)), patch.object(p, "version_info", return_value=("2.0.3", 18, "2.0.3")), \
                 patch.object(p, "preferences", return_value={"ProtectionEnabled": False, "MinifyEnabled": True}), \
                 patch.object(p, "run", return_value="8.0.426"), \
                 patch.object(p, "java_homes", return_value={}), patch.object(p, "check_native"), \
                 patch.object(p, "build_android", side_effect=RuntimeError("Gradle failed")), \
                 patch.object(p, "make_payload") as package, \
                 patch.object(p.sys, "platform", "darwin"), \
                 patch.object(p.sys, "argv", ["publish.py", "--java-home", "/unused/jdk"]):
                with self.assertRaisesRegex(RuntimeError, "Gradle failed"): p.main()
                package.assert_not_called()

    def test_native_checks_reject_wrong_arch_and_newer_macos(self):
        with patch.object(p, "run", side_effect=["arm64", "cmd LC_BUILD_VERSION\n minos 11.0\n"]):
            p.check_native(Path("java"), "arm64")
        with patch.object(p, "run", return_value="arm64"):
            with self.assertRaisesRegex(RuntimeError, "Missing x86_64"):
                p.check_native(Path("java"), "x86_64")
        with patch.object(p, "run", side_effect=["x86_64", "cmd LC_BUILD_VERSION\n minos 13.0\n"]):
            with self.assertRaisesRegex(RuntimeError, "above 12.0"):
                p.check_native(Path("java"), "x86_64")
        with patch.object(p, "run", side_effect=["x86_64", "cmd LC_VERSION_MIN_MACOSX\n cmdsize 16\n version 10.15\n"]):
            p.check_native(Path("java"), "x86_64")

    def test_universal_requires_both_java_architectures(self):
        from types import SimpleNamespace
        args = SimpleNamespace(java_home="/unused", java_home_arm64="", java_home_x64="")
        with patch.object(Path, "is_file", return_value=True), \
             patch.object(Path, "read_text", return_value='JAVA_VERSION="25.0.4.1"\nOS_NAME="Darwin"'), \
             patch.object(p, "check_native"):
            with self.assertRaisesRegex(RuntimeError, "JavaHomeX64"):
                p.java_homes(args, "osx-arm64")

    def test_universal_build_plan_without_running_any_build(self):
        from types import SimpleNamespace
        args = SimpleNamespace(dotnet="dotnet", java_home="/host", sdk="/sdk", development=False, identity="-")
        prefs = {"ProtectionEnabled": False, "MinifyEnabled": True}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def fake_run(argv, **kwargs):
                argv = list(map(str, argv))
                if "--version" in argv: return "8.0.426"
                if "required-files" in argv: return "[]"
                return ""
            # All compilers/processes and packaging writes are mocked: no build is performed.
            with patch.object(p, "ROOT", root), patch.object(p, "run", side_effect=fake_run) as commands, \
                 patch.object(p, "java_homes", return_value={r: root / r for r in p.TARGETS}), \
                 patch.object(p, "build_android", return_value=(root / "app.apk", root, "certificate")) as android, \
                 patch.object(p, "check_native"), patch.object(p, "check_native_tree"), \
                 patch.object(p, "copy_java_runtime"), patch.object(p, "payload_sources", return_value={}), \
                 patch.object(p, "make_payload"), patch.object(p.shutil, "copytree"), patch.object(p.shutil, "copy2"), \
                 patch.object(p, "install_icon", return_value="icon.icns"), patch.object(p, "sha", return_value="hash"), \
                 patch.object(p, "replace_outputs") as promote:
                with contextlib.redirect_stdout(io.StringIO()):
                    p.publish(args, {}, "2.0.3", 18, "2.0.3", prefs, "osx-arm64", root)
            android.assert_called_once()
            calls = [list(map(str, c.args[0])) for c in commands.call_args_list]
            publishes = [c for c in calls if "publish" in c]
            self.assertEqual({c[c.index("-r") + 1] for c in publishes}, set(p.TARGETS))
            compiles = [c for c in calls if "swiftc" in c]
            self.assertEqual({c[c.index("-target") + 1] for c in compiles}, {"arm64-apple-macosx12.0", "x86_64-apple-macosx12.0"})
            self.assertTrue(any(c[:2] == ["/usr/bin/lipo", "-create"] for c in calls))
            verifications = [c for c in calls if "verify-payload" in c]
            self.assertEqual(len(verifications), 2)
            self.assertTrue(all("osx-arm64" in c[0] for c in verifications))
            promote.assert_called_once()
            staged = promote.call_args.args[0]
            report = json.loads((staged / "voyah-iran-installer_2.0.3.build-report.json").read_text())
            self.assertEqual(report["runtime"], "osx-universal")
            self.assertFalse(report["macOS12Tested"])

    def test_certificate_rejects_multiple_signers(self):
        line = "Signer #1: certificate SHA-256 digest: " + "a" * 64
        with patch.object(p, "run", return_value=line):
            self.assertEqual(p.certificate("java", "signer", "apk", {}), "a" * 64)
        with patch.object(p, "run", return_value=line + "\nSigner #2: certificate SHA-256 digest: " + "b" * 64):
            with self.assertRaises(RuntimeError): p.certificate("java", "signer", "apk", {})

    def test_missing_branch_payload_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(RuntimeError):
                p.payload_sources(["app.apk"], root / "missing.apk", root, root)


if __name__ == "__main__": unittest.main()
