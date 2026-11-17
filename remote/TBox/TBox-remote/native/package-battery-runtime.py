"""Package the owner's already extracted MPU dependencies, without building or device access."""
from pathlib import Path
import json,hashlib,tarfile
root=Path(__file__).resolve().parents[1]
repo=root.parents[2]
source=repo.parent/'work/tbox-x-remote-runtime-20261117'
manifest=json.loads((source/'manifest.json').read_text())
if manifest['source_sha256']!='7dd21a5181d6d7fc544703c3778547d74735bf48e77237fa548cd46a17c561f8':raise SystemExit('Unexpected owner MPU source')
if manifest['missing_libraries']:raise SystemExit('Incomplete runtime extraction')
selected=[]
for f in manifest['files']:
 if not f['output'].startswith('lib/'):continue
 # Keep factory libc/loader; carry the matching vendor closure and Boost only.
 if f['image']!='appfwk.ubi' and not Path(f['output']).name.startswith('libboost_'):continue
 p=source/'runtime'/f['output']
 if hashlib.sha256(p.read_bytes()).hexdigest()!=f['sha256']:raise SystemExit('Runtime integrity failure: '+f['output'])
 selected.append((p,f))
dest=root/'telemetry-payload/native-libs.tar.gz'
with tarfile.open(dest,'w:gz') as t:
 for p,f in selected:t.add(p,arcname=f['output'])
(root/'telemetry-payload/native-libs-source.json').write_text(json.dumps({'source_sha256':manifest['source_sha256'],'files':[f for p,f in selected]},indent=2)+'\n')
print('Packaged',len(selected),'native dependencies')
