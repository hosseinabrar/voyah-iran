import unittest
from validation import consistency_reason
class MirrorConsistencyTest(unittest.TestCase):
 def test_explicit_direction(self):
  for text,fold in [('آینه رو باز کن',False),('آینه همراه باز بشه',False),('آینه برای دیدن عقب باز باشه',False),('آینه رو جمع کن',True),('آینه راننده تا بشه',True),('آینه رو باز کن جمع نشه',False)]:
   self.assertIsNone(consistency_reason(text,'SET_MIRROR_FOLD',{'side':'driver','folded':fold}),text)
   self.assertIsNotNone(consistency_reason(text,'SET_MIRROR_FOLD',{'side':'driver','folded':not fold}),text)
 def test_conflicting_commands(self):
  for fold in [True,False]:self.assertIsNotNone(consistency_reason('آینه رو باز کن و جمع کن','SET_MIRROR_FOLD',{'folded':fold}))
 def test_descriptions_not_commands(self):
  self.assertIsNone(consistency_reason('آینه جمع شده رو بازش کن','SET_MIRROR_FOLD',{'folded':False}))
  self.assertIsNone(consistency_reason('آینه باز نمونه جمعش کن','SET_MIRROR_FOLD',{'folded':True}))
