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
        self.verification={'state':'not_run'}
        self.processes=set()
        self.directory=self.root/'artifacts/project-sync';self.directory.mkdir(parents=True,exist_ok=True)
        self.thread=threading.Thread(target=self._loop,daemon=True,name='raha-project-sync')

    def start(self):self.thread.start();self.request()
    def request(self):
        with self.state_lock:
            if self.state['state'] in ['synced','error']:self.state.update(state='pending',error=None)
        self.event.set()
    def close(self):
        self.stopping.set();self.event.set()
        with self.state_lock:
            for process in list(self.processes):
                if process.poll() is None:process.terminate()
    def status(self):
        with self.state_lock:
            result=dict(self.state,verification=dict(self.verification))
        if result['verification'].get('feedback_sha256'):
            result['verification']['outdated']=result['verification']['feedback_sha256']!=digest(self.checkpoint)
        return result
    def update(self,**values):
        with self.state_lock:self.state.update(values)

    def installed(self):
        path=self.root.parents[1]/'voyah-iran/raha-runtime/src/main/assets/raha-intent/manifest.json'
        try:
            manifest=json.loads(path.read_text())
            return manifest.get('feedback_sha256') if manifest.get('snapshot_loader_version')==2 else None
        except (OSError,ValueError):return None

    def run_script(self,name,env,log,*args):
        with self.state_lock:
            if self.stopping.is_set():raise RuntimeError('Worker stopped')
            process=subprocess.Popen([sys.executable,str(self.root/name),*args],cwd=self.root,env=env,stdout=log,stderr=subprocess.STDOUT)
            self.processes.add(process)
        try:
            code=process.wait(timeout=3600 if name=='verify_android_export.py' else 600)
            if code:raise RuntimeError(f'{name} exited with {code}')
        finally:
            if process.poll() is None:
                process.kill();process.wait()
            with self.state_lock:self.processes.discard(process)

    def request_verification(self):
        with self.state_lock:
            if self.verification['state']=='running':return False
            self.verification={'state':'running'}
        threading.Thread(target=self._verify,name='raha-manual-verification',daemon=True).start()
        return True

    def _verify(self):
        report=None;captured=None
        try:
            with tempfile.TemporaryDirectory(prefix='manual-',dir=self.directory) as temporary:
                job=Path(temporary);snapshot=job/'source.safetensors'
                shutil.copyfile(self.checkpoint,snapshot);captured=digest(snapshot)
                env=dict(os.environ,RAHA_EXPORT_DIR=str(job),RAHA_FEEDBACK_SOURCE=str(snapshot))
                with (self.directory/'manual-verification.log').open('w') as log:
                    self.run_script('export_compact_android.py',env,log)
                    try:self.run_script('verify_android_export.py',env,log,'model.compact.onnx')
                    finally:
                        if (job/'parity-report.json').exists():
                            report=json.loads((job/'parity-report.json').read_text())
                            shutil.copyfile(job/'parity-report.json',self.directory/'last-manual-parity-report.json')
                    # Regression fixtures belong to the exact current snapshot only.
                    if digest(self.checkpoint)==captured and not self.stopping.is_set():
                        self.run_script('export_guard_fixtures.py',env,log)
                        from install_android_assets import atomic_copy
                        atomic_copy(job/'parity-fixtures.json',self.root.parents[1]/'voyah-iran/raha-core/src/test/resources/intent-tokenizer-parity.json')
        except Exception as error:
            result={'state':'mismatch' if report and report.get('failures') else 'error','error':str(error)}
        else:result={'state':'passed'}
        if report:result.update(count=report['count'],failure_count=len(report.get('failures',[])),failures=report.get('failures',[])[:20])
        result['feedback_sha256']=captured
        with self.state_lock:self.verification=result

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
                    if digest(self.checkpoint)!=captured:
                        self.update(state='pending');self.request();return
                    if self.stopping.is_set():return
                    self.update(state='installing')
                    self.run_script('install_android_assets.py',dict(env,RAHA_ASSUME_PARITY='1'),log)
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
