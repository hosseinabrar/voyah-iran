# Python 2.7, factory Yocto runtime. Diagnostic only; NOT a production backend.
# Uses only the verified live ABI, never archived libraries. No auth bypass.
from __future__ import print_function
import hashlib, sys, os, time
assert len(sys.argv)==1, 'No arguments or vehicle actions supported'
with open('/usr/local/lib/TBox-experimental-heartbeat/enabled') as marker:
 assert marker.read().strip()=='MCU_ONLY_EXPERIMENTAL sha256=3168d60dc85c07d91266543a1ce273cc28e93d824dfaea6fbb279104e4ff919f', 'Experimental compatibility is not enabled'
for name, expected in [
 ('libvns.so','c12b887d5981052f1b44f3c583e5e60953d674596676b45e6b78f6f49d75729c'),
 ('libhcore.so','543c6fdb1b70921bc17ac71ad7738e4d5772d187d29cdd5abd0565354b2c3b72')]:
 with open('/mnt/hirain_app/usr/lib/'+name,'rb') as f:
  assert hashlib.sha256(f.read()).hexdigest()==expected, 'Unsupported native ABI: '+name
import ctypes as C
P=C.c_void_p; I=C.c_int; U=C.c_uint; B=C.c_ubyte
v=C.CDLL('/mnt/hirain_app/usr/lib/libvns.so',mode=C.RTLD_GLOBAL)
def fn(n,args,ret=I):
 f=getattr(v,n);f.argtypes=args;f.restype=ret;return f
obj=C.create_string_buffer(32)
fn('_ZN8ProxyLib8VnsProxyC1Ev',[P],None)(obj)
h=fn('_ZN8ProxyLib8VnsProxy7vnsOpenEv',[P],P)(obj)
assert h, 'VNS open failed'
M=C.CFUNCTYPE(I,P,P,I)
@M
def manager(dst,src,op):
 if op==2:C.memmove(dst,src,16)
 return 0
D=C.CFUNCTYPE(None,P,P,P,P,P)
@D
def data(a,s,t,p,u): pass
state={'connected':False, 'error':False}
O=C.CFUNCTYPE(None,P,P,P)
@O
def opened(a,s,u):
 state['connected']=True
 print('DONE: OEM session connected');sys.stdout.flush()
E=C.CFUNCTYPE(None,P,P,P,P,P)
@E
def error(a,s,c,msg,u):
 state['error']=True
 print('ERROR: OEM session',C.cast(c,C.POINTER(I))[0]);sys.stdout.flush()
def function(inv):
 a=(C.c_void_p*4)();a[2]=C.cast(manager,P).value;a[3]=C.cast(inv,P).value;return a
fs=[function(data),function(opened),function(error)]
name=C.create_string_buffer('VoyahExperimentalHeartbeat'+str(os.getpid()));st=(C.c_ulonglong*4)(C.addressof(name),len(name.value),len(name.value),0)
s=fn('_ZN8ProxyLib8VnsProxy14vnsSessionOpenEPvRKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEESt8functionIFvS1_14_vns_data_typeR18_vns_data_package_S1_EESA_IFvS1_S1_EESA_IFvS1_iPKcS1_EES1_',[P,P,P,P,P,P,P],P)(obj,h,st,fs[0],fs[1],fs[2],None)
assert s, 'VNS session failed'

# Only OEM Linux heartbeat. No vehicle control, wake lock, or watchdog disable.
C.c_int.in_dll(v, '_ZN5Codec10MCUEncoder17mControlDirectionE').value=2
beat=fn('_ZN8ProxyLib8VnsProxy16vnsSendHeartbeatEPv',[P,P],B)
for attempt in range(30):
 if state['connected'] or state['error']: break
 time.sleep(1)
assert state['connected'] and not state['error'], 'OEM session unavailable'
i=0
while True:
 if state['error'] or not beat(obj,s):
  raise SystemExit('ERROR: OEM heartbeat submission failed')
 if i % 6 == 0:
  print('DONE: Experimental MCU heartbeat submitted; count='+str(i+1));sys.stdout.flush()
 i+=1
 time.sleep(10)
