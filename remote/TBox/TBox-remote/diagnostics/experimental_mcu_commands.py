# Python 2.7, factory Yocto runtime. Diagnostic only; NOT a production backend.
# Uses only the verified live ABI, never archived libraries. No auth bypass.
from __future__ import print_function
import hashlib, subprocess, atexit, sys, os, time
with open('/usr/local/lib/TBox-experimental-heartbeat/enabled') as marker:
 assert marker.read().strip() == 'MCU_ONLY_EXPERIMENTAL sha256=3168d60dc85c07d91266543a1ce273cc28e93d824dfaea6fbb279104e4ff919f', 'Unsupported experimental MCU marker'
class Options: pass
args=Options(); args.action=None; args.wake=False
tokens=iter(sys.argv[1:])
for token in tokens:
 if token=='--wake': args.wake=True
 elif token=='--action': args.action=next(tokens)
 else: raise SystemExit('Unknown argument: '+token)
if args.action not in ['window-open','hazard-on','climate-on']:
 raise SystemExit('Unsupported diagnostic action')
for name, expected in [
 ('libvns.so','c12b887d5981052f1b44f3c583e5e60953d674596676b45e6b78f6f49d75729c'),
 ('libhcore.so','543c6fdb1b70921bc17ac71ad7738e4d5772d187d29cdd5abd0565354b2c3b72')]:
 with open('/mnt/hirain_app/usr/lib/'+name,'rb') as f:
  assert hashlib.sha256(f.read()).hexdigest()==expected, 'Unsupported native ABI: '+name
holds=[]
def pm(method, signature, values):
 command=['busctl','--address=unix:path=/tmp/.hirain-dbus','--no-pager','--timeout=3',
  'call','gdbus.hcommon.powermanager','/gdbus/hcommon/powermanager',
  'gdbus.hcommon.powermanager',method,signature]+[str(v) for v in values]
 return subprocess.check_output(command).strip()==b'b true'
def release():
 for kind in reversed(holds):
  try: pm('release_wakelock','iis',[kind,os.getpid(),'VoyahNativeBench'])
  except Exception as e: print('Wake release failed; timed lock expires:',e)
 del holds[:]
atexit.register(release)
if args.wake:
 for kind in [1,2]:
  assert pm('apply_wakelock','iisi',[kind,os.getpid(),'VoyahNativeBench',25]), 'Wake request refused'
  holds.append(kind)
  time.sleep(2)
import ctypes as C,time,os,struct,binascii
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
def data(a,s,t,p,u):
 n=C.cast(t,C.POINTER(I))[0]
 if n==21: print('MCU RECEIPT ONLY id,status,error',struct.unpack('<HBB',C.string_at(p+0x148,4)));import sys;sys.stdout.flush()
O=C.CFUNCTYPE(None,P,P,P)
@O
def opened(a,s,u):print('CONNECTED');import sys;sys.stdout.flush()
E=C.CFUNCTYPE(None,P,P,P,P,P)
@E
def error(a,s,c,msg,u):print('ERROR',C.cast(c,C.POINTER(I))[0]);import sys;sys.stdout.flush()
def function(inv):
 a=(C.c_void_p*4)();a[2]=C.cast(manager,P).value;a[3]=C.cast(inv,P).value;return a
fs=[function(data),function(opened),function(error)]
name=C.create_string_buffer('VoyahNativeBench'+str(os.getpid()));st=(C.c_ulonglong*4)(C.addressof(name),len(name.value),len(name.value),0)
s=fn('_ZN8ProxyLib8VnsProxy14vnsSessionOpenEPvRKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEESt8functionIFvS1_14_vns_data_typeR18_vns_data_package_S1_EESA_IFvS1_S1_EESA_IFvS1_iPKcS1_EES1_',[P,P,P,P,P,P,P],P)(obj,h,st,fs[0],fs[1],fs[2],None)
assert s, 'VNS session failed'
mask=(C.c_ulonglong*8)(0x7ff,0x1f,0x7f,0xfe,0x7fff,7,1,15)
print('MASK',fn('_ZN8ProxyLib8VnsProxy26vnsSetSubscribeMcuDataMaskEPvRK17__mcu_data_mask__',[P,P,P],B)(obj,s,mask))
time.sleep(1)
def read(signal):
 n=C.create_string_buffer(signal);t=(C.c_ulonglong*4)(C.addressof(n),len(signal),len(signal),0)
 val=C.c_double(-999);raw=C.c_long(-999)
 ok=fn('_ZN8ProxyLib8VnsProxy16vnsGetSignalDataEPvRKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEERdRl',[P,P,P,P,P],B)(obj,s,t,C.byref(val),C.byref(raw))
 print('SIGNAL',signal,ok,val.value,raw.value)
 return raw.value if ok else None



