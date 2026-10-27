import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
import torch
from safetensors.torch import save_file
from feedback_training import FeedbackTrainer

class SnapshotTests(unittest.TestCase):
    def test_explicit_snapshot_wins_over_stale_default(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            old=root/'artifacts/android/feedback-snapshot.safetensors'
            selected=root/'unique-job/feedback-snapshot.safetensors'
            model=SimpleNamespace(schema={'frames':['TEST|']},frame_head=torch.nn.Linear(2,1))
            for path,value in [(old,1.0),(selected,7.0)]:
                path.parent.mkdir(parents=True,exist_ok=True)
                save_file({'weight':torch.full((1,2),value),'bias':torch.full((1,),value)},str(path),
                          metadata={'frames':json.dumps(['TEST|']),'feedback':json.dumps([{'version':value}])})
            trainer=FeedbackTrainer(root,model,None,checkpoint_name='android/feedback-snapshot',checkpoint_path=selected)
            self.assertEqual(trainer.rows,[{'version':7.0}])
            self.assertTrue(torch.equal(model.frame_head.weight,torch.full((1,2),7.0)))
            self.assertEqual(trainer.path,selected)

if __name__=='__main__':unittest.main()
