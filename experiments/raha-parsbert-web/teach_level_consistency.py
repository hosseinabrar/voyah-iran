"""OEM range alignment and relative FCW sensitivity, with recent replay."""
import asyncio,json
from pathlib import Path
import server
from teach_wiper_sensitivity import CASES as RECENT
CASES=list(RECENT)
def add(t,a,p): CASES.append(dict(text=t,action=a,parameters=p))
for noun in ['حساسیت هشدار برخورد جلو','حساسیت هشدار برخورد','حساسیت اخطار تصادف جلو']:
 for verb,d in [('زیاد کن',1),('بیشتر کن',1),('یک پله زیاد کن',1),('کم کن',-1),('کمتر کن',-1),('یک پله کم کن',-1)]:add(noun+' رو '+verb,'ADJUST_FORWARD_COLLISION_SENSITIVITY',{'delta':d})
 for word,s in [('کم','min'),('متوسط','medium'),('زیاد','max')]:add(noun+' رو روی '+word+' بذار','SET_FORWARD_COLLISION_WARNING',{'sensitivity':s})
for n in range(25,81,5):
 add(f'ذخیره باتری رو روی {n} درصد تنظیم کن','SET_BATTERY_RESERVE_TARGET',{'percent':n})
 add(f'حفظ شارژ رو بذار روی {n} درصد','SET_BATTERY_RESERVE_TARGET',{'percent':n})
 add(f'حالت سیو رو با حفظ شارژ {n} درصد فعال کن','SET_ENERGY_STRATEGY',{'strategy':'save','reservePercent':n})
for noun,a in [('حساسیت بین خطوط','SET_LANE_SUPPORT_MODE'),('حساسیت پایش راننده','SET_DRIVER_MONITORING')]:
 for word,s in [('کم','min'),('زیاد','max')]:add(noun+' رو روی '+word+' بذار',a,{'sensitivity':s})
 add(noun+' رو روی متوسط بذار','UNKNOWN',{})
for t,a,p in [
 ('فن رو روی صفر بذار','SET_FAN_SPEED',{'level':0,'zone':'front'}),
 ('فن رو زیاد کن','ADJUST_FAN_SPEED',{'delta':1,'zone':'front'}),
 ('دما رو زیاد کن','ADJUST_CABIN_TEMPERATURE',{'delta':1,'zone':'front'}),
 ('دما رو روی ۱۵.۵ بذار','SET_CABIN_TEMPERATURE',{'degreesC':15.5,'zone':'front'}),
 ('دما رو روی ۳۰.۵ بذار','SET_CABIN_TEMPERATURE',{'degreesC':30.5,'zone':'front'}),
 ('دما رو روی ۳۲ بذار','UNKNOWN',{}),
 ('نور نمایشگر رو زیاد کن','SET_DISPLAY_BRIGHTNESS',{'display':'central','deltaPercent':10}),
 ('صدا رو روی ۱۵ بذار','SET_AUDIO_VOLUME',{'channel':'media','level':15}),
 ('نور داخل رو روی پنجاه درصد بذار','SET_AMBIENT_LIGHT_BRIGHTNESS',{'percent':50}),
 ('شیشه رو بیشتر باز کن','ADJUST_WINDOW_POSITION',{'window':'driver','deltaPercent':10}),
 ('شیشه رو پنجاه درصد باز کن','SET_WINDOW_POSITION',{'window':'driver','percent':50}),
 ('سانروف رو پنجاه درصد باز کن','SET_SUNROOF_POSITION',{'percent':50}),
 ('پرده رو بیشتر باز کن','ADJUST_SUNSHADE_POSITION',{'deltaPercent':10}),
]:add(t,a,p)
CASES=list({r['text']:r for r in CASES}.values())
async def main():
 async with server.lifespan(server.app):
  if server.engine.get('project_sync'):server.engine['project_sync'].close()
  before=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
  trainer=server.engine['trainer']
  print(trainer.train(CASES[-1]['text'],CASES[-1]['action'],CASES[-1]['parameters'],extra_records=CASES[:-1],steps=900),flush=True)
  after=[dict(case=r,result=server.predict(r['text'])) for r in CASES]
  Path('artifacts/level-consistency-training.json').write_text(json.dumps(dict(before=before,after=after),ensure_ascii=False,indent=2))
  for stage,rows in [('before',before),('after',after)]:
   bad=[]
   for x in rows:
    e=x['case'];r=x['result'];p=r['prediction']
    if not(p['label']==e['action'] and r['slots']==e['parameters'] and (r['accepted'] and int(p['score']*100+.5)==100 if e['action']!='UNKNOWN' else not r['accepted'])):bad.append(x)
   print(stage,len(rows)-len(bad),'/',len(rows),flush=True)
   if stage=='after':
    for x in bad:print(json.dumps(x,ensure_ascii=False),flush=True)
if __name__=='__main__':asyncio.run(main())
