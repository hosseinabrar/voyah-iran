import tempfile
import unittest
from pathlib import Path
import torch
from transformers import AutoTokenizer
from full_model import FullModel
from feedback_training import FeedbackTrainer

ROOT=Path(__file__).resolve().parent

class FeedbackTests(unittest.TestCase):
    def test_learning_persistence_and_replacement(self):
        torch.set_num_threads(4)
        model=FullModel.load(ROOT/'artifacts/full-intent')
        tokenizer=AutoTokenizer.from_pretrained(ROOT/'artifacts/full-intent',local_files_only=True)
        original=model.frame_head.weight.detach().clone()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'artifacts').mkdir()
            (root/'dataset.json').symlink_to(ROOT/'dataset.json')
            trainer=FeedbackTrainer(root,model,tokenizer)
            text='لطفا جای بغلی رو گرم کن'
            result=trainer.train(text,'SET_SEAT_HEATING')
            self.assertTrue(result['learned'])
            self.assertFalse(torch.equal(original,model.frame_head.weight))
            expected=model.frame_head.weight.detach().clone()
            with torch.no_grad(): model.frame_head.weight.zero_()
            restored=FeedbackTrainer(root,model,tokenizer)
            self.assertTrue(torch.equal(expected,model.frame_head.weight))
            self.assertEqual(restored.rows[0]['text'],text)
            # Latest correction replaces a contradictory old label, not duplicates it.
            result=trainer.train(text,'SET_SEAT_VENTILATION')
            self.assertTrue(result['learned'])
            self.assertEqual(result['count'],1)
            result=trainer.train('دمای شاگرد بیست و دو باشه','SET_CABIN_TEMPERATURE',{'zone':'passenger','degreesC':22})
            self.assertTrue(result['learned'])
            result=trainer.train('الان نوبت تماس علی است','CALL_CONTACT',{'contact_query':'علی'})
            self.assertTrue(result['learned'])
            from contact_extraction import extract_contact
            self.assertEqual(extract_contact('الان نوبت تماس مریم است',trainer.contact_rules),'مریم')
            slot_weights=model.slot_heads['slot_degreesC'].weight.detach().clone()
            with torch.no_grad():model.slot_heads['slot_degreesC'].weight.zero_()
            restored=FeedbackTrainer(root,model,tokenizer)
            self.assertTrue(torch.equal(slot_weights,model.slot_heads['slot_degreesC'].weight))
            self.assertEqual(extract_contact('الان نوبت تماس مریم است',restored.contact_rules),'مریم')
            self.assertIn('parameters',restored.rows[-2])
            with self.assertRaises(ValueError):trainer.train(text,'SET_CABIN_TEMPERATURE',{'zone':'passenger','degreesC':99})
            with self.assertRaises(ValueError):trainer.train('','SET_SEAT_HEATING')
            with self.assertRaises(ValueError):trainer.train(text,'NOT_AN_ACTION')

if __name__=='__main__':unittest.main()
