import itertools,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from server import Worker
from export_lessons import export
cases=[]
for target,verb,marker,prefix in itertools.product(['دمای چپ و راست','دمای راست و چپ','دمای راننده و شاگرد','دمای راننده و همراه','دمای دو طرف','دمای هر دو طرف','دمای کولر','دمای کابین'],['یکسان کن','یکی کن','هماهنگ کن','همگام کن','سینک کن','مثل هم کن'],[' رو ',' '],['','لطفا ']):
 cases.append(dict(text=prefix+target+marker+verb,action='SET_FRONT_CLIMATE_SYNC',parameters={'enabled':True}))
for noun,verb,marker in itertools.product(['یکسان سازی دمای چپ و راست','همگام سازی دمای دو طرف','سینک دمای کولر','هماهنگ سازی دمای راننده و شاگرد'],['فعال کن','روشن کن','غیر فعال کن','خاموش کن'],[' رو ',' ']):
 cases.append(dict(text=noun+marker+verb,action='SET_FRONT_CLIMATE_SYNC',parameters={'enabled':verb in ['فعال کن','روشن کن']}))
cases.append(dict(text='یکسان سازی دمای چپ و راست',action='SET_FRONT_CLIMATE_SYNC',parameters={'enabled':True}))
for target,n,verb in itertools.product(['دمای کابین','دمای اتاق','دمای ماشین'],range(16,31),['رو بذار روی','رو تنظیم کن روی']):
 cases.append(dict(text=f'{target} {verb} {n}',action='SET_CABIN_TEMPERATURE',parameters={'zone':'front','degreesC':n}))
for target,(verb,sign),n,marker,prefix in itertools.product(['دمای کابین','دمای اتاق','دمای ماشین','دمای کولر'],[('زیاد کن',1),('کم کن',-1),('ببر بالا',1),('بیار پایین',-1)],range(1,4),[' رو ',' '],['','لطفا ']):
 cases.append(dict(text=f'{prefix}{target}{marker}{n} درجه {verb}',action='ADJUST_CABIN_TEMPERATURE',parameters={'zone':'front','delta':n*sign}))
w=Worker();results=[]
for r in cases:
 a=w.recognize(r['text']);results.append(dict(**r,actual=a,correct=a['action']==r['action'] and a['parameters']==r['parameters']))
w.close();stage=sys.argv[1];report=dict(count=len(results),correct=sum(r['correct'] for r in results),results=results)
(ROOT/f'practice/climate-sync-{stage}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(stage,report['correct'],'/',report['count'])
if stage=='rules':
 assert report['correct']==report['count'],[r for r in results if not r['correct']][:8]
 print('Export',export(json.loads((ROOT/'lessons.json').read_text())+cases)['unique'])
