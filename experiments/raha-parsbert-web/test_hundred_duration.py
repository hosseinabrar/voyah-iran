import unittest
from numeric_grounding import ground_numbers,quantities
class HundredDurationTest(unittest.TestCase):
 def test_hundreds_compose(self):
  for words,value in [('صد و بیست',120),('صد و پنجاه',150),('صد و هشتاد',180)]:
   a='SET_POWER_KEEP_DURATION';p={'minutes':30};schema={'allowed':{a+'|minutes':{'minutes':['30','60','90','120','150','180','-1']}}}
   out,_,err=ground_numbers(f'زمان حفظ برق رو {words} دقیقه کن',a,p,schema)
   self.assertIsNone(err);self.assertEqual(out['minutes'],value)
 def test_separate_tens_remain_ambiguous(self):
  self.assertEqual(len(quantities('سی و شصت دقیقه')),2)
 def test_full_number_keeps_ones(self):
  self.assertEqual(quantities('صد و بیست و پنج دقیقه')[0][0],125)
if __name__=='__main__':unittest.main()
