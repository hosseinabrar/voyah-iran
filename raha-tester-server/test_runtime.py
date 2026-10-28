import json,unittest
from pathlib import Path
from runtime import Runtime

class SnapshotTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        here=Path(__file__).resolve().parent
        if not (here/'bundle/model.onnx').exists():raise unittest.SkipTest('Prepare the snapshot first')
        cls.runtime=Runtime(here/'bundle')
        fixtures=here/'bundle/parity-fixtures.json'
        if not fixtures.exists():
            # Older snapshots predate bundled fixtures. Never compare them with
            # scores from a differently trained model in the live Android tree.
            manifest=json.loads((here.parent/'voyah-iran/raha-runtime/src/main/assets/raha-intent/manifest.json').read_text())
            if manifest['model_sha256']!=cls.runtime.version:
                raise unittest.SkipTest('Snapshot and current Android model differ; prepare a new snapshot with matching fixtures')
            fixtures=here.parent/'voyah-iran/raha-core/src/test/resources/intent-tokenizer-parity.json'
        cls.rows=json.loads(fixtures.read_text())
    def test_all_token_ids_match_android(self):
        for row in self.rows:
            self.assertEqual(self.runtime.tokenizer.encode(row['text']).ids,row['ids'],row['text'])
    def test_all_winning_actions_match_verified_export(self):
        for row in self.rows:
            result=self.runtime.predict(row['text'])
            self.assertEqual(result['prediction']['label'],row['expected']['action'],row['text'])
            self.assertAlmostEqual(result['prediction']['score'],row['expected']['score'],delta=.0002,msg=row['text'])

if __name__=='__main__':unittest.main()
