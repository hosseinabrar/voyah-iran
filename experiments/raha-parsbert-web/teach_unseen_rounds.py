"""Complete the missing feedback step for five independent evaluation rounds.
Preserves blind results. Trains frame/slots and exact-case semantic reviews.
"""
import asyncio,json,shutil,sqlite3,hashlib
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from data import normalize
from teach_batches import train,assess
from teach_remaining import evaluate as old_evaluate,summary as old_summary
OUT=Path('artifacts/unseen-rounds-training');OUT.mkdir(exist_ok=True)
NAMES=['abstract-unseen']+[f'mixed-unseen-round{i:02}' for i in range(2,6)]
def dump(n,x):(OUT/(n+'.json')).write_text(json.dumps(x,ensure_ascii=False,indent=2))
def evaluate(rows):
 out=[]
 for r in rows:
  p=server.predict(r['text']);a=p['prediction']['label'];params=p['slots']
  options=r.get('acceptable_predictions',[dict(action=r['action'],parameters=r['parameters'])])
  exact=any(a==v['action'] and params==v['parameters'] for v in options)
  execute=p['accepted'] and round(p['prediction']['score']*100)==100
  invalid=r['policy']=='reject';unsafe=execute and (invalid or not exact)
  out.append(dict(**r,exact=exact,executed=execute,unsafe=unsafe,passed=not execute if invalid else execute and exact,predicted=a,slots=params,score=p['prediction']['score'],reason=p['reason']))
 return out

def metrics(rs):
 return {k:dict(count=len(q),passed=sum(r['passed'] for r in q),unsafe=sum(r['unsafe'] for r in q),raw_exact=sum(r['exact'] for r in q),rejected=sum(not r['executed'] for r in q)) for k,q in [('all',rs),('positive',[r for r in rs if r['policy']!='reject']),('negative',[r for r in rs if r['policy']=='reject'])]}

def semantic(res,ledger):
 for r in res:
  pairs=[]
  if r['policy']=='correct':pairs=[(v['action'],v['parameters'],True) for v in r['acceptable_predictions']]
  if r['predicted']!='UNKNOWN' and (r['policy']=='reject' or not r['exact']):pairs.append((r['predicted'],r['slots'],False))
  for a,p,allowed in pairs:
   if ef.evaluate(r['text'],a,p)['allowed']!=allowed:
    ef.save(r['text'],a,p,allowed,'آموزش پس از آزمون مستقل: '+('تایید برداشت دقیق' if allowed else 'رد برداشت نادرست'))
    ledger.add(r['text'])

def needs(r):return not r['exact'] or (r['policy']=='correct' and round(r['score']*100)!=100)
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];rows=[]
  for name in NAMES:
   rs=json.loads(Path('regression-contracts',name,'samples.json').read_text())
   for r in rs:r['batch']=name
   rows+=rs
  # Actual executor defaults enabled=true for ambient mode; documented equivalence.
  for r in rows:
   if r['action']=='SET_AMBIENT_LIGHT_MODE':r['acceptable_predictions'].append(dict(action=r['action'],parameters=dict(r['parameters'],enabled=True)))
  assert len(rows)==5000 and len({normalize(r['text']) for r in rows})==5000
  for r in rows:
   if r['action'].startswith('SET_SEAT_') and r['parameters'].get('enabled') is False:
    r['parameters']=dict(r['parameters'],level=0)
    r['acceptable_predictions'].append(dict(action=r['action'],parameters=r['parameters']))
  learning=[]
  for r in rows:
   p=dict(r['parameters']);frame=r['action']+'|'+','.join(sorted(p))
   assert frame in tr.model.schema['frames'],frame
   for k,v in p.items():
    choices=[json.loads(x) for x in tr.model.schema['allowed'][frame][k]]
    same=[x for x in choices if x==v and isinstance(x,bool)==isinstance(v,bool)]
    assert same,(r['text'],k,v)
    p[k]=same[0]
   learning.append(dict(text=r['text'],action=r['action'],parameters=p))
  if not (OUT/'before.safetensors').exists():
   shutil.copy2(tr.path,OUT/'before.safetensors')
   with ef.connect() as c:
    with sqlite3.connect(OUT/'before-evidence.sqlite3') as d:c.backup(d)
  dump('samples',rows)
  intent=set();sem=set();history=[]
  oldstrict=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in ['batch01','batch02']],[])
  oldnoisy=sum([json.loads(Path('regression-contracts',n,'samples.json').read_text()) for n in [f'batch{i:02}' for i in range(3,11)]+['batch_remaining']],[])
  for step in range(7):
   res=evaluate(rows);dump('latest',res);m=metrics(res);history.append(m)
   if not (OUT/'baseline.json').exists():dump('baseline',res)
   print('ROUND',step,json.dumps(m),flush=True)
   semantic(res,sem);bad=[r for r in res if needs(r)]
   dump('progress',dict(round=step,history=history,intent_examples=sorted(intent),semantic_examples=sorted(sem)))
   if not bad or step==6:break
   intent.update(r['text'] for r in bad)
   worst=min(bad,key=lambda r:(not r['unsafe'],r['score']))
   focus=next(r for r in learning if r['text']==worst['text'])
   train(tr,learning,focus,steps=1000)
  # Replay is retained throughout; separately verify previous contracts.
  prior=assess(oldstrict);previous=old_evaluate(oldnoisy)
  for step in range(3):
   failed=[r for r in prior if not r['passed']]+[r for r in previous if not r['passed']]
   print('PRIOR',step,sum(r['passed'] for r in prior),old_summary(previous)['all'],flush=True)
   if not failed:break
   replay=[dict(text=r['text'],action=r['action'],parameters=r['parameters']) for r in oldstrict]
   for r in previous:
    reject=r['policy']=='reject' or (r['policy']=='correct_or_reject' and not r['exact'])
    replay.append(dict(text=r['text'],action='UNKNOWN' if reject else r['action'],parameters={} if reject else r['parameters']))
   focus=next(r for r in replay if r['text']==failed[0]['text'])
   train(tr,learning+replay,focus,steps=600)
   prior=assess(oldstrict);previous=old_evaluate(oldnoisy)
  final=evaluate(rows);semantic(final,sem);final=evaluate(rows)
  dump('final',final);dump('prior-final',prior);dump('previous-final',previous)
  report=dict(before=metrics(json.loads((OUT/'baseline.json').read_text())),after=metrics(final),batches={n:metrics([r for r in final if r['batch']==n]) for n in NAMES},intent_examples=sorted(intent),semantic_examples=sorted(sem),history=history,prior_correct=sum(r['passed'] for r in prior),previous=old_summary(previous),model_sha256=hashlib.sha256(tr.path.read_bytes()).hexdigest(),notes='Post-training, in-sample. Semantic reviews are exact-case web SQLite overrides, not a trained semantic model or Android guard update. Existing hard guards retained; rejected positives reported as failures. No audio/CAN execution.')
  dump('report',report);print('DONE',report['after'],flush=True)
if __name__=='__main__':asyncio.run(main())
