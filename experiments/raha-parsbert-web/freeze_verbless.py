"""Preserve the before/after evidence for the targeted elliptical-command round."""
import csv,json,shutil
from pathlib import Path
source=Path('artifacts/verbless-round17');dest=Path('regression-contracts/verbless-round17');dest.mkdir(exist_ok=True)
for n in ['samples','baseline','report','final','training-report']:
 shutil.copy2(source/(n+'.json'),dest/(n+'.json'))
before={r['text']:r for r in json.loads((source/'baseline.json').read_text())}
with (dest/'results.csv').open('w',newline='',encoding='utf-8-sig') as f:
 w=csv.writer(f);w.writerow(['text','policy','expected_action','expected_parameters','previously_seen','before_passed','before_action','before_parameters','before_score','before_reason','after_passed','after_action','after_parameters','after_score','after_reason'])
 for r in json.loads((source/'final.json').read_text()):
  b=before[r['text']];w.writerow([r['text'],r['policy'],r['action'],json.dumps(r['parameters'],ensure_ascii=False),r['previously_seen'],b['passed'],b['predicted'],json.dumps(b['slots'],ensure_ascii=False),b['score'],b['reason'],r['passed'],r['predicted'],json.dumps(r['slots'],ensure_ascii=False),r['score'],r['reason']])
print(dest/'results.csv')
