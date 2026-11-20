"""Offline checks only: firmware bytes never executed and no device accessed."""
import hashlib, importlib.util, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sms_runtime',ROOT/'telemetry-payload/sms-runtime.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class SMSRuntimeTests(unittest.TestCase):
 def test_reject_unknown(self):
  with self.assertRaises(ValueError):m.patch(b'unknown firmware')
 def test_verified_inputs(self):
  work=ROOT.parents[2].parent/'work'
  files=[work/'tbox-firmware-audit/extracted/appfwk.ubi/usr/bin/rild',work/'tbox-firmware-audit/reference-only-user-mpu/old-extracted/usr/bin/rild',work/'tbox-firmware-audit/voyah-tweaks-2.2.2vtr1.9/extracted/appfwk.ubi/usr/bin/rild',work/'tbox-enablement-20261108/radio-audit/binaries/hrild',ROOT/'payload/hrild-remote',ROOT.parent/'TBox-antenna/payload/hrild-signal']
  for p in files:
   with self.subTest(profile=str(p)):
    if not p.exists():self.skipTest('Private firmware audit archive unavailable')
    source=p.read_bytes();result=m.patch(source)
    self.assertEqual(len(source),len(result));self.assertIn(m.digest(result),m.FINAL);self.assertEqual(m.patch(result),result)
    if p.name=='rild':self.assertLessEqual(sum(a!=b for a,b in zip(source,result)),8)
    corrupt=bytearray(source);corrupt[-1]^=1
    with self.assertRaises(ValueError):m.patch(bytes(corrupt))
 def test_installed_antenna_upgrade_preserves_customizations(self):
  with tempfile.TemporaryDirectory() as td:
   root=Path(td);original=b'#!/bin/sh\nBASE=/usr/local/lib/TBox-antenna\n. "$BASE/common.sh"\n# owner customization\necho done\n'
   for name in ('connect.sh','rollback.sh'):(root/name).write_bytes(original)
   (root/'SHA256SUMS').write_text(''.join(m.digest(original)+'  '+n+'\n' for n in ('connect.sh','rollback.sh')))
   m.antenna_hooks(str(root));once=(root/'connect.sh').read_bytes();m.antenna_hooks(str(root));self.assertEqual(once,(root/'connect.sh').read_bytes())
   for n in ('connect.sh','rollback.sh'):
    self.assertEqual((root/(n+'.before-vi-sms')).read_bytes(),original);self.assertIn(b'# owner customization',(root/n).read_bytes())
   for line in (root/'SHA256SUMS').read_text().splitlines():
    h,n=line.split();self.assertEqual(h,m.digest((root/n).read_bytes()))
 def test_unknown_antenna_layout_unchanged(self):
  with tempfile.TemporaryDirectory() as td:
   p=Path(td)/'connect.sh';p.write_text('custom')
   with self.assertRaises(ValueError):m.antenna_hooks(td)
   self.assertEqual(p.read_text(),'custom')
if __name__=='__main__':unittest.main()
