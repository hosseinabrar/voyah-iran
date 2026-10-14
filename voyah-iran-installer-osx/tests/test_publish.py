import importlib.util
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
                 patch.object(p, "build_android", side_effect=RuntimeError("Gradle failed")), \
                 patch.object(p, "make_payload") as package, \
                 patch.object(p.sys, "platform", "darwin"), \
                 patch.object(p.sys, "argv", ["publish.py", "--java-home", "/unused/jdk"]):
                with self.assertRaisesRegex(RuntimeError, "Gradle failed"): p.main()
                package.assert_not_called()

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
