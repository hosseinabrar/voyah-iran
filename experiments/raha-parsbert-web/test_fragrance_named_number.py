import unittest
from validation import consistency_reason
from numeric_grounding import ground_numbers
class FragranceNamedNumberTest(unittest.TestCase):
 def test_name_is_not_quantity(self):
  for name in ['گل صد تومنی','صدتومنی','گل صد تومانی']:
   self.assertIsNone(consistency_reason(f'عطر {name} رو انتخاب کن','SET_FRAGRANCE_CHANNEL',{'channel':2}))
 def test_actual_quantity_still_checked(self):
  self.assertIsNotNone(consistency_reason('عطر گل صد تومنی شماره سه رو انتخاب کن','SET_FRAGRANCE_CHANNEL',{'channel':2}))
  a='CONFIGURE_FRAGRANCE';p={'channel':2,'enabled':True,'durationMinutes':60,'intensity':'high'}
  schema={'allowed':{a+'|channel,durationMinutes,enabled,intensity':{'durationMinutes':['30','60']}}}
  out,keys,err=ground_numbers('گل صد تومنی رو برای سی دقیقه فعال کن',a,p,schema)
  self.assertIsNone(err);self.assertEqual(out['durationMinutes'],30)
 def test_unrelated_numbers_unchanged(self):
  self.assertIsNotNone(consistency_reason('صدا رو روی صد بذار','SET_AUDIO_VOLUME',{'level':2,'channel':'media'}))
if __name__=='__main__':unittest.main()
