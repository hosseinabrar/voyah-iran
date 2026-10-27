import unittest,json,torch
from torch import nn
from types import SimpleNamespace
from temperature_schema import expand_temperature
from validation import consistency_reason

class TemperatureTests(unittest.TestCase):
    def test_expansion_preserves_old_weights_and_indices(self):
        model=SimpleNamespace(schema={'slots':{'degreesC':['16.0','20.0'],'delta':['-1','1']},'allowed':{}},slot_heads=nn.ModuleDict({'slot_degreesC':nn.Linear(3,2),'slot_delta':nn.Linear(3,2)}))
        weights=model.slot_heads['slot_delta'].weight.clone()
        expand_temperature(model)
        self.assertEqual(model.schema['slots']['delta'][:2],['-1','1'])
        self.assertTrue(torch.equal(weights,model.slot_heads['slot_delta'].weight[:2]))
        self.assertEqual([json.loads(v) for v in model.schema['allowed']['SET_CABIN_TEMPERATURE|degreesC,zone']['degreesC']],[i/2 for i in range(32,65)])
        self.assertEqual([json.loads(v) for v in model.schema['allowed']['ADJUST_CABIN_TEMPERATURE|delta,zone']['delta']],[i/2 for i in range(-32,33) if i])
        expand_temperature(model)
        self.assertEqual(len(model.schema['slots']['delta']),64)

    def test_sign_and_bounds(self):
        self.assertIsNotNone(consistency_reason('دما رو ۶ درجه کم کن','ADJUST_CABIN_TEMPERATURE',{'delta':6}))
        self.assertIsNone(consistency_reason('دما رو ۶ درجه کم کن','ADJUST_CABIN_TEMPERATURE',{'delta':-6}))
        self.assertIsNotNone(consistency_reason('دما رو روی ۳۳ بذار','SET_CABIN_TEMPERATURE',{'degreesC':32}))
        self.assertIsNone(consistency_reason('دما رو روی سی و دو بذار','SET_CABIN_TEMPERATURE',{'degreesC':32}))

if __name__=='__main__':unittest.main()
