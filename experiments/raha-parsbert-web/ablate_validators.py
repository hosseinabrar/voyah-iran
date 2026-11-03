"""Read-only validator ablation, identical checkpoint and one inference per text.
No training, model writes, API changes, or vehicle execution.
"""
import asyncio,json,hashlib,csv,collections
from pathlib import Path
import server,project_sync
OUT=Path('artifacts/validator-ablation-round13')

def samples():
 rows=json.loads(Path('artifacts/unseen-rounds-training/samples.json').read_text())
 for r in rows:r['set']='rounds01-05'
 for i in range(6,14):
  q=json.loads(Path(f'regression-contracts/mixed-unseen-round{i:02}/samples.json').read_text())
  for r in q:r['set']=f'round{i:02}'
  rows+=q
 for name in [f'batch{i:02}' for i in range(1,11)]+['batch_remaining']:
  q=json.loads(Path(f'regression-contracts/{name}/samples.json').read_text())
  for r in q:
   r['set']=name
   if 'policy' not in r:r['policy']='correct' if r['valid'] else 'reject'
  rows+=q
 return rows

def measure(rows,mode):
 def m(rs):
  return dict(count=len(rs),correct_accepted=sum(x[mode]['execute'] and x[mode]['exact'] and x['policy']!='reject' for x in rs),rejected=sum(not x[mode]['execute'] for x in rs),wrong_accepted=sum(x[mode]['unsafe'] for x in rs),passed=sum(x[mode]['passed'] for x in rs),raw_exact=sum(x[mode]['exact'] for x in rs))
 return {k:m([x for x in rows if k=='all' or x['policy']==k]) for k in ['all','correct','reject','correct_or_reject']}

async def main():
 if OUT.exists():raise RuntimeError('Preserve ablation results; choose a fresh directory.')
 OUT.mkdir()
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];sha=hashlib.sha256(tr.path.read_bytes()).hexdigest();rows=samples();results=[]
  for i,r in enumerate(rows):
   p=server.predict(r['text']);top=p['prediction'];action=top['label'];raw=dict(top['parameters']);grounded=p['slots']
   # Preserve contact extraction and real dialogue prerequisites, independent of semantic guards.
   if action=='CALL_CONTACT':raw['contact_query']=server.contact_target(p['text'])
   pending=r.get('context')=='pending_call_confirmation'
   def eligible(params,keys):
    return bool(p['text']) and len(server.engine['tokenizer'].encode(p['text']))<=48 and action!='UNKNOWN' and round(top['score']*100)==100 and top['score']-p['candidates'][1]['score']>=.15 and all(v>=.65 for k,v in top['slot_scores'].items() if k not in keys) and (action!='CALL_CONTACT' or bool(params.get('contact_query'))) and (action not in ['CONFIRM_CALL','CANCEL_DIALOG'] or pending)
   _,keys,_=server.ground_numbers(p['text'],action,dict(top['parameters']),server.engine['model'].schema)
   context_ok=pending and p['evidence']['model_reason']=='پاسخ مربوط به گفتگوی تماس است؛ این صفحه تماس در انتظاری ندارد' and p['evidence']['allowed']
   options=r.get('acceptable_predictions',[dict(action=r['action'],parameters=v) for v in [r['parameters']]+r.get('alternatives',[])])
   modes={
    'current':(grounded,(p['accepted'] or context_ok) and round(top['score']*100)==100),
    'no_semantic':(grounded,(p['evidence']['model_accepted'] or (pending and p['evidence']['model_reason']=='پاسخ مربوط به گفتگوی تماس است؛ این صفحه تماس در انتظاری ندارد')) and round(top['score']*100)==100),
    'no_guards_keep_numbers':(grounded,eligible(grounded,keys)),
    'raw_model':(raw,eligible(raw,set())),
   }
   row={k:r.get(k) for k in ['text','set','policy','action','parameters']};row.update(predicted=action,score=top['score'],current_reason=p['reason'])
   for mode,(params,execute) in modes.items():
    exact=any(action==v['action'] and params==v['parameters'] for v in options)
    unsafe=bool(execute and (r['policy']=='reject' or not exact))
    row[mode]=dict(parameters=params,execute=bool(execute),exact=exact,unsafe=unsafe,passed=bool(execute and exact) if r['policy']=='correct' else not unsafe)
   results.append(row)
   if (i+1)%1000==0:print('CHECKED',i+1,len(rows),flush=True)
  assert hashlib.sha256(tr.path.read_bytes()).hexdigest()==sha
  report=dict(model_sha256=sha,count=len(results),unique_texts=len({r['text'] for r in results}),metrics={m:measure(results,m) for m in modes},last_round={m:measure([r for r in results if r['set']=='round13'],m) for m in modes},notes='Previously trained text samples; no new training or microphone/vehicle test. Same score threshold and checkpoint. no_semantic disables semantic evidence including exact reviews only. no_guards_keep_numbers also bypasses numeric/consistency/negation guards but retains numeric parameter rewriting. raw_model also removes numeric rewriting. UNKNOWN, confidence/slot certainty and contact/dialogue prerequisites retained.')
  for mode in modes:
   report.setdefault('changes',{})[mode]=dict(rescued=sum(not r['current']['execute'] and r[mode]['execute'] and r[mode]['exact'] and r['policy']!='reject' for r in results),new_wrong=sum(not r['current']['unsafe'] and r[mode]['unsafe'] for r in results))
  (OUT/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
  with (OUT/'results.csv').open('w',encoding='utf-8-sig',newline='') as f:
   w=csv.writer(f);w.writerow(['text','set','policy','expected','expected_parameters','predicted','score','current_reason']+[m+'_'+k for m in modes for k in ['parameters','execute','exact','unsafe']])
   for r in results:w.writerow([r[k] if k!='parameters' else json.dumps(r[k],ensure_ascii=False) for k in ['text','set','policy','action','parameters','predicted','score','current_reason']]+[json.dumps(r[m][k],ensure_ascii=False) for m in modes for k in ['parameters','execute','exact','unsafe']])
  print('DONE',json.dumps(report,ensure_ascii=False),flush=True)
asyncio.run(main())
