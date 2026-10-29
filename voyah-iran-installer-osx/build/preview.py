"""Build a frontend-only preview; contains no backend, ADB or install payload."""
import json
import platform
import plistlib
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
destination = Path(tempfile.mkdtemp(prefix='voyah-installer-ui-'))
app = destination / 'Voyah Installer Preview.app'
macos = app / 'Contents/MacOS'
resources = app / 'Contents/Resources'
macos.mkdir(parents=True)
resources.mkdir()
shutil.copytree(ROOT.parent / 'voyah-iran/app/src/main/assets/passenger/fonts', resources / 'Fonts')
subprocess.run(['xcrun', 'swiftc', '-parse-as-library', '-target',
                f'{platform.machine()}-apple-macosx12.0', str(ROOT / 'Sources/App.swift'),
                '-o', str(macos / 'VoyahIranInstaller')], check=True)
(app / 'Contents/Info.plist').write_bytes(plistlib.dumps({
    'CFBundleExecutable': 'VoyahIranInstaller', 'CFBundleIdentifier': 'com.abrar.voyahiran.installer.preview',
    'CFBundleName': 'Voyah Installer Preview', 'CFBundlePackageType': 'APPL',
    'LSMinimumSystemVersion': '12.0', 'NSHighResolutionCapable': True,
    'LSEnvironment': {'VI_MAC_UI_PREVIEW': '1'},
}))
print(json.dumps({'app': str(app)}, ensure_ascii=False))
