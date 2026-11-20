"""Publish the verified PWA only; preserve gateway, credentials and subscriptions."""
from pathlib import Path
import datetime,getpass,hashlib,paramiko,shlex
ROOT=Path(__file__).resolve().parent
# Deploy the complete built app inventory, including optimized artwork/fonts.
WEB=ROOT/'dist/vi-remote'
FILES=sorted(p.relative_to(WEB).as_posix() for p in WEB.rglob('*') if p.is_file() and p.name not in ('sw.js','precache.js'))+['precache.js','sw.js']
def deploy(password):
 client=paramiko.SSHClient();client.load_system_host_keys()
 client.connect('193.242.125.144',username='root',password=password,timeout=15,allow_agent=False,look_for_keys=False)
 def run(command):
  _,out,err=client.exec_command(command,timeout=30);stdout=out.read().decode();stderr=err.read().decode();status=out.channel.recv_exit_status()
  if status:raise RuntimeError(stderr or stdout)
  return stdout
 web='/opt/voyah-remote/vi-remote'
 backup='/root/vi-remote-sms-web-backup-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S')
 before=run('systemctl is-active voyah-remote.service; sha256sum /etc/voyah-remote/credentials.json /etc/nginx/sites-available/voyah-remote /etc/nginx/sites-available/voyah-iran')
 run('mkdir -m 700 '+shlex.quote(backup))
 sftp=client.open_sftp();changed=[];existing=[]
 try:
  for name in FILES:
   target=web+'/'+name;source=ROOT/'dist/vi-remote'/name
   if not source.is_file():raise RuntimeError('Missing built asset '+name)
   try:sftp.stat(target);present=True
   except FileNotFoundError:present=False
   except IOError as e:
    if e.errno==2:present=False
    else:raise
   if present:
    run('mkdir -p '+shlex.quote(str(Path(backup+'/'+name).parent))+' && cp -p '+shlex.quote(target)+' '+shlex.quote(backup+'/'+name));existing.append(name)
   sftp.put(str(source),target+'.new');sftp.chmod(target+'.new',0o644)
   expected=hashlib.sha256(source.read_bytes()).hexdigest()
   if run('sha256sum '+shlex.quote(target+'.new')).split()[0]!=expected:raise RuntimeError('Transfer mismatch: '+name)
   run('mv '+shlex.quote(target+'.new')+' '+shlex.quote(target));changed.append(name)
  after=run('systemctl is-active voyah-remote.service; sha256sum /etc/voyah-remote/credentials.json /etc/nginx/sites-available/voyah-remote /etc/nginx/sites-available/voyah-iran')
  if before!=after:raise RuntimeError('Unrelated service/config changed')
  for name in FILES:
   h=run('curl --resolve remote.voyah-iran.ir:443:127.0.0.1 -fsS '+shlex.quote('https://remote.voyah-iran.ir/'+name)+' | sha256sum').split()[0]
   if h!=hashlib.sha256((ROOT/'dist/vi-remote'/name).read_bytes()).hexdigest():raise RuntimeError('HTTPS asset mismatch '+name)
  print('DONE: PWA files published and verified over HTTPS. Backup: '+backup)
 except Exception:
  for name in reversed(changed):
   target=web+'/'+name
   if name in existing:run('cp -p '+shlex.quote(backup+'/'+name)+' '+shlex.quote(target))
   else:run('rm -f '+shlex.quote(target))
  raise
 finally:sftp.close();client.close()
if __name__=='__main__':deploy(getpass.getpass('SSH password: '))
