import itertools,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from server import Worker
from export_lessons import export
cases=[]
for cover,target,verb,marker,prefix in itertools.product(['در','درب','درپوش','محفظه'],['باک','باک بنزین','باک سوخت','بنزین','سوخت'],['باز کن','بازش کن','باز بشه','باز شود','باز کردن'],[' رو ',' '],['','لطفا ']):
 cases.append(dict(text=f'{prefix}{cover} {target}{marker}{verb}',action='OPEN_FUEL_FLAP',parameters={}))
w=Worker();results=[]
for r in cases:
 a=w.recognize(r['text']);results.append(dict(**r,actual=a,correct=a['action']==r['action'] and a['parameters']==r['parameters']))
w.close();stage=sys.argv[1];report=dict(count=len(results),correct=sum(r['correct'] for r in results),results=results)
(ROOT/f'practice/fuel-cover-{stage}.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(stage,report['correct'],'/',report['count'])
if stage=='rules':
 assert report['correct']==report['count'],[r for r in results if not r['correct']][:5]
 print('Export',export(json.loads((ROOT/'lessons.json').read_text())+cases)['unique'])
