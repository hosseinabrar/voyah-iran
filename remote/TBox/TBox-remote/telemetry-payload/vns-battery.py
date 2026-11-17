#!/usr/bin/env python
# Python 2.7/3 compatible. Native VNS ABI from the X and C direct-command probes.
from __future__ import print_function
import ctypes as C, os, sys, json, hashlib, struct, time, threading
P=C.c_void_p; I=C.c_int; B=C.c_ubyte
PROFILES={
 ('c12b887d5981052f1b44f3c583e5e60953d674596676b45e6b78f6f49d75729c','543c6fdb1b70921bc17ac71ad7738e4d5772d187d29cdd5abd0565354b2c3b72'):(32,False),
 ('193d4f764c952c7e177f4afae92634402ae237a439ae1d5693c0ba6b5f3a1420','920d73326a61c10faf218783595b18120a5f182daaecdf2c5ceb31254be33828'):(24,True)}
def digest(p):
 with open(p,'rb') as f:return hashlib.sha256(f.read()).hexdigest()
def run():
 base=os.environ.get('VI_VNS_LIB','/mnt/hirain_app/usr/lib')
 offset,old=PROFILES[(digest(base+'/libvns.so'),digest(base+'/libhcore.so'))]
 v=C.CDLL(base+'/libvns.so',mode=C.RTLD_GLOBAL)
 core=C.CDLL(base+'/libhcore.so',mode=C.RTLD_GLOBAL)
 def fn(lib,n,args,ret=I):
  f=getattr(lib,n);f.argtypes=args;f.restype=ret;return f
 obj=C.create_string_buffer(256)
 fn(v,'_ZN8ProxyLib8VnsProxyC1Ev',[P],None)(obj)
 h=fn(v,'_ZN8ProxyLib8VnsProxy7vnsOpenEv',[P],P)(obj)
 if not h:raise RuntimeError('vnsOpen failed')
 connected=threading.Event();receipt=threading.Event();result=[];request=(os.getpid()%60000)+1
 M=C.CFUNCTYPE(I,P,P,I)
 @M
 def manager(dst,src,op):
  if op==2:C.memmove(dst,src,16)
  return 0
 D=C.CFUNCTYPE(None,P,P,P,P,P)
 @D
 def data(a,s,t,p,u):
  if not old and C.cast(t,C.POINTER(I))[0]==21:
   rid,status,error=struct.unpack('<HBB',C.string_at(p+0x148,4))
   if rid==request:result[:]=[status,error];receipt.set()
 O=C.CFUNCTYPE(None,P,P,P)
 @O
 def opened(a,s,u):connected.set()
 E=C.CFUNCTYPE(None,P,P,P,P,P)
 @E
 def error(a,s,c,msg,u):pass
 def function(inv):
  a=(P*4)();a[2]=C.cast(manager,P).value;a[3]=C.cast(inv,P).value;return a
 fs=[function(data),function(opened),function(error)]
 name=C.create_string_buffer(('VIBattery'+str(os.getpid())).encode('ascii'))
 st=(C.c_ulonglong*4)(C.addressof(name),len(name.value),len(name.value),0)
 session=fn(v,'_ZN8ProxyLib8VnsProxy14vnsSessionOpenEPvRKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEESt8functionIFvS1_14_vns_data_typeR18_vns_data_package_S1_EESA_IFvS1_S1_EESA_IFvS1_iPKcS1_EES1_',[P,P,P,P,P,P,P],P)(obj,h,st,fs[0],fs[1],fs[2],None)
 if not session or not connected.wait(2):raise RuntimeError('VNS session unavailable')
 fn(v,'_ZN8ProxyLib8VnsProxy14vnsSendCanDataEPvRK12__can_data__',[P,P,P],B)
 if '--probe' in sys.argv:return {'status':'ready','abi':'archive' if old else 'X'}
 q=json.loads(sys.stdin.readline(4096))
 if q.get('canID')!=126 or len(q.get('data',[]))!=8 or any(type(x)!=int or not 0<=x<=255 for x in q['data']):raise ValueError('invalid battery frame')
 if not 0<q.get('deadline',0)-time.time()*1000<=7000:raise ValueError('expired command')
 # The charge frame is a settings CAN message, not a BCM remote-auth command.
 # CANDataHeader is 16 bytes; __can_data__ contains the data pointer at +16.
 payload=C.create_string_buffer(struct.pack('8B',*q['data']))
 packet=C.create_string_buffer(struct.pack('<IIIHHQ',126,0,0,1,8,C.addressof(payload)))
 if time.time()*1000>=q['deadline']:raise ValueError('expired command')
 send=fn(v,'_ZN8ProxyLib8VnsProxy14vnsSendCanDataEPvRK12__can_data__',[P,P,P],B)
 # OEM method returns an error code (zero on submission), not boolean success.
 result=send(obj,session,packet)
 return {'status':'accepted' if result==0 else 'failed','nativeCode':int(result)}

if __name__=='__main__':
 try:r=run()
 except Exception as e:r={'status':'failed','detail':str(e)}
 print('VI_RESULT '+json.dumps(r));sys.stdout.flush()
 # Native callback objects live until process exit (no ctypes teardown race).
 os._exit(0 if r['status'] in ('ready','accepted') else 1)
