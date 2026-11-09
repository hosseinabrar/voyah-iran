import hashlib, os, shutil, subprocess, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

class KitTests(unittest.TestCase):
 def test_shell_syntax(self):
  for f in list((ROOT/'payload').glob('*.sh'))+list(ROOT.glob('*.command'))+list(ROOT.glob('*.sh')):
   subprocess.run(['bash' if f.parent==ROOT else 'sh','-n',str(f)],check=True)
 def test_payload_integrity(self):
  for line in (ROOT/'payload/SHA256SUMS').read_text().splitlines():
   digest,name=line.split('  ',1)
   self.assertEqual(digest,hashlib.sha256((ROOT/'payload'/name).read_bytes()).hexdigest())
 def test_prebuilt_known_hashes(self):
  for name,digest in [('libvendor-ril-test.so','1ec44b63fd7dd2a5d0cc88a06fe82c802f2ec415aff87861611b6ed95c6ef201'),('hrild-signal','0396fb7a3d1cf19866e4d02bb6043590dd433873fc38d3650b8def21e647654a')]:
   self.assertEqual(hashlib.sha256((ROOT/'payload'/name).read_bytes()).hexdigest(),digest)
 def test_at_response_validation(self):
  for reply,expected in [(r's "\r\nOK\r\n" true',0),(r's "\r\n+CME ERROR: 5874\r\n" true',1),(r's "" true',1)]:
   env=dict(os.environ,MOCK_REPLY=reply)
   code='. "$1"; busctl() { printf "%s\\n" "$MOCK_REPLY"; }; at "AT+CFUN?"'
   self.assertEqual(subprocess.run(['sh','-c',code,'sh',str(ROOT/'payload/common.sh')],env=env).returncode,expected)
 def test_ipv4_validation(self):
  for ip,expected in [('10.185.68.114',0),('8.8.8.8',0),('',1),('0.0.0.0',1),('999.1.1.1',1),('127.0.0.1',1)]:
   self.assertEqual(subprocess.run(['sh','-c','. "$1"; valid_ip "$2"','sh',str(ROOT/'payload/common.sh'),ip]).returncode,expected)
 def run_host(self,mode):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);(root/'tools/macos').mkdir(parents=True)
   shutil.copy(ROOT/'host_mac.sh',root)
   adb=root/'tools/macos/adb'
   adb.write_text('''#!/bin/bash
case "$1" in
 start-server) exit 0;;
 devices) echo 'List of devices attached'; [ "$MODE" = none ] || printf 'testbox\\tdevice\\n'; [ "$MODE" != multiple ] || printf 'box2\\tdevice\\n'; exit 0;;
esac
case "$*" in
 *software_versions.xml*) exit 0;;
 *install.sh*) [ "$MODE" != rejected ] || { echo 'ERROR: Wrong version'; exit 1; }; echo 'DONE: Installed';;
 *status.sh*) [ "$MODE" != failure ] && echo SUCCESS || echo FAILED;;
 *'cat /tmp/'*) echo 'DONE: Mock network';;
esac
''');adb.chmod(0o755)
   return subprocess.run(['bash',str(root/'host_mac.sh')],env=dict(os.environ,MODE=mode),capture_output=True,text=True)
 def test_host_success(self):
  r=self.run_host('success');self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertIn('SUCCESS:',r.stdout)
 def test_host_no_device(self): self.assertNotEqual(self.run_host('none').returncode,0)
 def test_host_multiple_devices(self): self.assertNotEqual(self.run_host('multiple').returncode,0)
 def test_host_rejected_firmware(self): self.assertNotEqual(self.run_host('rejected').returncode,0)
 def test_host_network_failure(self): self.assertNotEqual(self.run_host('failure').returncode,0)
 def test_no_host_patching(self):
  for name in ['host_mac.sh','host_win.ps1']:
   s=(ROOT/name).read_text();self.assertNotIn(' pull ',s);self.assertNotIn('python',s.lower())
 def test_platform_payload_parity(self):
  for name in ['host_mac.sh','host_win.ps1']:
   s=(ROOT/name).read_text()
   for entry in ['payload/.','/tmp/TBox-antenna-stage','/usr/local/lib/TBox-antenna/status.sh']:
    self.assertIn(entry,s)
if __name__=='__main__': unittest.main()
