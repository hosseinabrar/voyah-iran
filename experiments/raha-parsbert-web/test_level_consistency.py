import unittest,json
from types import SimpleNamespace
import torch
from easy_access_schema import expand_easy_access
from validation import consistency_reason
class LevelConsistencyTest(unittest.TestCase):
 def test_reserve_expansion_preserves_learned_indices(self):
  frames=['SET_BATTERY_RESERVE_TARGET|percent','SET_ENERGY_STRATEGY|reservePercent,strategy','SET_LANE_SUPPORT_MODE|sensitivity','SET_DRIVER_MONITORING|sensitivity']
  m=SimpleNamespace(schema={'frames':frames[:],'slots':{'percent':['25','50'],'reservePercent':['25','50']},'allowed':{f:{} for f in frames},'titles':{}},frame_head=torch.nn.Linear(3,4),slot_heads=torch.nn.ModuleDict({'slot_percent':torch.nn.Linear(3,2),'slot_reservePercent':torch.nn.Linear(3,2)}))
  old=m.slot_heads['slot_reservePercent'].weight.detach().clone()
  expand_easy_access(m);expand_easy_access(m)
  self.assertEqual(m.schema['slots']['reservePercent'][:2],['25','50'])
  self.assertTrue(torch.equal(old,m.slot_heads['slot_reservePercent'].weight[:2]))
  for f,k in [(frames[0],'percent'),(frames[1],'reservePercent')]:
   self.assertEqual([json.loads(x) for x in m.schema['allowed'][f][k]],list(range(25,81,5)))
  for f in frames[2:]: self.assertEqual(m.schema['allowed'][f]['sensitivity'],['"min"','"max"'])
 def test_nonexistent_middle_sensitivity_is_rejected(self):
  for action in ['SET_LANE_SUPPORT_MODE','SET_DRIVER_MONITORING']:
   self.assertIsNotNone(consistency_reason('حساسیت رو روی متوسط بذار',action,{'sensitivity':'max'}))
if __name__=='__main__':unittest.main()
