import json
import unittest
from pathlib import Path
from full_model import frame,encode_value

ROOT=Path(__file__).resolve().parent

class PracticeDataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=json.loads((ROOT/'artifacts/practice/dataset.json').read_text())
        cls.schema=json.loads((ROOT/'artifacts/full-intent/schema.json').read_text())

    def test_all_command_families(self):
        self.assertEqual(set(self.data['titles']),{r['action'] for r in self.data['train']})

    def test_disjoint_splits(self):
        texts=[{r['text'] for r in self.data[s]} for s in ['train','dev','final']]
        for i in range(3):
            for j in range(i):self.assertFalse(texts[i]&texts[j])

    def test_contracts_and_no_conflicts(self):
        for split in ['train','dev','final']:
            seen={}
            for row in self.data[split]:
                self.assertIn(frame(row),self.schema['frames'])
                for k,v in row['parameters'].items():self.assertIn(encode_value(v),self.schema['slots'][k])
                if row['text'] in seen:self.assertEqual(seen[row['text']],(row['action'],row['parameters']))
                seen[row['text']]=(row['action'],row['parameters'])

    def test_direction_and_value_contrasts(self):
        rows=self.data['train']
        for action,key,values in [('SET_CHILD_LOCK','side',{'left','right','both'}),('SET_WINDOW_POSITION','window',{'driver','passenger','rearLeft','rearRight','all','front','rear'}),('SET_CABIN_TEMPERATURE','zone',{'driver','passenger','front'})]:
            self.assertEqual(values,{r['parameters'][key] for r in rows if r['action']==action})

if __name__=='__main__':unittest.main()
