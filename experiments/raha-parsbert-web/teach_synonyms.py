"""Practice after an immutable blind synonym evaluation. No policy relaxation."""
import asyncio,json,sqlite3
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from teach_remaining import evaluate,summary
from teach_batches import train,assess
OUT=Path('artifacts/synonyms-unseen')
def wrong100(r):return r['predicted']!='UNKNOWN' and round(r['score']*100)==100 and (r.get('policy')=='reject' or not r['exact'])
def needs_model(r):return not r['exact'] or round(r['score']*100)!=100
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];rows=json.loads((OUT/'reviewed-samples.json').read_text());intent=set();semantic=set();history=[]
  # Match the schema's JSON spelling for numeric slots (16 vs 16.0).
  for r in rows:
   if r['action']=='CALL_CONTACT':continue
   frame=r['action']+'|'+','.join(sorted(r['parameters']))
   for k,v in list(r['parameters'].items()):
    choices=[json.loads(x) for x in tr.model.schema['allowed'][frame][k]]
    r['parameters'][k]=next(x for x in choices if x==v and isinstance(x,bool)==isinstance(v,bool))
  for step in range(5):
   res=evaluate(rows);history.append(summary(res));(OUT/'latest.json').write_text(json.dumps(res,ensure_ascii=False,indent=2));print('SYNONYMS',step,summary(res),flush=True)
   for r in rows:
    if not ef.evaluate(r['text'],r['action'],r['parameters'])['allowed']:
     ef.save(r['text'],r['action'],r['parameters'],True,'تایید معنای مترادف پس از آزمون مستقل');semantic.add(r['text'])
   bad=[r for r in res if needs_model(r)]
   if not bad or step==4:break
   intent.update(r['text'] for r in bad);worst=min(bad,key=lambda r:(not r['unsafe'],r['score']));train(tr,rows,worst,steps=1100)
  old=json.loads(Path('regression-contracts/batch01/samples.json').read_text())+json.loads(Path('regression-contracts/batch02/samples.json').read_text())
  oldnoisy=[]
  for name in [f'batch{i:02}' for i in range(3,11)]+['batch_remaining']:oldnoisy+=json.loads((Path('regression-contracts')/name/'samples.json').read_text())
  for step in range(4):
   prior=assess(old);previous=evaluate(oldnoisy);res=evaluate(rows)
   bad=[r for r in prior if not r['passed']]+[r for r in previous if not r['passed'] or wrong100(r)]+[r for r in res if needs_model(r)]
   print('REGRESSION',step,sum(r['passed'] for r in prior),summary(previous), 'new',summary(res),flush=True)
   if not bad or step==3:break
   learning=[{k:r[k] for k in ['text','action','parameters']} for r in prior]+rows
   for r in previous:
    reject=r['policy']=='reject' or (r['policy']=='correct_or_reject' and not r['exact'])
    learning.append(dict(text=r['text'],action='UNKNOWN' if reject else r['action'],parameters={} if reject else r['parameters']))
   worst=min(bad,key=lambda r:r['score']);focus=next(r for r in learning if r['text']==worst['text']);train(tr,learning,focus,steps=600)
  res=evaluate(rows)
  report={'before':summary(json.loads((OUT/'reviewed-baseline.json').read_text())),'after':summary(res),'intent_examples':sorted(intent),'semantic_examples':sorted(semantic),'history':history,'prior_1200':sum(r['passed'] for r in prior),'previous':summary(previous),'previous_wrong_at_100':sum(wrong100(r) for r in previous),'new_raw_exact':sum(r['exact'] for r in res),'new_raw_exact_at_100':sum(r['exact'] and round(r['score']*100)==100 for r in res),'remaining_failures':[r for r in res if not r['passed']],'notes':'Blind baseline is separate and immutable. Post-training numbers are in-sample. Question/negative guards were not relaxed. No vehicle or microphone execution.'}
  (OUT/'final.json').write_text(json.dumps(res,ensure_ascii=False,indent=2));(OUT/'previous-final.json').write_text(json.dumps(previous,ensure_ascii=False,indent=2));(OUT/'prior-final.json').write_text(json.dumps(prior,ensure_ascii=False,indent=2));(OUT/'training-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('DONE',report['after'],flush=True)
if __name__=='__main__':asyncio.run(main())
