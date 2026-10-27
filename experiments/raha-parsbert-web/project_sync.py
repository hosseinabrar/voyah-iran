"""Event-driven, coalescing export after feedback; never builds or commits."""
import fcntl
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

def digest(path):
    with path.open('rb') as file:
        return hashlib.file_digest(file,'sha256').hexdigest() if hasattr(hashlib,'file_digest') else hashlib.sha256(file.read()).hexdigest()

class ProjectSync:
    def __init__(self,root,checkpoint):
        self.root=Path(root);self.checkpoint=Path(checkpoint)
        self.state_lock=threading.Lock();self.event=threading.Event();self.stopping=threading.Event()
        self.state={'state':'checking','error':None}
        self.directory=self.root/'artifacts/project-sync';self.directory.mkdir(parents=True,exist_ok=True)
        self.thread=threading.Thread(target=self._loop,daemon=True,name='raha-project-sync')

    def start(self):self.thread.start();self.request()
    def request(self):
        with self.state_lock:
            if self.state['state'] in ['synced','error']:self.state.update(state='pending',error=None)
        self.event.set()
    def close(self):self.stopping.set();self.event.set()
    def status(self):
        with self.state_lock:return dict(self.state)
    def update(self,**values):
        with self.state_lock:self.state.update(values)

    def installed(self):
        path=self.root.parents[1]/'voyah-iran/raha-runtime/src/main/assets/raha-intent/manifest.json'
        try:
            manifest=json.loads(path.read_text())
            return manifest.get('feedback_sha256') if manifest.get('snapshot_loader_version')==2 else None
        except (OSError,ValueError):return None

    def run_script(self,name,env,log,*args):
        subprocess.run([sys.executable,str(self.root/name),*args],cwd=self.root,env=env,
                       stdout=log,stderr=subprocess.STDOUT,check=True,timeout=600)

    def sync_once(self):
        wanted=digest(self.checkpoint)
        if self.installed()==wanted:
            self.update(state='synced',error=None,feedback_sha256=wanted)
            return
        self.update(state='exporting',error=None)
        # A unique immutable workspace prevents a later feedback update from
        # changing the model being verified. Only one worker publishes at a time.
        with (self.directory/'worker.lock').open('w') as lease:
            fcntl.flock(lease,fcntl.LOCK_EX)
            with tempfile.TemporaryDirectory(prefix='snapshot-',dir=self.directory) as temporary:
                job=Path(temporary);snapshot=job/'source.safetensors'
                shutil.copyfile(self.checkpoint,snapshot)
                captured=digest(snapshot)
                env=dict(os.environ,RAHA_EXPORT_DIR=str(job),RAHA_FEEDBACK_SOURCE=str(snapshot))
                with (self.directory/'last-run.log').open('w') as log:
                    self.run_script('export_compact_android.py',env,log)
                    self.update(state='verifying')
                    try:
                        self.run_script('verify_android_export.py',env,log,'model.compact.onnx')
                    finally:
                        # Keep failure evidence too, before the temporary snapshot is removed.
                        report=job/'parity-report.json'
                        if report.exists():
                            shutil.copyfile(report,self.directory/'last-parity-report.json')
                    if digest(self.checkpoint)!=captured:
                        self.update(state='pending');self.request();return
                    if self.stopping.is_set():return
                    self.update(state='installing')
                    self.run_script('install_android_assets.py',env,log)
                    self.run_script('export_guard_fixtures.py',env,log)
                    from install_android_assets import atomic_copy
                    atomic_copy(job/'parity-fixtures.json',self.root.parents[1]/'voyah-iran/raha-core/src/test/resources/intent-tokenizer-parity.json')
                    shutil.copyfile(job/'parity-report.json',self.directory/'last-parity-report.json')
                self.update(state='synced',error=None,feedback_sha256=captured)
                if digest(self.checkpoint)!=captured:self.request()

    def _loop(self):
        while not self.stopping.is_set():
            self.event.wait();self.event.clear()
            if self.stopping.wait(2):break
            try:self.sync_once()
            except Exception as error:
                self.update(state='error',error=str(error))
                # Keep feedback durable; bounded-rate retries and startup check
                # recover failures without blocking microphone or training.
                if self.stopping.wait(30):break
                self.request()
