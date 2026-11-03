"""Independent situation-based test: no training, no semantic-review writes."""
import asyncio,json,hashlib
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from data import normalize
from mixed_round11_samples import samples
OUT=Path('artifacts/mixed-unseen-round11');OUT.mkdir(exist_ok=True)
def variants(r):
 result=[(r['action'],r['parameters'])]+[(r['action'],p) for p in r['alternatives']]
 if r['action'] in ['OPEN_WINDOW','CLOSE_WINDOW']:result.append(('SET_WINDOW_POSITION',dict(r['parameters'],percent=100 if r['action']=='OPEN_WINDOW' else 0)))
 if r['action'] in ['OPEN_SUNROOF','CLOSE_SUNROOF']:result.append(('SET_SUNROOF_POSITION',{'percent':100 if r['action']=='OPEN_SUNROOF' else 0}))
 if r['action']=='SET_AC_POWER' and r['parameters']=={'enabled':True}:result.append(('SET_CLIMATE_POWER',{'enabled':True,'zone':'front','acEnabled':True,'temperatureMaxC':23.0}))
 return result
async def main():
 if (OUT/'baseline.json').exists():raise RuntimeError('Preserve the independent baseline.')
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];known={normalize(r['text']) for r in tr.rows}
  for values in json.loads(Path('dataset.json').read_text()).values():
   if isinstance(values,list):known.update(normalize(r['text']) for r in values if isinstance(r,dict) and 'text' in r)
  for p in Path('regression-contracts').glob('*/samples.json'):known.update(normalize(r['text']) for r in json.loads(p.read_text()))
  for p in [Path('artifacts/synonyms-unseen/samples.json')]:known.update(normalize(r['text']) for r in json.loads(p.read_text()))
  with ef.connect() as c:
   for (target,) in c.execute('select target from reviews'):known.add(normalize(json.loads(target)[0]))
  rows=samples(known);assert not known.intersection(r['text'] for r in rows)
  for r in rows:r['acceptable_predictions']=[dict(action=a,parameters=p) for a,p in variants(r)]
  checkpoint_before=hashlib.sha256(tr.path.read_bytes()).hexdigest()
  (OUT/'samples.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2));result=[]
  for r in rows:
   p=server.predict(r['text']);exact=any(p['prediction']['label']==v['action'] and p['slots']==v['parameters'] for v in r['acceptable_predictions']);executed=p['accepted'] and round(p['prediction']['score']*100)==100
   unsafe=executed and (r['policy']=='reject' or not exact);passed=not executed if r['policy']=='reject' else executed and exact
   result.append(dict(**r,exact=exact,passed=passed,unsafe=unsafe,executed=executed,predicted=p['prediction']['label'],slots=p['slots'],score=p['prediction']['score'],reason=p['reason'],model_reason=p['evidence']['model_reason'],evidence=p['evidence']))
  def metrics(rs):return dict(count=len(rs),passed=sum(r['passed'] for r in rs),unsafe=sum(r['unsafe'] for r in rs),executed=sum(r['executed'] for r in rs),raw_exact=sum(r['exact'] for r in rs),rejected=sum(not r['executed'] for r in rs))
  assert hashlib.sha256(tr.path.read_bytes()).hexdigest()==checkpoint_before, 'Model changed during evaluation'
  report=dict(total=metrics(result),clear=metrics([r for r in result if r['policy']=='correct']),reject=metrics([r for r in result if r['policy']=='reject']),exact_overlap=0,known_texts=len(known),base_phrasings=len({r['core'] for r in rows}),categories=len({r['family'] for r in rows}),actions=sorted({r['action'] for r in rows if r['action']!='UNKNOWN'}),model_sha256=hashlib.sha256(tr.path.read_bytes()).hexdigest(),trained_on_this_set=False,notes='1000 compositional sentences: 800 positive, 200 reject. Template/context/target/amount combinations are correlated, not 1000 independent scenarios. Frozen gold before inference. Text only. No training or semantic feedback.')
  (OUT/'baseline.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
