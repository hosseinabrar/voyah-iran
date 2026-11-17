"""Rebuild the private runtime from the archived dump; never modifies vehicle libraries."""
raise SystemExit('Blocked: owner-supplied archived OEM runtime has a proven ABI mismatch with live H97X 2.0.7. See firmware/SOURCE-CORRECTION.txt.')
from pathlib import Path
import argparse, hashlib, subprocess, tarfile, json
from elftools.elf.elffile import ELFFile
root=Path(__file__).resolve().parents[1]
repo=Path(__file__).resolve().parents[4]
a=argparse.ArgumentParser();a.add_argument('--strip',required=True);args=a.parse_args()
archive=repo.parent/'work/tbox-firmware-audit/reference-only-user-mpu'
src=archive/'old-extracted';system=archive/'extracted/system.img/usr/lib64'
work=repo.parent/'work/tbox-remote-tools/private-runtime';work.mkdir(parents=True,exist_ok=True)
binary=src/'usr/bin/remotecontrol';b=binary.read_bytes()
assert hashlib.sha256(b).hexdigest()==(root/'payload/remotecontrol.expected').read_text().strip()
pairs=[(b'/mnt/hirain_app/usr/etc/remotecontrol/',b'/run/vi-rc/'),(b'/mnt/hirain_conf/app/remotecontrol/',b'/usr/local/lib/TBox-remote/conf/')]
for name,short in [('control_atoms.xml','atoms.xml'),('control_command.xml','commands.xml'),('control_signal_mapping.xml','signals.xml')]:
 pairs.append((('/usr/etc/remotecontrol/'+name).encode(),('/../../run/rc/'+short).encode()))
for old,new in pairs:
 assert len(new)<=len(old) and b.count(old+b'\0')==1
 b=b.replace(old+b'\0',new+b'\0'*(len(old)-len(new)+1))
(root/'payload/remotecontrol-bundled').write_bytes(b)
def needs(p):
 with p.open('rb') as f:
  e=ELFFile(f);return [t.needed for t in e.get_section_by_name('.dynamic').iter_tags() if t.entry.d_tag=='DT_NEEDED']
q=needs(binary);seen=set();out=[];manifest=[]
while q:
 n=q.pop()
 if n in seen:continue
 seen.add(n);p=src/'usr/lib'/n
 if not p.exists():
  matches=list((src/'usr/lib').glob(n+'*'));p=matches[0] if matches else system/n if n.startswith('libboost_') else None
 if p is None or not p.exists():continue
 data=p.read_bytes();dst=work/n;dst.write_bytes(data)
 subprocess.run([args.strip,'--strip-debug',str(dst)],check=True)
 out.append(dst);q+=needs(dst)
 manifest.append({'file':n,'source':str(p.relative_to(archive)),'sha256':hashlib.sha256(data).hexdigest(),'packaged_sha256':hashlib.sha256(dst.read_bytes()).hexdigest()})
with tarfile.open(root/'payload/oem-libs.tar.gz','w:gz') as t:
 for p in out:t.add(p,arcname=p.name)
with tarfile.open(root/'payload/oem-config.tar.gz','w:gz') as t:
 for p in (src/'usr/etc/remotecontrol').glob('*.xml'):t.add(p,arcname=p.name)
(root/'firmware/bundled-runtime.json').write_text(json.dumps(manifest,indent=2)+'\n')
