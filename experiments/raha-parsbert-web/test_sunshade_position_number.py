import unittest
from numeric_grounding import ground_numbers
from validation import consistency_reason
class SunshadePositionNumberTest(unittest.TestCase):
 def test_explicit_percent_replaces_wrong_prediction(self):
  a='SET_SUNSHADE_POSITION';schema={'allowed':{a+'|position':{'position':[str(n) for n in range(101)]}}}
  for text,n in [('پرده سانروف رو سی درصد باز کن',30),('پرده سانروف رو تا نصف باز کن',50)]:
   p,keys,e=ground_numbers(text,a,{'position':90},schema);self.assertIsNone(e);self.assertEqual(p['position'],n);self.assertEqual(keys,{'position'})
  for text in ['پرده سانروف رو صد و بیست درصد باز کن','پرده سانروف رو سی دقیقه باز کن']:
   self.assertIsNotNone(ground_numbers(text,a,{'position':90},schema)[2])
 def test_mismatch_is_detected(self):
  self.assertIsNotNone(consistency_reason('پرده سانروف رو سی درصد باز کن','SET_SUNSHADE_POSITION',{'position':90}))
if __name__=='__main__':unittest.main()
