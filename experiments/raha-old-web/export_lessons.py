"""Export saved annotations, not BERT weights. Latest correction wins per exact text."""
import base64, hashlib, json, re, struct
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parents[1]
SOURCE=REPO/'experiments/raha-parsbert-web/artifacts/compact-web/feedback-head.safetensors'
TARGET=REPO/'voyah-iran/raha-core/src/main/resources/raha-old-lessons.tsv'
def normalize(s):
 s=s.lower().replace('ي','ی').replace('ك','ک').replace('ۀ','ه')
 s=s.translate(str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩','01234567890123456789'))
 s=re.sub(r'\s+',' ',re.sub(r'[\u200c\u200e\u200f،,!؛:؟?]+',' ',re.sub(r'[\u064B-\u065F\u0670]','',s))).strip()
 return re.sub(r'(?<!\S)چراغ(?: های| ای| ها|های|ها|ای)?(?!\S)','چراغ',s)
def b64(s):return base64.b64encode(s.encode()).decode()
def export(rows):
 latest={}; skipped=[]
 for r in rows:
  if not isinstance(r.get('parameters'),dict):skipped.append(r);continue
  r=dict(r);r['parameters']=dict(r['parameters'])
  if r['action'] in ['SET_FRONT_DEFROSTER','SET_REAR_DEFROSTER']:
   r['parameters']['window']='front' if r['action']=='SET_FRONT_DEFROSTER' else 'rear';r['action']='SET_DEFROSTER'
  if r['action']=='VENT_SUNROOF':r['action']='VENT_WINDOW';r['parameters']['window']='sunroof'
  latest[normalize(r['text'])]=r
 lines=[]
 for text,r in sorted(latest.items()):
  fields=[b64(text),r['action']]
  for k,v in sorted(r['parameters'].items()):
   if isinstance(v,bool):t='b';v=str(v).lower()
   elif isinstance(v,(int,float)):t='n';v=str(v)
   elif isinstance(v,str):t='s'
   else:raise ValueError((text,k,v))
   fields.append(k+':'+t+':'+b64(v))
  lines.append('\t'.join(fields))
 tmp=TARGET.with_suffix('.tmp');tmp.write_text('\n'.join(lines)+'\n');tmp.replace(TARGET)
 (ROOT/'lessons.json').write_text(json.dumps(list(latest.values()),ensure_ascii=False,separators=(',',':')))
 return dict(source_rows=len(rows),unique=len(latest),skipped=skipped,sha256=hashlib.sha256(TARGET.read_bytes()).hexdigest())
if __name__=='__main__':
 import sys
 if (ROOT/'lessons.json').exists() and '--replace-lessons' not in sys.argv:
  raise SystemExit('Lessons already exist; refusing to overwrite subsequent corrections. Use --replace-lessons for an intentional fresh migration.')
 with SOURCE.open('rb') as f:
  n=struct.unpack('<Q',f.read(8))[0];m=json.loads(f.read(n))['__metadata__']
 rows=json.loads(m['feedback']);report=export(rows)
 report['source_revision']='version-2.1.1';report['source_checkpoint_sha256']=hashlib.sha256(SOURCE.read_bytes()).hexdigest()
 (ROOT/'migration-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False))
