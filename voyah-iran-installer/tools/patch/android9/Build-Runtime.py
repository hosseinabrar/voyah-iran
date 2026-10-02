"""Reproduce the minimal local patch-tool runtime from the pinned JADX archive."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

root = Path(__file__).resolve().parent
metadata = json.loads((root / 'runtime-provenance.json').read_text())
source = Path(sys.argv[1])
assert hashlib.sha256(source.read_bytes()).hexdigest() == metadata['sourceSha256'], 'Unexpected source archive'
output = root / 'smali-runtime.jar'
with zipfile.ZipFile(source) as archive, zipfile.ZipFile(output, 'w') as target:
    for name in sorted(archive.namelist()):
        if not name.endswith('/') and (name.startswith(tuple(metadata['includedPrefixes'])) or name in ('smali.properties', 'baksmali.properties')):
            entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(entry, archive.read(name))
assert hashlib.sha256(output.read_bytes()).hexdigest() == metadata['runtimeSha256'], 'Runtime differs'
print('Pinned runtime reproduced and verified:', output)
