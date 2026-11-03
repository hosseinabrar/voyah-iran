import asyncio,json,sqlite3,random,shutil
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from teach_batches import OUT,assess,summary
from safetensors.torch import load_file
from safetensors import safe_open
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];b1=json.loads(Path('regression-contracts/batch01/samples.json').read_text());b2=json.loads((OUT/'batch02-samples.json').read_text());checks=assess(b1+b2)
  print('FINAL FRESH',summary(checks),flush=True)
  report=json.loads((OUT/'report.json').read_text());report['final_batch01']=summary(checks[:600]);report['final_batch02']=summary(checks[600:]);report['fresh_process']=True
  print('FAILURES',[(r['text'],r['slots'],r['reason']) for r in checks if not r['passed']],flush=True)
  with safe_open(str(OUT/'before.safetensors'),framework='pt') as f:prior=json.loads(f.metadata()['feedback'])
  prior=[r for r in prior if 'parameters' in r and r['action']!='CALL_CONTACT'];prior=random.Random(310).sample(prior,200)
  def exact():
   out=[]
   for r in prior:
    p=server.predict(r['text']);out.append(p['prediction']['label']==r['action'] and p['slots']==r['parameters'])
   return out
  def load(p):
   s=load_file(str(p));m=server.engine['model'];m.frame_head.load_state_dict({k[6:]:v for k,v in s.items() if k.startswith('frame.')});m.slot_heads.load_state_dict({k[6:]:v for k,v in s.items() if k.startswith('slots.')})
  final=exact();load(OUT/'before.safetensors');before=exact();load(tr.path)
  report['regression']={'count':200,'before':sum(before),'after':sum(final),'new_failures':[r for r,b,a in zip(prior,before,final) if b and not a]}
  (OUT/'final.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2));(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('REGRESSION',report['regression'],flush=True)
if __name__=='__main__':asyncio.run(main())
