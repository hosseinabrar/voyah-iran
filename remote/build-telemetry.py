"""Build shared telemetry binaries; no connected-device or deployment actions."""
from pathlib import Path
import hashlib,os,subprocess,sys
root=Path(__file__).resolve().parent
box=root/'TBox/TBox-remote'
subprocess.run([sys.executable,str(box/'native/package-battery-runtime.py')],check=True)
for src,name in [('telemetry-agent','vi-remote-agent'),('telemetry-collector','vi-remote-collector')]:
 subprocess.run(['go','build','-trimpath','-ldflags=-s -w','-o',str(box/'telemetry-payload'/name),'./TBox/TBox-remote/'+src],cwd=root,env={**os.environ,'CGO_ENABLED':'0','GOOS':'linux','GOARCH':'arm64'},check=True)
for system,arch in [('darwin','arm64'),('darwin','amd64'),('windows','amd64')]:
 name=f'telemetry-install-{system}-{arch}'+('.exe' if system=='windows' else '')
 subprocess.run(['go','build','-trimpath','-ldflags=-s -w','-o',str(box/'dist'/name),'./TBox/TBox-remote/telemetry-installer'],cwd=root,env={**os.environ,'CGO_ENABLED':'0','GOOS':system,'GOARCH':arch},check=True)
payload=box/'telemetry-payload'
(payload/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(payload.iterdir()) if p.name!='SHA256SUMS'))

# Release PWA excludes full-resolution artwork sources and authoring tools.
import shutil
web=root/'client/vi-remote'
destination=root/'server/dist/vi-remote'
if destination.exists(): shutil.rmtree(destination)
shutil.copytree(web,destination,ignore=shutil.ignore_patterns('artwork','tests','README*'))
subprocess.run(['go','build','-trimpath','-ldflags=-s -w','-o',str(root/'server/dist/remote-gateway'),'./server'],cwd=root,env={**os.environ,'CGO_ENABLED':'0','GOOS':'linux','GOARCH':'amd64'},check=True)
