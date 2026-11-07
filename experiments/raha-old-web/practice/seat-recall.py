import json,itertools,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from server import Worker
from export_lessons import export
cases=[]
for noun,context,(value,slot),template,prefix in itertools.product(
 ['صندلی','صندلی راننده','صندلیم'],['حالت','موقعیت','وضعیت','حافظه'],
 [('یک','driver'),('دو','2'),('سه','3'),('۱','driver'),('۲','2'),('۳','3')],
 ['{n} رو بذار تو {c} {v}','{n} را روی {c} {v} تنظیم کن','{n} رو به {c} {v} برگردون','{n} رو ببر تو {c} {v}','{n} رو تو {c} {v} قرار بده'],['','لطفا ']):
 cases.append(dict(text=prefix+template.format(n=noun,c=context,v=value),action='RECALL_SEAT_POSITION',parameters={'seat':'driver','slot':slot}))
for t in ['صندلی رو به حالت رانندگی برگردون','حالت رانندگی صندلی رو بازیابی کن','صندلی رو بذار تو حالت رانندگی','صندلی رو برگردون سر جای رانندگی']:
 cases.append(dict(text=t,action='RECALL_SEAT_POSITION',parameters={'seat':'driver','slot':'driver'}))
for t in ['صندلی شاگرد رو بذار تو حالت دو','صندلی رو بذار تو حالت چهار','صندلی رو بذار تو حالت','صندلی رو تو موقعیت صفر قرار بده','صندلی رو بذار روی دو و سه']:
 cases.append(dict(text=t,action='UNKNOWN',parameters={}))
cases.append(dict(text='صندلی رو بذار تو حالت گرم',action='SET_SEAT_HEATING',parameters={'seat':'driver','enabled':True}))
w=Worker();results=[]
for row in cases:
 a=w.recognize(row['text']);results.append(dict(**row,actual=a,correct=a['action']==row['action'] and a['parameters']==row['parameters']))
w.close();stage=sys.argv[1];report=dict(count=len(results),correct=sum(r['correct'] for r in results),results=results)
(ROOT/f'practice/seat-recall-{stage}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(stage,report['correct'],'/',report['count'])
if stage=='rules':
 assert report['correct']==report['count'],[r for r in results if not r['correct']][:6]
 print('Export',export(json.loads((ROOT/'lessons.json').read_text())+cases)['unique'])
