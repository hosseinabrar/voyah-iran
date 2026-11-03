import asyncio,json,hashlib,shutil,sqlite3,torch
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from teach_unseen_rounds import evaluate,metrics,semantic,needs
from teach_batches import train,assess
from teach_remaining import evaluate as old_evaluate,summary as old_summary
OUT=Path('artifacts/mixed-unseen-round10')
def dump(n,x):(OUT/(n+'.json')).write_text(json.dumps(x,ensure_ascii=False,indent=2))
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];rows=json.loads((OUT/'samples.json').read_text())
  shutil.copy2(tr.path,OUT/'before.safetensors')
  with ef.connect() as c:
   with sqlite3.connect(OUT/'before-evidence.sqlite3') as d:c.backup(d)
  # Frozen encoder: cache exact text embeddings only for this offline run.
  original=tr.features;cache={}
  def features(texts):
   missing=list(dict.fromkeys(t for t in texts if t not in cache))
   if missing:
    vals=original(missing)
    cache.update({t:v for t,v in zip(missing,vals)})
   return torch.stack([cache[t] for t in texts])
  tr.features=features
  learning=[]
  for r in rows:
   p=dict(r['parameters']);f=r['action']+'|'+','.join(sorted(p))
   for k,v in p.items():p[k]=next(json.loads(x) for x in tr.model.schema['allowed'][f][k] if json.loads(x)==v and isinstance(json.loads(x),bool)==isinstance(v,bool))
   learning.append(dict(text=r['text'],action=r['action'],parameters=p))
  intent=set();sem=set();history=[]
  for step in range(3):
   res=evaluate(rows);dump('latest',res);history.append(metrics(res));print('ROUND',step,history[-1],flush=True)
   semantic(res,sem);bad=[r for r in res if needs(r)]
   dump('progress',dict(intent=sorted(intent),semantic=sorted(sem),history=history))
   if not bad or step==2:break
   intent.update(r['text'] for r in bad);w=min(bad,key=lambda r:(not r['unsafe'],r['score']));focus=next(r for r in learning if r['text']==w['text']);train(tr,learning,focus,steps=1000)
  priorrows=json.loads(Path('artifacts/unseen-rounds-training/samples.json').read_text())+json.loads(Path('regression-contracts/mixed-unseen-round06/samples.json').read_text())+json.loads(Path('regression-contracts/mixed-unseen-round07/samples.json').read_text())+json.loads(Path('regression-contracts/mixed-unseen-round08/samples.json').read_text())+json.loads(Path('regression-contracts/mixed-unseen-round09/samples.json').read_text())
  strict=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in ['batch01','batch02']],[])
  noisy=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in [f'batch{i:02}' for i in range(3,11)]+['batch_remaining']],[])
  prior=evaluate(priorrows);old=assess(strict);older=old_evaluate(noisy)
  repairs=[]
  for turn in range(3):
   failed=[r for r in old+older if not r['passed']]
   print('REGRESSION',turn,'strict',sum(r['passed'] for r in old),'older',sum(r['passed'] for r in older),flush=True)
   if not failed:break
   targets=[]
   for r in failed:
    a='UNKNOWN' if r.get('policy') in ['reject','correct_or_reject'] or r.get('valid') is False else r['action']
    p={} if a=='UNKNOWN' else dict(r['parameters']);f=a+'|'+','.join(sorted(p))
    for k,v in p.items():p[k]=next(json.loads(x) for x in tr.model.schema['allowed'][f][k] if json.loads(x)==v and isinstance(json.loads(x),bool)==isinstance(v,bool))
    targets.append(dict(text=r['text'],action=a,parameters=p))
   repairs.extend(r['text'] for r in targets)
   train(tr,learning+targets,targets[0],steps=400)
   old=assess(strict);older=old_evaluate(noisy)
  prior=evaluate(priorrows)
  dump('repair-targets',repairs)
  dump('prior9000',prior);dump('prior1200',old);dump('prior7540',older)
  final=evaluate(rows);semantic(final,sem);final=evaluate(rows);dump('final',final)
  report=dict(before=metrics(json.loads((OUT/'baseline.json').read_text())),after=metrics(final),history=history,intent_examples=sorted(intent),semantic_examples=sorted(sem),prior9000=metrics(prior),prior1200=sum(r['passed'] for r in old),prior7540=old_summary(older),model_sha256=hashlib.sha256(tr.path.read_bytes()).hexdigest(),notes='In-sample after teaching. Exact-case semantic feedback is web-only. Original baseline unchanged. Text-only, no vehicle execution.')
  dump('training-report',report);print('DONE',json.dumps({k:report[k] for k in ['after','prior9000','prior1200','prior7540']},ensure_ascii=False),flush=True)
asyncio.run(main())
