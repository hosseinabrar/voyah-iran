"""Wireless phone charger contrasted with charge-port and scheduled charging."""
import asyncio,json
from pathlib import Path
import server
from teach_level_consistency import CASES as RECENT
CASES=list(RECENT)
def add(t,a,p):CASES.append(dict(text=t,action=a,parameters=p))
for noun in ['شارژر','شارژ وایرلس','شارژر وایرلس','شارژر موبایل','شارژ گوشی','وایرلس شارژر']:
 for verb,on in [('روشن کن',True),('فعال کن',True),('اکتیو کن',True),('خاموش کن',False),('غیر فعال کن',False),('بذار تو حالت روشن',True),('بذار تو حالت خاموش',False)]:
  add(noun+' رو '+verb,'SET_WIRELESS_CHARGER',{'enabled':on})
for t in ['گوشی رو شارژ کن','گوشیمو شارژ کن','گوشیم رو شارژ کن','موبایلم رو شارژ کن','شارژر رو روشن کن']:
 add(t,'SET_WIRELESS_CHARGER',{'enabled':True})
for t in ['شارژ گوشی رو قطع کن','شارژ موبایل رو متوقف کن','شارژر گوشی رو خاموش کن']:
 add(t,'SET_WIRELESS_CHARGER',{'enabled':False})
for noun in ['در شارژر','درگاه شارژ','در شارژ','درب شارژر']:
 for verb in ['باز کن','وا کن']:add(noun+' رو '+verb,'OPEN_CHARGE_PORT',{})
for noun in ['شارژ زمانبندی شده','زمان بندی شارژ','شارژ سر ساعت','شارژ برنامه ریزی شده']:
 for verb,on in [('فعال کن',True),('روشن کن',True),('خاموش کن',False),('غیر فعال کن',False)]:
  add(noun+' رو '+verb,'SET_SCHEDULED_CHARGING',{'enabled':on})
for t in ['ماشین رو سر ساعت شارژ کن','ماشین رو بر اساس زمانبندی شارژ کن']:
 add(t,'SET_SCHEDULED_CHARGING',{'enabled':True})
for t in ['می خوام ماشین رو شارژ کنم','می خوام شارژر رو وصل کنم','می خوام باتری رو شارژ کنم']:
 add(t,'OPEN_CHARGE_PORT',{})
for t in ['شارژر وایرلس رو گرم کن','شارژ گوشی رو مسیریابی کن','گوشی رو شارژ نکن','شارژر روشنه؟','در شارژر رو روشن کن','شارژ گوشی رو سر ساعت روشن کن']:
 add(t,'UNKNOWN',{})
CASES=list({r['text']:r for r in CASES}.values())
CASES.sort(key=lambda r:r['text']=='شارژر رو روشن کن')
async def main():
 async with server.lifespan(server.app):
  if server.engine.get('project_sync'):server.engine['project_sync'].close()
  before=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
  trainer=server.engine['trainer']
  print(trainer.train(CASES[-1]['text'],CASES[-1]['action'],CASES[-1]['parameters'],extra_records=CASES[:-1],steps=900),flush=True)
  after=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
  Path('artifacts/wireless-charger-training.json').write_text(json.dumps(dict(before=before,after=after),ensure_ascii=False,indent=2))
  for stage,rows in [('before',before),('after',after)]:
   bad=[]
   for x in rows:
    e=x['case'];r=x['result'];p=r['prediction']
    if not(p['label']==e['action'] and r['slots']==e['parameters'] and (r['accepted'] and int(p['score']*100+.5)==100 if e['action']!='UNKNOWN' else not r['accepted'])):bad.append(x)
   print(stage,len(rows)-len(bad),'/',len(rows),flush=True)
   if stage=='after':
    for x in bad:print(json.dumps(x,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
