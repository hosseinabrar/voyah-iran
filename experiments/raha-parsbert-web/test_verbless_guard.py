import unittest
from validation import consistency_reason
class VerblessGuardTest(unittest.TestCase):
 def test_explicit_endpoint(self):
  for text,value in [('پرده باز',100),('پرده بسته',0),('رها پرده سقف بسته',0),('لطفا سایبان کاملا باز',100),('پرده سانروف رو کامل بسته',0)]:
   self.assertIsNone(consistency_reason(text,'SET_SUNSHADE_POSITION',{'position':value}),text)
   self.assertIsNotNone(consistency_reason(text,'SET_SUNSHADE_POSITION',{'position':100-value}),text)
 def test_partial_positions_preserved(self):
  for text,value in [('پرده نصفه باز',50),('پرده بیست درصد باز',20),('پرده کمی بازتر',60)]:
   self.assertIsNone(consistency_reason(text,'SET_SUNSHADE_POSITION',{'position':value}),text)
 def test_negative_remains_blocked(self):
  self.assertIsNotNone(consistency_reason('پرده باز نکن','SET_SUNSHADE_POSITION',{'position':100}))
