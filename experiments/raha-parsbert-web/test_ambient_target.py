import unittest
from command_evidence import check
class AmbientTargetTests(unittest.TestCase):
 def test_complete_light_target_wins_over_cabin_word(self):
  for target in ['نور داخل کابین','نور داخل اتاق','نور داخل ماشین','نور داخل خودرو','نور داخل','نور کابین','نور اتاق','نورپردازی داخل کابین','نورپردازی کابین']:
   for change in ['زیاد','کم']:
    result=check(target+' رو '+change+' کن','ADJUST_AMBIENT_LIGHT_BRIGHTNESS',{'delta':1 if change=='زیاد' else -1})
    self.assertIsNone(result['error'],target)
    self.assertEqual([t['kind'] for t in result['targets']],['ambient'])
 def test_other_explicit_targets_not_discarded(self):
  self.assertIsNotNone(check('دمای کابین رو زیاد کن','ADJUST_AMBIENT_LIGHT_BRIGHTNESS',{'delta':1})['error'])
  self.assertIsNotNone(check('نور داخل کابین رو زیاد کن','ADJUST_CABIN_TEMPERATURE',{'delta':1,'zone':'front'})['error'])
  self.assertIsNotNone(check('نور داخل کابین و دمای اتاق رو زیاد کن','ADJUST_AMBIENT_LIGHT_BRIGHTNESS',{'delta':1})['error'])
