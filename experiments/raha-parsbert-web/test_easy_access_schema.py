import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
import torch
from safetensors.torch import save_file
from easy_access_schema import expand_easy_access
from feedback_training import FeedbackTrainer, frame_loss

class EasyAccessSchemaTest(unittest.TestCase):
    def test_batched_loss_preserves_value_and_gradient(self):
        logits=torch.randn(5,4,requires_grad=True)
        logp=logits.log_softmax(-1)
        old=torch.stack([-logp[0,1],-logp[1,3],-torch.logsumexp(logp[2,[0,2]],0),
                         -torch.logsumexp(logp[3,[0,2]],0),-logp[4,0]]).mean()
        new=frame_loss(logp,[0,1,4],[1,3,0],[([2,3],[0,2])])
        self.assertTrue(torch.allclose(old,new,atol=1e-7))
        self.assertTrue(torch.allclose(torch.autograd.grad(old,logits,retain_graph=True)[0],
                                       torch.autograd.grad(new,logits)[0],atol=1e-7))

    def test_ventilation_slot_expansion_does_not_expand_open_window(self):
        m=self.model()
        m.schema['slots']['window']=['"all"','"driver"']
        m.schema['frames'] += ['OPEN_WINDOW|window','VENT_WINDOW|window']
        m.frame_head=torch.nn.Linear(4,3)
        m.slot_heads['slot_window']=torch.nn.Linear(4,2)
        old=m.slot_heads['slot_window'].weight.detach().clone()
        expand_easy_access(m);expand_easy_access(m)
        self.assertEqual(m.schema['allowed']['OPEN_WINDOW|window']['window'],['"all"','"driver"'])
        self.assertEqual(m.schema['allowed']['VENT_WINDOW|window']['window'],['"all"','"driver"','"sunroof"','"allWithSunroof"'])
        self.assertTrue(torch.equal(old,m.slot_heads['slot_window'].weight[:2]))
        self.assertEqual(m.slot_heads['slot_window'].out_features,4)

    def model(self):
        return SimpleNamespace(schema={'frames':['OLD|enabled'],'slots':{'enabled':['false','true']},'allowed':{},'titles':{}},
            frame_head=torch.nn.Linear(4,1),slot_heads=torch.nn.ModuleDict({'slot_enabled':torch.nn.Linear(4,2)}))

    def test_append_only_idempotent_and_old_feedback_preserved(self):
        m=self.model(); old=m.frame_head.weight.detach().clone()
        state={'frame.'+k:v for k,v in m.frame_head.state_dict().items()}
        state.update({'slots.'+k:v for k,v in m.slot_heads.state_dict().items()})
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'head.safetensors'
            save_file(state,str(path),metadata={'format':'2','frames':json.dumps(m.schema['frames']),'feedback':'[]'})
            expand_easy_access(m);expand_easy_access(m)
            FeedbackTrainer(Path(directory),m,None,checkpoint_path=path)
            self.assertEqual(m.schema['frames'],['OLD|enabled','SET_EASY_ENTRY|enabled','SET_EASY_EXIT|enabled','SET_SCHEDULED_CHARGING|enabled','VENT_SUNROOF|','ADJUST_SUSPENSION_HEIGHT|delta','ADJUST_FORWARD_COLLISION_SENSITIVITY|delta','SET_WIRELESS_CHARGER|enabled','SET_PASSENGER_DISPLAY_POWER|enabled'])
            self.assertEqual(m.schema['allowed']['VENT_SUNROOF|'], {})
            self.assertTrue(torch.equal(old,m.frame_head.weight[:1]))
            self.assertTrue(torch.equal(m.frame_head.bias[1:],torch.tensor([-20.,-20.,-20.,-20.,-20.,-20.,-20.,-20.])))
            m.schema['frames'][0]='WRONG|enabled'
            with self.assertRaises(RuntimeError):FeedbackTrainer(Path(directory),m,None,checkpoint_path=path)

if __name__=='__main__':unittest.main()
