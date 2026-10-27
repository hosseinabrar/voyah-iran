import json
import re
import unittest
from pathlib import Path
import torch
from transformers import AutoTokenizer
from full_model import FullModel,frame
from presentation import titles
from data import normalize
from evaluate_full import EXTRA
from validation import explicit_numbers,consistency_reason

ROOT=Path(__file__).resolve().parent

class FullDataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.data=json.loads((ROOT/'dataset.json').read_text())

    def test_all_production_actions_covered(self):
        source=(ROOT.parents[1]/'voyah-iran/raha-core/src/main/kotlin/com/abrar/raha/RahaCommandRecognizer.kt').read_text()
        enum=source.split('enum class RahaAction {',1)[1].split('}',1)[0]
        expected=set(re.findall(r'\b[A-Z][A-Z_]+\b',enum))
        observed={r['action'] for r in self.data['train']}
        self.assertEqual(expected-observed,set())
        self.assertTrue({'CALL_CONTACT','CONFIRM_CALL','CANCEL_DIALOG','UNKNOWN'}<=observed)

    def test_no_eval_exact_duplicates(self):
        seen={normalize(r['text']) for r in self.data['train']}
        tests={normalize(r['text']) for r in self.data['evaluation']}|{normalize(t) for t,a,p in EXTRA}
        self.assertFalse(seen&tests)

    def test_no_contradictory_labels(self):
        seen={}
        for r in self.data['train']:
            key=normalize(r['text']);value=(r['action'],json.dumps(r['parameters'],sort_keys=True))
            self.assertEqual(seen.setdefault(key,value),value)

    def test_quarantined_negatives_not_positive(self):
        bad={r['text'] for r in self.data['provenance']['quarantined_positive_labels']}
        self.assertTrue(bad)
        for r in self.data['train']:
            if r['text'] in bad:self.assertEqual(r['action'],'UNKNOWN')

    def test_titles_do_not_claim_sample_parameters(self):
        self.assertEqual(titles({'SET_SEAT_HEATING':'گرم‌کن صندلی راننده (چپ)'})['SET_SEAT_HEATING'],'گرم‌کن صندلی')
        self.assertEqual(titles({'SET_FRAGRANCE_CHANNEL':'انتخاب رایحه 2'})['SET_FRAGRANCE_CHANNEL'],'انتخاب رایحه')

class FullCheckpointTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(4)
        cls.path=ROOT/'artifacts/full-intent'
        if not (cls.path/'report.json').exists():raise unittest.SkipTest('Training not complete')
        cls.model=FullModel.load(cls.path)
        cls.tokenizer=AutoTokenizer.from_pretrained(cls.path,local_files_only=True)

    def test_cached_prefix_matches_complete_inference(self):
        x=self.tokenizer(['گرمکن رو روشن کن','شیشه شاگرد رو ببند'],padding=True,return_tensors='pt')
        with torch.inference_mode():
            a,sa=self.model(**x)
            b,sb=self.model.from_prefix(self.model.frozen_prefix(x),x['attention_mask'])
        torch.testing.assert_close(a,b,atol=1e-4,rtol=1e-4)

    def test_checkpoint_covers_dataset(self):
        data=json.loads((ROOT/'dataset.json').read_text())
        self.assertEqual(set(self.model.schema['frames']),{frame(r) for r in data['train']})

class GuardTests(unittest.TestCase):
    def test_numbers(self):
        self.assertEqual(explicit_numbers('دما بیست و سه و نیم'),[23.5])
        self.assertEqual(explicit_numbers('دما ۲۳٫۵'),[23.5])

    def test_wrong_number_is_not_silently_executed(self):
        self.assertIsNotNone(consistency_reason('دما رو ۲۴ کن','SET_CABIN_TEMPERATURE',{'degreesC':23}))
        self.assertIsNone(consistency_reason('حفظ برق یک و نیم ساعت','SET_POWER_KEEP_DURATION',{'minutes':90}))

    def test_wrong_polarity_is_rejected(self):
        self.assertIsNotNone(consistency_reason('گرمکن رو خاموش کن','SET_SEAT_HEATING',{'enabled':True}))

if __name__=='__main__':unittest.main()
