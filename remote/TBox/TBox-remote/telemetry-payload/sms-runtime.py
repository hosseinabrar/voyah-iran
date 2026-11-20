#!/usr/bin/env python
# Python 2.7/3. Hash-guarded, reversible RAM bind overlay; never flashes firmware.
from __future__ import print_function
import fcntl, binascii, hashlib, json, os, struct, subprocess, sys, time
BASE='/data/vi-remote/sms-runtime'
X_ORIGINAL='25b8787e5bfe471abb4329c4128e942dab89544ffab4b7aca3e8901b105ed721'
X_SIGNAL='0396fb7a3d1cf19866e4d02bb6043590dd433873fc38d3650b8def21e647654a'
X_SMS='272602c471fb3559aae44950a732546849da124183f91dd6014edf1a08e92276'
X_FINAL='f4ce52c96f78e8790753da5f5ab6519ace823698fdeeb6ee8c1bddb56233e1d0'
PROFILES={
 '5b0465d6dfdd1c7aaa74809776537d57bd161f9994957922083117750f285eef':(0x376ec,'4bd0ff97','967f85be725c63b1983fb95e5c1c100a8a3d29925a94ef4211ac80478622c76e'),
 '545d9f54988ed1442c3e47ae7133efc7f240fe67a96f5d541353825b6dc8b8ef':(0x373fc,'c7d0ff97','c3dc06abaa8af8b97c574993dde078bc8684ed317d46c7e282516727acf3d285'),
 'd1c3ff7748e6dc0a4d2ee5486049f89d4d39be123ba44755124473085b436532':(0x35bbc,'03d2ff97','9f4d06f7ad6c4a73a4eade0b20123a70be4ac3f89ac0676581b679f3e3da1749'),
 X_SMS:(0x42b3c,'4a9eff97',X_FINAL)}
FINAL=set(p[2] for p in PROFILES.values())
def digest(b):return hashlib.sha256(b).hexdigest()
def read(p):
 with open(p,'rb') as f:return f.read()
def write(p,b,mode=0o600):
 with open(p+'.new','wb') as f:f.write(b);f.flush();os.fsync(f.fileno())
 os.chmod(p+'.new',mode);os.rename(p+'.new',p)
