import asyncio,json,shutil,sqlite3
from pathlib import Path
import server,project_sync,evidence_feedback as ef
from data import normalize
OUT=Path('artifacts/batch01');OUT.mkdir(exist_ok=True)
def samples(s):
 a=sorted(k for k in s['titles'] if k!='UNKNOWN');(OUT/'command-batches.json').write_text(json.dumps([a[i:i+10] for i in range(0,len(a),10)],indent=2));rows=[]
 names=[[],['شدت نورپردازی داخلی','نور محیط کابین','روشنایی نورپردازی'],['صدای موسیقی','بلندی صدا','صدای مولتی مدیا'],['دمای کابین','دمای سمت راننده','دمای سمت شاگرد'],['سرعت فن','دور فن کولر','شدت باد تهویه'],['شدت رایحه','شدت خوشبوکننده','میزان پخش رایحه'],['سطح بازیابی انرژی','میزان بازیابی انرژی','شدت احیای انرژی'],['شدت گرمکن صندلی راننده','شدت گرمکن صندلی شاگرد','شدت گرم کن صندلی همراه'],['شدت ماساژ صندلی راننده','قدرت ماساژ صندلی شاگرد','شدت ماساژور صندلی همراه'],['شدت سردکن صندلی راننده','شدت تهویه صندلی شاگرد','شدت سرد کن صندلی همراه']]
 for ai,action in enumerate(a[:10]):
  pos=[]
  if ai==0:
   for ti,t in enumerate(['سناریو {} رو فعال کن','ماشین رو بذار تو حالت {}','حالت {} رو روشن کن','سناریوی {} رو راه بنداز','لطفا برو روی سناریو {}','می خوام حالت {} فعال بشه']):
    for n,v in [('کمپ','camp'),('کارواش','carWash'),('مراقبت از کودک','babyCare'),('چرت','nap'),('مهمانی','party')]:pos.append((t.format(n),{'scene':v},ti==5))
  else:
   for ti,t in enumerate(['{} رو یک پله {} کن','{} را یه مقدار {} کن','{} رو یه درجه {} کن','لطفا {} رو کمی {} کن','می خوام {} یک سطح {} بشه']):
    for ni,n in enumerate(names[ai]):
     for d,v in [(-1,'کم'),(1,'زیاد')]:
      p={'delta':d}
      if ai==2:p['channel']='media'
      if ai==3:p['zone']=['front','driver','passenger'][ni]
      if ai==4:p['zone']='front'
      if ai>=7:p['seat']='driver' if ni==0 else 'passenger'
      pos.append((t.format(n,v),p,ti==4))
  n='سناریو کمپ' if ai==0 else names[ai][0]
  neg=[f'{n} رو فعال نکن',f'{n} رو تغییر نده',f'{n} رو زیاد نکن',f'{n} رو کم نکن',f'نمی خوام {n} عوض بشه',f'آیا {n} روشنه؟',f'{n} یعنی چی؟',f'چرا {n} کم شده؟',f'چطور {n} کار می کنه؟',f'{n} الان چقدره؟',f'اگر بارون اومد {n} رو زیاد کن',f'اگه فردا سرد شد {n} رو کم کن',f'وقتی رسیدیم {n} رو فعال کن',f'بعد از پیاده شدنم {n} رو تغییر بده',f'اگه بچه خوابید {n} رو کم کن',f'دیروز {n} رو زیاد کردم',f'قبلا {n} کمتر بود',f'او گفت {n} رو زیاد کرده',f'یادم هست {n} رو کم کرده بودم',f'داشتم درباره {n} حرف می زدم',f'{n} رو بخور',f'{n} رو به علی زنگ بزن',f'{n} رو آبی بشور',f'{n} رو دانلود کن',f'{n} رو سرخ کن',f'{n} رو هم زیاد کن هم کم کن',f'{n} رو نه زیاد نه کم کن',f'{n} رو روشن خاموش باز بسته کن',f'{n} رو شاید یه روزی تغییر بدم',f'لطفا درباره {n} یه شعر بگو']
  for t,p,h in pos:rows.append(dict(group=action,text=normalize(t),action=action,parameters=p,valid=True,split='holdout' if h else 'train'))
  for i,t in enumerate(neg):rows.append(dict(group=action,text=normalize(t),action='UNKNOWN',parameters={},valid=False,split='holdout' if i%5==4 else 'train'))
 assert len(rows)==len({r['text'] for r in rows})==600
 return rows
def evaluate(rows):
 out=[]
 for i,r in enumerate(rows):
  p=server.predict(r['text']);exact=p['prediction']['label']==r['action'] and p['slots']==r['parameters'];e=ef.evaluate(r['text'],r['action'] if r['valid'] else p['prediction']['label'],r['parameters'] if r['valid'] else p['slots']);exe=p['accepted'] and round(p['prediction']['score']*100)==100
  out.append(dict(**r,exact=exact,semantic=e['allowed']==r['valid'],passed=(exact and exe) if r['valid'] else not exe,predicted=p['prediction']['label'],slots=p['slots'],score=p['prediction']['score'],reason=p['reason']))
  if i%100==0:print('EVAL',i,flush=True)
 return out
def summary(rows):
 return {g:dict(count=len(rs),intent_exact=sum(r['exact'] for r in rs),semantic_correct=sum(r['semantic'] for r in rs),end_to_end=sum(r['passed'] for r in rs)) for g,rs in [('all',rows),('valid',[r for r in rows if r['valid']]),('invalid',[r for r in rows if not r['valid']]),('holdout',[r for r in rows if r['split']=='holdout'])]}
async def main():
 project_sync.ProjectSync.start=lambda self:None
 async with server.lifespan(server.app):
  tr=server.engine['trainer'];print('LOADED',len(tr.rows),tr.path,flush=True);shutil.copy2(tr.path,OUT/'before.safetensors')
  with ef.connect() as c:
   with sqlite3.connect(OUT/'before-evidence.sqlite3') as dest:c.backup(dest)
  rows=samples(server.engine['model'].schema);(OUT/'samples.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2));before=evaluate(rows);(OUT/'baseline.json').write_text(json.dumps(before,ensure_ascii=False,indent=2));print('BASELINE',summary(before),flush=True)
  fixes=[r for r in before if r['split']=='train' and (not r['exact'] or (r['valid'] and not r['passed']))];sem=[]
  for r in before:
   if r['split']!='train' or r['semantic']:continue
   ef.save(r['text'],r['action'] if r['valid'] else r['predicted'],r['parameters'] if r['valid'] else r['slots'],r['valid'],'بازبینی دسته اول: '+('فرمان معتبر' if r['valid'] else 'غیردستوری، متناقض یا خارج از کاربرد خودرو'));sem.append(r['text'])
  if fixes:
   merged={normalize(r['text']):r for r in tr.rows}
   for r in fixes:merged[r['text']]={k:r[k] for k in ['text','action','parameters']}
   last=merged.pop(fixes[-1]['text']);print('TRAIN',len(fixes),'REPLAY',len(merged),flush=True);tr.train(last['text'],last['action'],last['parameters'],extra_records=list(merged.values()))
  after=evaluate(rows);report=dict(baseline=summary(before),after=summary(after),intent_taught=len(fixes),semantic_taught=len(sem),intent_examples=[r['text'] for r in fixes],semantic_examples=sem,checks=after)
  (OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('RESULT',report['after'],flush=True)
if __name__=='__main__':asyncio.run(main())
