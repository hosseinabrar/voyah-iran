"""Frozen, pre-training evaluation of fresh colloquial requests across all actions."""
import asyncio,json,hashlib,shutil,sqlite3
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from data import normalize
from synonym_samples import samples
from teach_remaining import evaluate,summary
OUT=Path('artifacts/synonyms-unseen');OUT.mkdir(exist_ok=True)
async def main():
 if (OUT/'baseline.json').exists():raise RuntimeError('Preserve the blind baseline; use a separate output directory.')
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];known={normalize(r['text']) for r in tr.rows}
  dataset=json.loads(Path('dataset.json').read_text())
  for values in dataset.values():
   if isinstance(values,list):
    for r in values:
     if isinstance(r,dict) and 'text' in r:known.add(normalize(r['text']))
  for p in Path('regression-contracts').glob('*/samples.json'):
   known.update(normalize(r['text']) for r in json.loads(p.read_text()))
  with ef.connect() as c:
   for (target,) in c.execute('select target from reviews'):known.add(normalize(json.loads(target)[0]))
   with sqlite3.connect(OUT/'before-evidence.sqlite3') as dst:c.backup(dst)
  shutil.copy2(tr.path,OUT/'before.safetensors')
  rows=samples(known)
  assert not (known & {r['text'] for r in rows})
  (OUT/'samples.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
  report={'count':len(rows),'covered_actions':len({r['action'] for r in rows}),'known_normalized_text_count':len(known),'exact_overlap':0,'distinct_new_seed_families':len({r['family'] for r in rows}),'checkpoint_sha256':hashlib.sha256(tr.path.read_bytes()).hexdigest(),'notes':'Blind text test. No learning or semantic review changes made. 12 unseen normalized sentences per action. Some sentences share a synonym seed; no claim of 1368 independent semantic families.'}
  result=evaluate(rows);report['baseline']=summary(result)
  report['raw_action_correct']=sum(r['action']==r['predicted'] for r in result);report['raw_action_and_parameters_correct']=sum(r['exact'] for r in result)
  report['by_action']={a:summary([r for r in result if r['group']==a])['all'] for a in sorted({r['group'] for r in rows})}
  (OUT/'baseline.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
