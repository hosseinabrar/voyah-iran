import unittest
from numeric_grounding import ground_numbers
from validation import consistency_reason
class BatchNumericTests(unittest.TestCase):
 def test_relative_position(self):
  for action in ['ADJUST_WINDOW_POSITION','ADJUST_SUNROOF_POSITION','ADJUST_SUNSHADE_POSITION']:
   schema={'allowed':{action+'|deltaPercent':{'deltaPercent':['-10','10']}}}
   for text,delta in [('سانروف رو ده درصد بیشتر بسته کن',-10),('سانروف رو ده درصد بیشتر باز کن',10),('سانروف رو یک پله بسته کن',-10),('سانروف رو یک پله باز کن',10)]:
    p,k,e=ground_numbers(text,action,{'deltaPercent':delta*9},schema);self.assertIsNone(e);self.assertEqual(p['deltaPercent'],delta);self.assertIsNone(consistency_reason(text,action,p))
   self.assertIsNotNone(ground_numbers('سانروف رو ده درصد باز و بسته کن',action,{'deltaPercent':10},schema)[2])
 def test_duration_with_named_intensity(self):
  action='CONFIGURE_FRAGRANCE';p={'channel':1,'durationMinutes':60,'enabled':True,'intensity':'high'}
  schema={'allowed':{action+'|channel,durationMinutes,enabled,intensity':{'durationMinutes':['30','60']}}}
  out,keys,error=ground_numbers('رایحه بامبو رو نیم ساعت با شدت زیاد روشن کن',action,p,schema)
  self.assertIsNone(error);self.assertEqual(out,{**p,'durationMinutes':30});self.assertEqual(keys,{'durationMinutes'})
if __name__=='__main__':unittest.main()