def patch(source):
 h=digest(source)
 if h in FINAL:return source
 b=bytearray(source)
 if h in (X_ORIGINAL,X_SIGNAL):
  # Exact wrapper from native/build-sms-patch.py; Signal+SMS+original Voice init.
  wrapper=binascii.unhexlify('ffc300d1fd7b00a9fd030091e00701a9e21300f954eafe97e10f40f9e21340f99f4cff971e55ff97e10f40f9e21340f9ce52ff97e00741a9e21340f97258ff97fd7b40a9ffc30091c0035fd6')
  b[0x6df80:0x6df80+len(wrapper)]=wrapper
  struct.pack_into('<I',b,0x1ec0c,0x94000000|((0x6df80-0x1ec0c)//4))
  struct.pack_into('<Q',b,0xd0,0x6df80+len(wrapper));struct.pack_into('<Q',b,0xd8,0x6df80+len(wrapper))
  source=bytes(b) if sys.version_info[0]>=3 else str(b);h=digest(source)
  if h!=X_SMS:raise ValueError('SMS initialization patch mismatch')
 if h not in PROFILES:raise ValueError('Unsupported OEM SMS binary')
 offset,expected,out=PROFILES[h]
 if binascii.hexlify(b[offset:offset+4]).decode()!=expected:raise ValueError('SMS instruction mismatch')
 # Select the existing OEM UTF-8 -> UCS-2BE conversion (format 2), independent
 # of carrier cache overrides. Only replaces the sendSmsFormat readInt call.
 b[offset:offset+4]=binascii.unhexlify('40008052')
 result=bytes(b) if sys.version_info[0]>=3 else str(b)
 if digest(result)!=out:raise ValueError('SMS output hash mismatch')
 return result

def restart(name):
 # Factory process supervisor owns rild/hrild. Never terminate ql_rild/modem.
 try:pids=subprocess.check_output(['pidof',name]).decode().split()
 except subprocess.CalledProcessError:pids=[]
 for pid in pids:os.kill(int(pid),15)
def running_expected(target,expected):
 try:pids=subprocess.check_output(['pidof',os.path.basename(target)]).decode().split()
 except subprocess.CalledProcessError:return False
 try:return bool(pids) and all(digest(read('/proc/'+pid+'/exe'))==expected for pid in pids)
 except IOError:return False
def compatible():
 try:
  out=subprocess.check_output(['busctl','--address=unix:path=/tmp/.hirain-dbus','--no-pager','--timeout=2','introspect','gdbus.hcommon.rild','/gdbus/hcommon/rild','gdbus.hcommon.rild.sms']).decode()
  return '.sendSms' in out and '.receiveSms' in out
 except (OSError,subprocess.CalledProcessError):return False

def restore():
 if not os.path.exists(BASE+'/mount.json'):return
 state=json.loads(read(BASE+'/mount.json').decode());target=state['target']
 current=digest(read(target))
 if current!=state['patched']:
  if current in PROFILES or current in (X_ORIGINAL,X_SIGNAL):os.remove(BASE+'/mount.json');return
  raise ValueError('SMS overlay changed; refusing to unmount another owner')
 subprocess.check_call(['umount',target]);os.remove(BASE+'/mount.json');restart(os.path.basename(target))

def prepare():
 if not os.path.isdir(BASE):os.makedirs(BASE,0o700)
 target=next((p for p in ['/mnt/hirain_app/usr/bin/hrild','/mnt/hirain_app/usr/bin/rild'] if os.path.isfile(p)),None)
 if not target:raise ValueError('OEM SMS service missing')
 source=read(target);result=patch(source);changed=result!=source
 if changed:
  # The previous live overlay (including antenna patch) is preserved underneath.
  write(BASE+'/before-'+digest(source),source,0o700)
  write(BASE+'/rild-'+digest(result),result,0o700)
  subprocess.check_call(['mount','--bind',BASE+'/rild-'+digest(result),target])
  try:write(BASE+'/mount.json',json.dumps({'target':target,'patched':digest(result)}).encode())
  except Exception:subprocess.call(['umount',target]);raise
  restart(os.path.basename(target))
 for _ in range(20):
  if running_expected(target,digest(result)) and compatible():print('DONE: OEM SMS Unicode interface ready');return
  time.sleep(1)
 if changed:restore()
 raise ValueError('OEM SMS interface failed; new overlay rolled back')
def antenna_hooks(root='/usr/local/lib/TBox-antenna'):
 # Preserve installed APN/customization files. Only add lifecycle hooks at exact
 # known anchors, with backups; update checksums for the two changed scripts.
 if not os.path.isfile(root+'/connect.sh'):return
 updates={}
 for name in ('connect.sh','rollback.sh'):
  text=read(root+'/'+name).decode()
  if '# VI Remote SMS lifecycle' in text:continue
  anchor='. "$BASE/common.sh"\n'
  if text.count(anchor)!=1:raise ValueError('Unknown antenna lifecycle; cannot safely integrate SMS')
  hook='# VI Remote SMS lifecycle\nif [ -f /data/vi-remote/sms-runtime/mount.json ]; then /usr/bin/python /data/vi-remote/bin/sms-runtime.py --restore || exit 1; fi\n'
  text=text.replace(anchor,anchor+hook)
  if name=='connect.sh':text+='\nif [ -f /data/vi-remote/sms-enabled ]; then /usr/bin/python /data/vi-remote/bin/sms-runtime.py || exit 1; fi\n'
  updates[name]=text.encode()
 for name,content in updates.items():
  backup=root+'/'+name+'.before-vi-sms'
  if not os.path.exists(backup):write(backup,read(root+'/'+name),0o700)
  write(root+'/'+name,content,0o755)
 manifest=root+'/SHA256SUMS'
 if updates and os.path.isfile(manifest):
  lines=read(manifest).decode().splitlines();out=[]
  for line in lines:
   parts=line.split()
   if len(parts)==2 and parts[1].lstrip('./') in updates:
    name=parts[1].lstrip('./');line=digest(updates[name])+'  '+parts[1]
   out.append(line)
  write(manifest,('\n'.join(out)+'\n').encode())
if __name__=='__main__':
 try:
  lock=open('/run/vi-remote-sms.lock','w');fcntl.flock(lock,fcntl.LOCK_EX)
  if len(sys.argv)>1 and sys.argv[1]=='--antenna-hooks':antenna_hooks()
  elif len(sys.argv)>1 and sys.argv[1]=='--restore':restore()
  else:prepare()
 except Exception as e:print('ERROR: SMS preparation:',str(e));sys.exit(1)
