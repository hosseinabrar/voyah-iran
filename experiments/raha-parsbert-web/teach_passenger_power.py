"""Passenger display power only; central/cluster and brightness stay distinct."""
import asyncio,json
from pathlib import Path
import server
from teach_wireless_charger import CASES as RECENT
CASES=list(RECENT)
def add(t,a,p):CASES.append(dict(text=t,action=a,parameters=p))
for noun in ['مانیتور سمت راست','نمایشگر شاگرد','نمایشگر همراه','مانیتور شاگرد','صفحه نمایش سمت راست','صفحه نمایش همراه']:
 for verb,on in [('خاموش کن',False),('روشن کن',True),('فعال کن',True),('غیر فعال کن',False),('اکتیو کن',True),('دی اکتیو کن',False)]:
  add(noun+' رو '+verb,'SET_PASSENGER_DISPLAY_POWER',{'enabled':on})
for noun in ['نمایشگر مرکزی','مانیتور راننده','نمایشگر کلاستر','همه نمایشگرها']:
 for verb in ['خاموش کن','روشن کن']:add(noun+' رو '+verb,'UNKNOWN',{})
for t in ['نمایشگر شاگرد رو خاموش نکن','نمایشگر شاگرد خاموشه؟','نمایشگر شاگرد رو گرم کن']:
 add(t,'UNKNOWN',{})
add('نور نمایشگر شاگرد رو کم کن','SET_DISPLAY_BRIGHTNESS',{'display':'passenger','deltaPercent':-10})
add('نور نمایشگر شاگرد رو روی پنجاه درصد بذار','SET_DISPLAY_BRIGHTNESS',{'display':'passenger','percent':50})
CASES=list({r['text']:r for r in CASES}.values())
CASES.sort(key=lambda r:r['text']=='مانیتور سمت راست رو غیر فعال کن')
async def main():
 async with server.lifespan(server.app):
  if server.engine.get('project_sync'):server.engine['project_sync'].close()
  before=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
  trainer=server.engine['trainer']
  print(trainer.train(CASES[-1]['text'],CASES[-1]['action'],CASES[-1]['parameters'],extra_records=CASES[:-1],steps=900),flush=True)
  after=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
  Path('artifacts/passenger-power-training.json').write_text(json.dumps(dict(before=before,after=after),ensure_ascii=False,indent=2))
  for stage,rows in [('before',before),('after',after)]:
   bad=[]
   for x in rows:
    e=x['case'];r=x['result'];p=r['prediction']
    if not(p['label']==e['action'] and r['slots']==e['parameters'] and (r['accepted'] and int(p['score']*100+.5)==100 if e['action']!='UNKNOWN' else not r['accepted'])):bad.append(x)
   print(stage,len(rows)-len(bad),'/',len(rows),flush=True)
   if stage=='after':
    for x in bad:print(json.dumps(x,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
