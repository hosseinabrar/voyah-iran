"""Explicit simulation against the paired gateway. Never sends a vehicle SMS."""
import json,subprocess,time,urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[2]
url='http://127.0.0.1:18781'
def state():return json.load(urllib.request.urlopen(url+'/api/state',timeout=3))
def command():
 s=state();r=urllib.request.Request(url+'/api/command',data=json.dumps({'command':'10:1'}).encode(),headers={'Content-Type':'application/json','Origin':url,'X-Test-Token':s['csrf']});return json.load(urllib.request.urlopen(r,timeout=13))
assert not state()['connected'], 'Do not run simulation while a real vehicle is connected'
start=time.monotonic();r=command();elapsed=time.monotonic()-start
assert r['status']=='timeout' and 9.5<elapsed<12,(r,elapsed)
print('PASS: offline command timeout',round(elapsed,2),flush=True)
log=(root/'.local/simulation.log').open('ab')
p=subprocess.Popen([str(root/'.local/agent-simulation'),'-simulate','-state',str(root/'.local/simulation-state')],stdout=log,stderr=log)
start=time.monotonic()
try:
 for _ in range(30):
  s=state()
  if s['connected']:break
  time.sleep(.25)
 assert s['connected'] and s['mode']=='simulation',s
 assert 50000<s['remaining_ms']<=60000,s['remaining_ms']
 r=command();assert r['status']=='simulated',r
 print('PASS: encrypted request/result through deployed gateway; no vehicle action',flush=True)
 while time.monotonic()-start<64:time.sleep(.5)
 assert not state()['connected'],'Lease failed to expire'
 start=time.monotonic();r=command();elapsed=time.monotonic()-start
 assert r['status']=='timeout' and 9.5<elapsed<12,(r,elapsed)
 print('PASS: 60-second expiry and subsequent 10-second timeout',flush=True)
finally:p.terminate();p.wait(timeout=5);log.close()
