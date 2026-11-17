"""Deploy only the remote service. Password is entered interactively, never saved."""
from pathlib import Path
import getpass,paramiko
ROOT=Path(__file__).resolve().parent

def deploy(password):
 c=paramiko.SSHClient();c.load_system_host_keys();c.connect('193.242.125.144',username='root',password=password,timeout=15,allow_agent=False,look_for_keys=False)
 def run(command):
  _,out,err=c.exec_command(command);stdout=out.read().decode();stderr=err.read().decode();code=out.channel.recv_exit_status()
  if code:raise RuntimeError(stderr or stdout or str(code))
  return stdout
 run('test -s /etc/letsencrypt/live/remote.voyah-iran.ir/fullchain.pem && test -s /etc/letsencrypt/live/remote.voyah-iran.ir/privkey.pem')
 before=run("sha256sum /etc/nginx/sites-available/voyah-iran; curl -sS -o /dev/null -w '%{http_code}' -H 'Host: voyah-iran.ir' http://127.0.0.1/")
 run('id voyah-remote >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin voyah-remote')
 run('install -d -m 755 /opt/voyah-remote; install -d -m 750 -o root -g voyah-remote /etc/voyah-remote')
 s=c.open_sftp()
 for source,destination,mode in [('dist/remote-gateway','/opt/voyah-remote/remote-gateway.new',0o755),('credentials.local.json','/etc/voyah-remote/credentials.json',0o640),('remote-gateway.service','/etc/systemd/system/voyah-remote.service',0o644),('nginx-remote.conf','/etc/nginx/sites-available/voyah-remote',0o644)]:
  s.put(str(ROOT/source),destination);s.chmod(destination,mode)
 # Only this remote subdomain serves the PWA; do not copy source artwork.
 web=ROOT/'dist/vi-remote'
 if not web.is_dir(): raise RuntimeError('Run remote/build-telemetry.py first')
 run('install -d -m 755 /opt/voyah-remote/vi-remote')
 for source in sorted(web.rglob('*')):
  destination='/opt/voyah-remote/vi-remote/'+source.relative_to(web).as_posix()
  if source.is_dir():
   try:s.mkdir(destination)
   except IOError:pass
  else:s.put(str(source),destination);s.chmod(destination,0o644)
 s.close()
 run('chown root:voyah-remote /etc/voyah-remote/credentials.json; mv /opt/voyah-remote/remote-gateway.new /opt/voyah-remote/remote-gateway; systemctl daemon-reload; systemctl enable --now voyah-remote.service; systemctl restart voyah-remote.service')
 run('ln -sfn /etc/nginx/sites-available/voyah-remote /etc/nginx/sites-enabled/voyah-remote')
 try:run('nginx -t')
 except Exception:
  run('unlink /etc/nginx/sites-enabled/voyah-remote');raise
 run('systemctl reload nginx')
 after=run("sha256sum /etc/nginx/sites-available/voyah-iran; curl -sS -o /dev/null -w '%{http_code}' -H 'Host: voyah-iran.ir' http://127.0.0.1/")
 if before!=after:raise RuntimeError('Subscription config/status differs; investigate immediately')
 print('DONE: Remote gateway installed; subscription config and HTTP status unchanged')
 print(run("curl --resolve remote.voyah-iran.ir:443:127.0.0.1 -fsS https://remote.voyah-iran.ir/healthz"))
 c.close()
if __name__=='__main__':deploy(getpass.getpass('SSH password: '))
