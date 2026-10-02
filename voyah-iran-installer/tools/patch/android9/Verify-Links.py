from pathlib import Path
import re
import sys
root=Path(sys.argv[1]); helpers=Path(__file__).resolve().parent/'helpers'
def read(name):
 p=helpers/(name[1:-1]+'.smali')
 if not p.exists():p=root/(name[1:-1]+'.smali')
 return p.read_text() if p.exists() else None
def member(owner,signature,kind):
 s=read(owner)
 if s is None:return None
 for line in s.splitlines():
  if line.startswith('.'+kind+' ') and line.split(' = ')[0].endswith(' '+signature):return owner,line
 m=re.search(r'^\.super (.+)$',s,re.M)
 return member(m[1],signature,kind) if m else None
checks=0
for p in helpers.rglob('*.smali'):
 s=p.read_text();caller='L'+p.relative_to(helpers).as_posix()[:-6]+';'
 for l in s.splitlines():
  if not re.match(r'\s*(invoke-|[is][gp]et|[is]put)',l):continue
  m=re.search(r'(Lcom/android/server/[^;]+;)->([^\s,]+)',l)
  if not m:continue
  owner,signature=m.groups();kind='method' if '(' in signature else 'field'
  got=member(owner,signature,kind)
  assert got,(p,l)
  declaring,line=got
  assert ' public ' in line or caller.rsplit('/',1)[0]==declaring.rsplit('/',1)[0],(p,'inaccessible',line)
  assert ' private ' not in line or caller==declaring,(p,'private',line)
  checks+=1
print('Verified',checks,'server member references and access levels')


