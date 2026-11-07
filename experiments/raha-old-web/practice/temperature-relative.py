"""Reviewed compositional commands: explicit temperature, direction, optional amount."""
import itertools,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from server import Worker
from export_lessons import export
cases=[]
for target,(operation,sign),marker,(prefix,ending),(amount,n) in itertools.product(
 ['دمای کابین','دمای اتاق','دمای ماشین','دمای داخل ماشین'],
 [('ببر بالا',1),('بیار بالا',1),('ببر پایین',-1),('بیار پایین',-1),('بیشتر کن',1),('کمتر کن',-1)],
 [' رو ',' '],[('',''),('لطفا ','')],[('',1),('یک درجه ',1),('دو درجه ',2)]):
 cases.append(dict(text=f'{prefix}{target}{marker}{amount}{operation}{ending}',action='ADJUST_CABIN_TEMPERATURE',parameters={'zone':'front','delta':n*sign}))
path=ROOT/'practice/temperature-relative-cases.json';path.write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n')
worker=Worker();results=[]
for row in cases:
 actual=worker.recognize(row['text']);results.append(dict(**row,actual=actual,correct=actual['action']==row['action'] and actual['parameters']==row['parameters']))
worker.close()
stage=sys.argv[1] if len(sys.argv)>1 else 'baseline'
report={'count':len(results),'correct':sum(r['correct'] for r in results),'results':results}
(ROOT/f'practice/temperature-relative-{stage}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(stage,report['correct'],'/',report['count'])
if stage=='rules':
 assert report['correct']==report['count'],'Do not teach around a rule failure'
 rows=json.loads((ROOT/'lessons.json').read_text());print('Export:',export(rows+cases)['unique'])
