import json,unittest
from pathlib import Path
from command_evidence import check
from validation import consistency_reason
class CrowdGuardTests(unittest.TestCase):
 def test_shared_android_fixtures(self):
  path=Path(__file__).resolve().parents[2]/'voyah-iran/raha-core/src/test/resources/command-evidence-parity.json'
  for r in json.loads(path.read_text()):
   self.assertEqual(r['allowed'],check(r['text'],r['action'],r['parameters'])['error'] is None,r['text'])
 def test_small_is_not_one_percent(self):
  self.assertIsNone(consistency_reason('یک کم شیشه رو بده پایین','ADJUST_WINDOW_POSITION',{'deltaPercent':-10,'window':'driver'}))
