"""Read-only full regression for conditional/state clause fixes."""
import asyncio,json,hashlib,csv
from pathlib import Path
import server,project_sync
from teach_unseen_rounds import evaluate,metrics
from teach_batches import assess
from teach_remaining import evaluate as old_evaluate,summary as old_summary
P=Path('artifacts/validator-320-fix-final');P.mkdir(exist_ok=True)
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  sha=hashlib.sha256(server.engine['trainer'].path.read_bytes()).hexdigest()
  rows=json.loads(Path('artifacts/unseen-rounds-training/samples.json').read_text())
  for i in range(6,16):rows+=json.loads(Path(f'regression-contracts/mixed-unseen-round{i:02}/samples.json').read_text())
  before=json.loads(Path('artifacts/mixed-unseen-round15/prior14000.json').read_text())+json.loads(Path('artifacts/mixed-unseen-round15/final.json').read_text());b={r['text']:r for r in before}
  results=evaluate(rows);print('RECENT',metrics(results),flush=True)
  strict=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in ['batch01','batch02']],[])
  noisy=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in [f'batch{i:02}' for i in range(3,11)]+['batch_remaining']],[])
  old=assess(strict);older=old_evaluate(noisy)
  assert hashlib.sha256(server.engine['trainer'].path.read_bytes()).hexdigest()==sha
  report=dict(model_sha256=sha,before=metrics(before),after=metrics(results),strict=sum(r['passed'] for r in old),older=old_summary(older),rescued=[r['text'] for r in results if not b[r['text']]['passed'] and r['passed']],regressions=[r['text'] for r in results if b[r['text']]['passed'] and not r['passed']],new_wrong=[r['text'] for r in results if r['unsafe'] and not b[r['text']]['unsafe']],notes='No model training. Current checkpoint and threshold unchanged. Python/Kotlin validation-only clause normalization, original transcript and model input preserved.')
  for n,x in [('report',report),('results',results),('prior1200',old),('prior7540',older)]: (P/(n+'.json')).write_text(json.dumps(x,ensure_ascii=False,indent=2))
  with (P/'rescued.csv').open('w',encoding='utf-8-sig',newline='') as f:
   w=csv.writer(f);w.writerow(['text','action','parameters','score','before_reason','after_reason','passed'])
   for r in results:
    if not b[r['text']]['passed']:w.writerow([r['text'],r['action'],json.dumps(r['parameters'],ensure_ascii=False),r['score'],b[r['text']]['reason'],r['reason'],r['passed']])
  print('DONE',json.dumps({k:(len(report[k]) if isinstance(report[k],list) else report[k]) for k in ['after','strict','older','rescued','regressions','new_wrong']},ensure_ascii=False),flush=True)
asyncio.run(main())
