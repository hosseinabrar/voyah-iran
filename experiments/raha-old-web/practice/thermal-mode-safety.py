import json,itertools,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from server import Worker
from export_lessons import export
cases=[]
for target,mode,adjective in itertools.product(['ماشین','کابین','اتاق'],['حالت','مود','وضعیت'],['گرم','سرد','خنک']):
 for text in [f'{target} رو بذار تو {mode} {adjective}',f'{mode} {target} بذار تو {mode} {adjective} ایندی وی جوال']:
  cases.append(dict(text=text,action='UNKNOWN',parameters={}))
for t in ['هوا گرمه ایندی وی جوال پرت و پلا','هوا گرمه کولر رو روشن کن موز','حالت ماشین بذار تو حالت گرم ایندی وی جوال']:
 cases.append(dict(text=t,action='UNKNOWN',parameters={}))
for t,action,params in [
 ('هوا گرمه','SET_CLIMATE_POWER',{'enabled':True,'zone':'front','acEnabled':True,'temperatureMaxC':23}),
 ('هوا خیلی گرمه','SET_MAX_COOLING',{'enabled':True}),
 ('هوا سرده','SET_CLIMATE_POWER',{'enabled':True,'zone':'front','acEnabled':True,'temperatureMinC':26}),
 ('ماشین رو گرم کن','ADJUST_CABIN_TEMPERATURE',{'zone':'front','delta':1}),
 ('ماشین رو بذار تو حالت ایندی وی جوال','SET_DRIVE_MODE',{'mode':'personal'})]:
 cases.append(dict(text=t,action=action,parameters=params))
worker=Worker();results=[]
for row in cases:
 actual=worker.recognize(row['text']);results.append(dict(**row,actual=actual,correct=actual['action']==row['action'] and actual['parameters']==row['parameters']))
worker.close();stage=sys.argv[1]
report=dict(count=len(results),correct=sum(r['correct'] for r in results),results=results)
(ROOT/f'practice/thermal-mode-{stage}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(stage,report['correct'],'/',report['count'])
if stage=='rules':
 assert report['correct']==report['count'],[r for r in results if not r['correct']]
 rows=json.loads((ROOT/'lessons.json').read_text());print('Export',export(rows+cases)['unique'])
