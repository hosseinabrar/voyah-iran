import asyncio,json,torch,hashlib
from pathlib import Path
import server,project_sync
from teach_batches import train,assess
from teach_remaining import evaluate as old_evaluate,summary as old_summary
from teach_unseen_rounds import evaluate,metrics
P=Path('artifacts/mixed-unseen-round07')
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];original=tr.features;cache={}
  def features(texts):
   missing=list(dict.fromkeys(t for t in texts if t not in cache))
   if missing:cache.update(zip(missing,original(missing)))
   return torch.stack([cache[t] for t in texts])
  tr.features=features
  failed_texts={r['text'] for r in json.loads((P/'prior7540.json').read_text()) if not r['passed']}
  targets=[r for n in [f'batch{i:02}' for i in range(3,11)]+['batch_remaining'] for r in json.loads(Path('regression-contracts',n,'samples.json').read_text()) if r['text'] in failed_texts]
  for i in range(4):
   results=old_evaluate(targets);failed=[r for r in results if not r['passed']];print('REPAIR',i,[(r['text'],r['passed'],r['reason']) for r in results],flush=True)
   if not failed:break
   train(tr,[dict(text=r['text'],action=r['action'],parameters=r['parameters']) for r in targets],failed[0],steps=400)
  rows=json.loads((P/'samples.json').read_text());final=evaluate(rows)
  priorrows=json.loads(Path('artifacts/unseen-rounds-training/samples.json').read_text())+json.loads(Path('regression-contracts/mixed-unseen-round06/samples.json').read_text());prior=evaluate(priorrows)
  strict=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in ['batch01','batch02']],[])
  noisy=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in [f'batch{i:02}' for i in range(3,11)]+['batch_remaining']],[])
  old=assess(strict);older=old_evaluate(noisy)
  report=json.loads((P/'training-report.json').read_text());report.update(after=metrics(final),prior6000=metrics(prior),prior1200=sum(r['passed'] for r in old),prior7540=old_summary(older),repair_targets=[r['text'] for r in targets],model_sha256=hashlib.sha256(tr.path.read_bytes()).hexdigest())
  for n,v in [('final',final),('prior6000',prior),('prior1200',old),('prior7540',older),('training-report',report)]: (P/(n+'.json')).write_text(json.dumps(v,ensure_ascii=False,indent=2))
  print('DONE',json.dumps({k:report[k] for k in ['after','prior6000','prior1200','prior7540']},ensure_ascii=False),flush=True)
asyncio.run(main())