# Callback acknowledgement is never treated as successful actuation.
core=C.CDLL('/mnt/hirain_app/usr/lib/libhcore.so',mode=C.RTLD_GLOBAL)
libc=C.CDLL(None);libc.malloc.argtypes=[C.c_size_t];libc.malloc.restype=P
keep=[]
def send(payload, request, can_id=0x58):
 if os.environ.get('VI_BENCH_DEADLINE'):
  with open('/proc/uptime') as clock: now=float(clock.read().split()[0])
  assert now < float(os.environ['VI_BENCH_DEADLINE']), 'Command expired; nothing further sent'
 ba=libc.malloc(48)
 assert ba, 'Allocation failed'
 ctor=getattr(core,'_ZN6hirain9ByteArrayC1EPKci');ctor.argtypes=[P,P,I];ctor.restype=None
 buf=C.create_string_buffer(binascii.unhexlify(payload));ctor(ba,buf,8)
 inc=getattr(core,'_ZNK6hirain7RefBase9incStrongEPKv');inc.argtypes=[P,P];inc.restype=None
 inc(ba+32,ba)
 getdata=getattr(core,'_ZN6hirain9ByteArray4dataEv');getdata.argtypes=[P];getdata.restype=P
 assert C.string_at(getdata(ba),8)==buf.raw[:8], 'Payload mismatch'
 # Russian MCU expects control=2 and a 9-byte item header; X uses 10.
 packet=b'\x03\x06\x02'+struct.pack('<HHBHBH',25,request,1,1,5,17)+struct.pack('<HBBBI',8,1,2,3,can_id)+buf.raw[:8]
 assert len(packet)==30 and packet[18:22]==struct.pack('<I',can_id)
 wire=C.create_string_buffer(packet);wireba=libc.malloc(48)
 ctor(wireba,wire,len(packet));inc(wireba+32,wireba)
 assert C.string_at(getdata(wireba),30)==packet
 sp=P(wireba);keep.append((wire,wireba,sp))
 kind=fn('_ZN5Codec10MCUEncoder17getSendTypeOfDataEh',[B],I)(6)
 assert kind==25, 'Unexpected protocol enum'
 call=fn('_ZN8ProxyLib8VnsProxy14SendMCURawDataEPviRN6hirain2spINS2_9ByteArrayEEE',[P,P,I,P],B)
 assert call(obj,s,kind,C.byref(sp)), 'VNS rejected submission'
 print('Submitted compatible authenticated request',request,'packet',binascii.hexlify(packet));sys.stdout.flush()
def confirmed(signal, expected, seconds=6):
 for tick in range(int(seconds*2)):
  if os.environ.get('VI_BENCH_DEADLINE'):
   with open('/proc/uptime') as clock: now=float(clock.read().split()[0])
   if now >= float(os.environ['VI_BENCH_DEADLINE'])-1.0: return False
  time.sleep(.5)
  if read(signal)==expected: return True
 return False
# Owner authorizes parked vehicle testing; recheck physical conditions before use. No power/lock/drive/closing commands.
# Cache is not valid confirmation with this MCU/MPU protocol mismatch.
cases={
 'hazard-on':('0000000400000000','bcm_hazardLightSts_0x2A1_1'),
 'window-open':('0000000000000300','BCM_FLWindowHorizontalSts_0x3D2_1'),
 'climate-on':('0000001000000000','ac_systemOnOffSts_0x3B6_1')}
assert args.action in cases
payload,signal=cases[args.action]
try:
 print('TEST',args.action,'wall_time',time.time());sys.stdout.flush()
 read(signal)
 with open('/proc/uptime') as f: deadline=float(f.read().split()[0])+12
 os.environ['VI_BENCH_DEADLINE']=str(deadline)
 send(payload,(os.getpid()%60000)+1)
 for tick in range(10):
  time.sleep(.5);read(signal);sys.stdout.flush()
 print('TEST COMPLETE; cache readings alone do not confirm actuation')
finally:
 fn('_ZN8ProxyLib8VnsProxy15vnsSessionCloseEPvS1_',[P,P,P],None)(obj,h,s)
 fn('_ZN8ProxyLib8VnsProxy8vnsCloseEPv',[P,P],None)(obj,h)
 release();sys.stdout.flush()
os._exit(0)
