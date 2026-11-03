import asyncio,json,sqlite3
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from practice_batch01 import OUT,evaluate,summary
from safetensors.torch import load_file

def load_head(path):
 s=load_file(str(path));m=server.engine['model'];m.frame_head.load_state_dict({k[6:]:v for k,v in s.items() if k.startswith('frame.')});m.slot_heads.load_state_dict({k[6:]:v for k,v in s.items() if k.startswith('slots.')})
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];rows=json.loads((OUT/'samples.json').read_text());before=json.loads((OUT/'baseline.json').read_text());old=json.loads((OUT/'report.json').read_text());fixtexts=set(old['intent_examples'])
  # Evaluate the semantic layer independently: offer a related executable
  # interpretation even when the classifier correctly abstains with UNKNOWN.
  gold={r['group']:r for r in rows if r['valid']}
  original_path=ef.PATH;ef.PATH=OUT/'before-evidence.sqlite3'
  def sem(r):
   q=r if r['valid'] else gold[r['group']]
   return ef.evaluate(r['text'],q['action'],q['parameters'])['allowed']==r['valid']
  for r in before:r['semantic']=sem(r)
  ef.PATH=original_path
  # Undo only this run's incorrectly scored UNKNOWN reviews, retaining earlier reviews.
  with sqlite3.connect(OUT/'before-evidence.sqlite3') as src:
   with ef.connect() as dest:src.backup(dest)
  semtexts=set()
  for r in before:
   if r['split']!='train' or r['semantic']:continue
   q=r if r['valid'] else gold[r['group']]
   ef.save(r['text'],q['action'],q['parameters'],r['valid'],'دسته اول: '+('درخواست معتبر' if r['valid'] else 'جمله غیردستوری یا نامعتبر'));semtexts.add(r['text'])
  # Preserve explicit rejections of actual erroneous baseline interpretations too.
  for r in before:
   if r['split']=='train' and not r['valid'] and r['predicted']!='UNKNOWN' and ef.evaluate(r['text'],r['predicted'],r['slots'])['allowed']:
    ef.save(r['text'],r['predicted'],r['slots'],False,'دسته اول: برداشت اجرایی از جمله نامعتبر');semtexts.add(r['text'])
  current=evaluate(rows);more=[r for r in current if r['split']=='train' and (not r['exact'] or (r['valid'] and not r['passed']))]
  merged={r['text']:r for r in tr.rows}
  for r in more:merged[r['text']]={k:r[k] for k in ['text','action','parameters']};fixtexts.add(r['text'])
  if more:
   last=merged.pop(more[-1]['text']);print('REFINE',len(more),flush=True);tr.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()),steps=1200)
  after=evaluate(rows)
  for r in after:r['semantic']=sem(r)
  report=dict(baseline=summary(before),after=summary(after),intent_taught=len(fixtexts),semantic_taught=len(semtexts),intent_examples=sorted(fixtexts),semantic_examples=sorted(semtexts),checks=after)
  # Fixed prior-feedback regression slice, not added to current training labels.
  import random
  prior=json.loads(__import__('safetensors').safe_open(str(OUT/'before.safetensors'),framework='pt').metadata()['feedback'])
  prior=[r for r in prior if 'parameters' in r and r['action']!='CALL_CONTACT'];prior=random.Random(310).sample(prior,min(200,len(prior)))
  def accuracy():
   return [server.predict(r['text']) for r in prior]
  load_head(OUT/'before.safetensors');bp=accuracy();load_head(tr.path);ap=accuracy()
  exact=lambda r,p:p['prediction']['label']==r['action'] and p['slots']==r['parameters']
  report['regression']={'count':len(prior),'before':sum(exact(r,p) for r,p in zip(prior,bp)),'after':sum(exact(r,p) for r,p in zip(prior,ap)),'new_failures':[r for r,b,a in zip(prior,bp,ap) if exact(r,b) and not exact(r,a)]}
  (OUT/'baseline.json').write_text(json.dumps(before,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('RESULT',report['after'],report['regression'],flush=True)
if __name__=='__main__':asyncio.run(main())
