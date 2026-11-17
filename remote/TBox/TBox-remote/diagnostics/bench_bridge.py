# Python 2.7. Temporary, local-only bench adapter; never installed by package.
from __future__ import print_function
import os, socket, struct, subprocess, time
SOCK='/run/TBox-remote-oem.sock'
def exact(c,n):
 b=b''
 while len(b)<n:
  x=c.recv(n-len(b))
  if not x: raise ValueError('Closed request')
  b+=x
 return b
def vint(b,i):
 v=0
 for shift in range(0,70,7):
  x=ord(b[i:i+1]);i+=1;v|=(x&127)<<shift
  if x<128:return v,i
 raise ValueError('Invalid varint')
def parse(b):
 out={};i=0
 while i<len(b):
  tag,i=vint(b,i)
  if tag&7==0:out[tag>>3],i=vint(b,i)
  elif tag&7==2:
   n,i=vint(b,i);i+=n
   if i>len(b):raise ValueError('Invalid length')
  else:raise ValueError('Invalid wire type')
 return out
def enc(v):
 b=b''
 while v>127:b+=struct.pack('B',(v&127)|128);v>>=7
 return b+struct.pack('B',v)
def response(c,fields,err):
 # Never claim OEM completion: even a matching cached state needs live CAN QA.
 b=b''.join(enc(k*8)+enc(v) for k,v in [(1,fields[1]),(2,fields[2]),(3,1),(4,err)])
 c.sendall(struct.pack('<I',len(b))+b)
if os.path.exists(SOCK):os.unlink(SOCK)
s=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);s.bind(SOCK);os.chmod(SOCK,0o600);s.listen(1)
print('BENCH ONLY: authenticated hazard and climate preparation; other commands refused')
while True:
 c,_=s.accept();c.settimeout(2)
 try:
  n,ms=struct.unpack('<II',exact(c,8))
  if not 0<n<=4096 or not 0<ms<=10000:raise ValueError('Invalid envelope')
  f=parse(exact(c,n));action={(5,1):'hazard-on',(48,1):'climate-on'}.get((f.get(2),f.get(3)))
  if not action:response(c,f,250);continue
  # No backlog, no retries. Budget too short for the bounded probe is refused.
  if ms<6500:response(c,f,251);continue
  print('BENCH REQUEST',f[1],action,ms)
  env=dict(os.environ)
  with open('/proc/uptime') as clock: env['VI_BENCH_DEADLINE']=str(float(clock.read().split()[0])+ms/1000.0-0.3)
  env['LD_LIBRARY_PATH']='/mnt/hirain_app/usr/lib:/usr/lib64'
  p=subprocess.Popen(['python','-u','/tmp/vi-native-bench.py','--wake','--action',action],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
  # Sequential process retains the slot through cleanup even if client times out.
  output=p.communicate()[0];code=p.returncode
  print(output.decode('utf-8','replace'));print('BENCH EXIT',f[1],code)
  err=253
  if b'Submitted authenticated request' in output: err=254
  if b'REMOTE RESULT id,status,error' in output: err=255
  if code==0: err=252
  response(c,f,err)
 except Exception as e:print('BENCH ERROR',str(e))
 finally:c.close()
