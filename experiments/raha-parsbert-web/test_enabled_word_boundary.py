import unittest
from validation import consistency_reason
class EnabledWordBoundaryTest(unittest.TestCase):
 def test_brightness_is_not_enable(self):
  self.assertIsNone(consistency_reason('چراغ روشنایی روز رو خاموش کن','SET_DAYTIME_RUNNING_LIGHTS',{'enabled':False}))
 def test_conflicting_or_wrong_enable_rejected(self):
  for text,value in [('چراغ رو روشن و خاموش کن',True),('چراغ رو روشن کن',False),('چراغ رو خاموش کن',True),('روشن، کن',False)]:
   self.assertIsNotNone(consistency_reason(text,'SET_DAYTIME_RUNNING_LIGHTS',{'enabled':value}))
if __name__=='__main__':unittest.main()
