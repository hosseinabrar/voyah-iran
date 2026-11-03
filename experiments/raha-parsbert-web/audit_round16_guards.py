"""Evaluate final rule improvements without changing checkpoint or feedback."""
import asyncio,json,hashlib,shutil
from pathlib import Path
import server,project_sync
from teach_unseen_rounds import evaluate,metrics
from teach_batches import assess
from teach_remaining import evaluate as old_evaluate,summary as old_summary
P=Path('artifacts/mixed-unseen-round16')
def dump(n,x):(P/(n+'.json')).write_text(json.dumps(x,ensure_ascii=False,indent=2))
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];sha=hashlib.sha256(tr.path.read_bytes()).hexdigest()
  rows=json.loads(Path('artifacts/unseen-rounds-training/samples.json').read_text())
  for i in range(6,16):rows+=json.loads(Path(f'regression-contracts/mixed-unseen-round{i:02}/samples.json').read_text())
  before=json.loads((P/'prior15000.json').read_text())
  prior=evaluate(rows);print('PRIOR',metrics(prior),flush=True)
  final=evaluate(json.loads((P/'samples.json').read_text()));print('FINAL',metrics(final),flush=True)
  strict=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in ['batch01','batch02']],[])
  noisy=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in [f'batch{i:02}' for i in range(3,11)]+['batch_remaining']],[])
  old=assess(strict);older=old_evaluate(noisy)
  assert hashlib.sha256(tr.path.read_bytes()).hexdigest()==sha
  b={r['text']:r for r in before}
  changes=dict(rescued=[r['text'] for r in prior if not b[r['text']]['passed'] and r['passed']],regressions=[r['text'] for r in prior if b[r['text']]['passed'] and not r['passed']],new_wrong=[r['text'] for r in prior if r['unsafe'] and not b[r['text']]['unsafe']])
  report=json.loads((P/'training-report.json').read_text());shutil.copy2(P/'training-report.json',P/'before-final-guard-report.json')
  for n in ['final','prior15000','prior1200','prior7540']:shutil.copy2(P/(n+'.json'),P/('before-final-guard-'+n+'.json'))
  report.update(after=metrics(final),prior15000=metrics(prior),prior1200=sum(r['passed'] for r in old),prior7540=old_summary(older),guard_changes=changes)
  for n,x in [('final',final),('prior15000',prior),('prior1200',old),('prior7540',older),('training-report',report)]:dump(n,x)
  print('DONE',json.dumps(dict(after=report['after'],prior=report['prior15000'],strict=report['prior1200'],older=report['prior7540'],changes={k:len(v) for k,v in changes.items()}),ensure_ascii=False),flush=True)
asyncio.run(main())
