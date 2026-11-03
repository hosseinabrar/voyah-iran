import unittest,json
from numeric_grounding import ground_numbers
from validation import consistency_reason
class PracticeGuardTest(unittest.TestCase):
 def delta(self,text,action='ADJUST_AUDIO_VOLUME',value=-1):
  extra={'zone':'front'} if action=='ADJUST_CABIN_TEMPERATURE' else {'channel':'media'}
  params=dict(extra,delta=value);schema={'allowed':{action+'|'+','.join(sorted(params)):{'delta':[str(i) for i in range(-16,17) if i]}}}
  return ground_numbers(text,action,params,schema)
 def test_explicit_change_forms(self):
  for text,value in [('صدا رو دو پله کمش کن',-2),('صدا رو دو پله زیادش کن',2),('صدا رو دو پله بالاتر ببر',2),('صدا دو پله پایین تر باشه',-2),('به صدا دو پله اضافه کن',2),('صدا رو دو پله بلندترش کن',2),('آهنگ رو کم می شنوم یک درجه بلندترش کن',1)]:
   p,_,e=self.delta(text);self.assertIsNone(e,text);self.assertEqual(p['delta'],value,text)
  for word,v in [('گرم ترش',3),('سردترش',-3),('خنک ترش',-3)]:
   p,_,e=self.delta('اتاق رو سه درجه '+word+' کن','ADJUST_CABIN_TEMPERATURE');self.assertIsNone(e);self.assertEqual(p['delta'],v)
 def test_ambiguous_directions_still_reject(self):
  for t in ['صدا رو دو پله کم و زیاد کن','صدا رو دو پله تغییر بده','صدا رو منفی دو پله زیاد کن','صدا رو دو یا سه پله زیاد کن']:
   self.assertIsNotNone(self.delta(t)[2],t)
 def test_negated_power(self):
  for t in ['سردکن روشن نمونه خاموشش کن','گرمکن روشن نباشه خاموش کن']:
   self.assertIsNone(consistency_reason(t,'SET_SEAT_HEATING',{'enabled':False}))
   self.assertIsNotNone(consistency_reason(t,'SET_SEAT_HEATING',{'enabled':True}))
  for t in ['گرمکن هم روشن هم خاموش باشه','گرمکن روشن نمونه ولی روشن کن']:
   for b in [False,True]:self.assertIsNotNone(consistency_reason(t,'SET_SEAT_HEATING',{'enabled':b}))
if __name__=='__main__':unittest.main()
