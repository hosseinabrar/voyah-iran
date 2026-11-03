import unittest,json
from pathlib import Path
from command_clauses import validation_text,blocked_form
from validation import consistency_reason
from command_evidence import check
class ClausesTest(unittest.TestCase):
 def test_courtesy_not_condition(self):
  for t in ['اگه ممکنه کولر رو روشن کن','رها اگر می توانی کولر رو روشن کن','اگه میتونی صندوق رو باز کن']:
   self.assertFalse(blocked_form(t),t)
  for t in ['اگر بارون اومد شیشه رو ببند','اگه ممکنه اگر بارون اومد شیشه رو ببند','چرا کولر رو روشن کنم','اگه ممکنه کولر رو روشن کن؟','کولر رو روشن نکن','شیشه رو نبند']:
   self.assertTrue(blocked_form(t),t)
 def test_corrections_and_transitions(self):
  for t,b in [('کولر رو از حالت فعال دربیار',False),('کولر رو از حالت غیر فعال خارج کن',True),('کولر رو روشن نکن فقط خاموشش کن',False),('کولر رو فعال نکن در عوض غیرفعالش کن',False),('کولر رو خاموش نکن فقط فعالش کن',True),('کولر رو از خاموش به روشن تغییر بده',True),('کولر رو از حالت روشن به حالت خاموش ببر',False)]:
   self.assertFalse(blocked_form(t),t)
   self.assertIsNone(consistency_reason(t,'SET_AC_POWER',{'enabled':b}),t)
   self.assertIsNotNone(consistency_reason(t,'SET_AC_POWER',{'enabled':not b}),t)
   self.assertIsNone(check(t,'SET_AC_POWER',{'enabled':b})['error'],t)
  for t in ['کولر رو هم روشن هم خاموش کن','کولر رو روشن نکن فقط خاموشش کن و روشنش کن','کولر رو از خاموش به روشن تغییر بده ولی خاموش کن']:
   for b in [True,False]:self.assertIsNotNone(consistency_reason(t,'SET_AC_POWER',{'enabled':b}),t)
 def test_all_320_reported_rejections(self):
  rows=json.loads(Path('artifacts/mixed-unseen-round15/prior14000.json').read_text())
  rows=[r for r in rows if r['policy']=='correct' and not r['passed']]
  self.assertEqual(320,len(rows))
  for r in rows:
   self.assertFalse(blocked_form(r['text']),r['text'])
   self.assertIsNone(consistency_reason(r['text'],r['action'],r['parameters']),r['text'])
if __name__=='__main__':unittest.main()
