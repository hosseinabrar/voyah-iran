import json,itertools,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from server import Worker
from export_lessons import export
names=['چراغ','چراغای','چراغ های','چراغ جلو','چراغ جلوی ماشین','چراغ بیرون','چراغ بیرونی','چراغ های بیرونی','چراغای بیرون ماشین','چراغ های بیرونی ماشین','چراغ بیرون خودرو','چراغ های جلو ماشین','چراغ خارجی','چراغای خارجی خودرو','چراغ ماشین','چراغ های بیرونی جلو']
operations=[('روشن کن','lowBeam'),('خاموش کن','off'),('روشنش کن','lowBeam'),('خاموشش کن','off'),('بذار روی اتو','auto'),('اتوماتیک کن','auto'),('روی خودکار تنظیم کن','auto'),('بذار رو نور پایین','lowBeam'),('روی چراغ کوچک تنظیم کن','position'),('بذار روی کوچک','position'),('بذار روی دی لایت','position')]
cases=[dict(text=f'{prefix}{name}{marker}{op}',action='SET_HEADLIGHT_MODE',parameters={'mode':mode}) for name,(op,mode),prefix,marker in itertools.product(names,operations,['','لطفا '],[' رو ',' '])]
cases += [dict(text=t,action='UNKNOWN',parameters={}) for t in ['چراغ بیرونی رو گرم کن','چراغ بیرونی رو بذار روی موزیک','چراغ بیرونی رو روشن و خاموش کن']]
w=Worker();results=[]
for r in cases:
 a=w.recognize(r['text']);results.append(dict(**r,actual=a,correct=a['action']==r['action'] and a['parameters']==r['parameters']))
w.close();stage=sys.argv[1];report=dict(count=len(results),correct=sum(r['correct'] for r in results),results=results)
(ROOT/f'practice/exterior-light-{stage}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(stage,report['correct'],'/',report['count'])
if stage=='rules':
 assert report['correct']==report['count'],[r for r in results if not r['correct']][:6]
 print('Export',export(json.loads((ROOT/'lessons.json').read_text())+cases)['unique'])
