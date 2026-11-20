"""Failure-path test; sandboxed paths and mocked systemctl, no device access."""
from pathlib import Path
import os, subprocess, sys, tempfile, unittest

class UninstallTest(unittest.TestCase):
 def test_restore_runs_even_when_service_shutdown_fails(self):
  source=(Path(__file__).resolve().parents[1]/'telemetry-payload/uninstall.sh').read_text()
  with tempfile.TemporaryDirectory() as temp:
   root=Path(temp);base=root/'remote';(base/'bin').mkdir(parents=True)
   (base/'sms-enabled').touch();(base/'messages.json').write_text('retained')
   (root/'systemctl').write_text('#!/bin/sh\nexit 1\n');(root/'systemctl').chmod(0o755)
   (base/'bin/sms-runtime.py').write_text('import sys\nfrom pathlib import Path\nassert sys.argv[1:] == ["--restore"]\nPath(__file__).parent.joinpath("restored").touch()\n')
   script=source.replace('/data/vi-remote',str(base)).replace('/usr/bin/python',sys.executable)
   result=subprocess.run(['sh'],input=script,text=True,capture_output=True,env={**os.environ,'PATH':str(root)+os.pathsep+os.environ['PATH']})
   self.assertNotEqual(result.returncode,0)
   self.assertTrue((base/'bin/restored').exists())
   self.assertFalse((base/'sms-enabled').exists())
   self.assertEqual((base/'messages.json').read_text(),'retained')
   self.assertNotIn('DONE:',result.stdout)

if __name__=='__main__':unittest.main()
