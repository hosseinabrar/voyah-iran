from pathlib import Path
import struct,json,zipfile,re,sys
if len(sys.argv) != 3: raise SystemExit("Usage: h97a_sdk_inventory.py DUMP_DIRECTORY OUTPUT_JSON")
OUT=Path(sys.argv[2]); result={}
def parse(d,src):
 u=lambda o:struct.unpack_from('<I',d,o)[0];h=lambda o:struct.unpack_from('<H',d,o)[0]
 base=u(108) if d[:4]==b'cdex' else 0
 def leb(o):
  v=0;b=0
  while True:
   n=d[o];o+=1;v|=(n&127)<<b;b+=7
   if n<128:return v,o
 def string(o):
  _,o=leb(o+base);return d[o:d.index(b'\0',o)].decode('utf8','replace')
 ss=[string(u(u(60)+i*4)) for i in range(u(56))];tt=[ss[u(u(68)+i*4)] for i in range(u(64))]
 prot=[]
 for i in range(u(72)):
  o=u(76)+12*i;p=u(o+8);args=[]
  if p:args=[tt[h(base+p+4+j*2)] for j in range(u(base+p))]
  prot.append('('+''.join(args)+')'+tt[u(o+4)])
 mm=[(tt[h(u(92)+8*i)],ss[u(u(92)+8*i+4)],prot[h(u(92)+8*i+2)]) for i in range(u(88))]
 ff=[(tt[h(u(84)+8*i)],ss[u(u(84)+8*i+4)],tt[h(u(84)+8*i+2)]) for i in range(u(80))]
 for i in range(u(96)):
  o=u(100)+32*i;c=tt[u(o)][1:-1].replace('/','.');off=u(o+24)
  if not c.startswith(('com.qinggan.','com.pateo.','com.android.server.','android.app.IActivity','android.app.Activity','android.view.KeyEvent')):continue
  item={'source':src,'methods':[],'fields':[]}
  if off:
   p=off+base;counts=[]
   for _ in range(4):v,p=leb(p);counts.append(v)
   for num in counts[:2]:
    idx=0
    for _ in range(num):delta,p=leb(p);flags,p=leb(p);idx+=delta;item['fields'].append(ff[idx][1]+':'+ff[idx][2])
   for num in counts[2:]:
    idx=0
    for _ in range(num):delta,p=leb(p);flags,p=leb(p);code,p=leb(p);idx+=delta;item['methods'].append(mm[idx][1]+mm[idx][2])
  result.setdefault(c,[]).append(item)
root=Path(sys.argv[1])
for p in root.rglob('*'):
 if p.suffix=='.apk':
  with zipfile.ZipFile(p) as z:
   for n in z.namelist():
    if re.fullmatch(r'classes\d*\.dex',n):parse(z.read(n),str(p.relative_to(root))+':'+n)
 elif p.suffix=='.vdex' and ('framework' in p.parts) and 'arm' not in p.parts and 'arm64' not in p.parts:
  v=p.read_bytes();i=v.find(b'cdex001\0')
  if i>=0:parse(v[i:],str(p.relative_to(root)))
OUT.write_text(json.dumps(result,ensure_ascii=False,indent=1),encoding='utf8')
print('Indexed classes:',len(result),'Output:',OUT)
