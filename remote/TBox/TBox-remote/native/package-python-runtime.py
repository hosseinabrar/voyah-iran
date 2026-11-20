"""Private Python runtime; no OEM files are installed into the factory filesystem."""
from pathlib import Path
import gzip,hashlib,tarfile,io
box=Path(__file__).resolve().parents[1];repo=box.parents[2]
source=repo.parent/'work/tbox-mpu-c-preparation-20261113/test-python.tar'
data=source.read_bytes()
assert hashlib.sha256(data).hexdigest()=='c6a0cee8983da3073586c0dd9f2b9b80c55ee903e8ffaa4d852ce7a866e3cab1','Unexpected Python archive'
# OEM runtime omits json. Add the matching unmodified pure-Python standard module.
stdlib=repo.parent/'work/remote-install-vtc-20261120/Python-2.7.16.tgz'
assert hashlib.sha256(stdlib.read_bytes()).hexdigest()=='01da813a3600876f03f46db11cc5c408175e99f03af2ba942ef324389a83bad5','Unexpected CPython source'
out=io.BytesIO()
with tarfile.open(fileobj=out,mode='w') as dest,tarfile.open(fileobj=io.BytesIO(data)) as original,tarfile.open(stdlib) as official:
 for member in original:
  dest.addfile(member,original.extractfile(member) if member.isfile() else None)
 for member in official:
  name=member.name;prefix='Python-2.7.16/Lib/json/'
  if member.isfile() and ((name.startswith(prefix) and '/' not in name[len(prefix):] and name.endswith('.py')) or name=='Python-2.7.16/LICENSE'):
   target='usr/lib64/python2.7/json/'+name[len(prefix):] if name.startswith(prefix) else 'PYTHON-LICENSE.txt'
   body=official.extractfile(member).read();info=tarfile.TarInfo(target);info.size=len(body);info.mode=0o644;dest.addfile(info,io.BytesIO(body))
(box/'telemetry-payload/python-runtime.tar.gz').write_bytes(gzip.compress(out.getvalue(),mtime=0))
