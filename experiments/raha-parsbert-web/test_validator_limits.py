import unittest
from numeric_grounding import ground_numbers
from validation import consistency_reason
class LimitGuardTest(unittest.TestCase):
 def apply(self,text,action,key,value,allowed):
  return ground_numbers(text,action,{key:value},{'allowed':{action+'|'+key:{key:[str(v) for v in allowed]}}})
 def test_domain_specific_decrease(self):
  for t,a in [('صدا رو دو پله آروم ترش کن','ADJUST_AUDIO_VOLUME'),('آهنگ رو دو پله آهسته تر پخش کن','ADJUST_AUDIO_VOLUME'),('نور داخل رو دو درجه ملایم تر کن','ADJUST_AMBIENT_LIGHT_BRIGHTNESS')]:
   p,_,e=self.apply(t,a,'delta',2,[-2,2]);self.assertIsNone(e);self.assertEqual(-2,p['delta'])
  self.assertIsNotNone(self.apply('صدا رو دو پله آروم تر و زیاد کن','ADJUST_AUDIO_VOLUME','delta',2,[-2,2])[2])
  self.assertIsNotNone(self.apply('دما رو دو درجه آروم تر کن','ADJUST_CABIN_TEMPERATURE','delta',2,[-2,2])[2])
 def test_negation_not_nine(self):
  for text in ['بلندی آهنگ دقیقا چهار باشه نه بیشتر','صدا دقیقا چهار باشه نه کمتر']:
   p,_,e=self.apply(text,'SET_AUDIO_VOLUME','level',9,[4,9]);self.assertIsNone(e);self.assertEqual(4,p['level']);self.assertIsNone(consistency_reason(text,'SET_AUDIO_VOLUME',p))
  p,_,e=self.apply('صدا نه درجه باشه','SET_AUDIO_VOLUME','level',4,[4,9]);self.assertIsNone(e);self.assertEqual(9,p['level'])
  self.assertIsNotNone(self.apply('صدا چهار یا نه باشه','SET_AUDIO_VOLUME','level',4,[4,9])[2])
