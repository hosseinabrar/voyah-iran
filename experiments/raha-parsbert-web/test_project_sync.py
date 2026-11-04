import tempfile,unittest,json,threading
from pathlib import Path
from unittest.mock import patch
from project_sync import ProjectSync,digest

class SyncTests(unittest.TestCase):
    def test_matching_snapshot_skips_export(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'experiments/web';root.mkdir(parents=True)
            checkpoint=root/'feedback';checkpoint.write_bytes(b'feedback')
            sync=ProjectSync(root,checkpoint)
            with patch.object(sync,'installed',return_value=digest(checkpoint)),patch.object(sync,'run_script') as run:
                sync.sync_once();run.assert_not_called()
            self.assertEqual(sync.status()['state'],'synced')

    def test_new_feedback_during_export_never_publishes_stale_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'experiments/web';root.mkdir(parents=True)
            checkpoint=root/'feedback';checkpoint.write_bytes(b'old')
            sync=ProjectSync(root,checkpoint);calls=[]
            def run(name,env,log,*args):
                calls.append(name)
                if name=='export_compact_android.py':checkpoint.write_bytes(b'new')
            with patch.object(sync,'installed',return_value=None),patch.object(sync,'run_script',side_effect=run):
                sync.sync_once()
            self.assertNotIn('install_android_assets.py',calls)
            self.assertTrue(sync.event.is_set())
            self.assertEqual(sync.status()['state'],'pending')

    def test_automatic_sync_does_not_verify(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'experiments/web';root.mkdir(parents=True)
            checkpoint=root/'feedback';checkpoint.write_bytes(b'new')
            sync=ProjectSync(root,checkpoint);calls=[]
            def run(name,env,*args):
                calls.append(name)
                if name=='install_android_assets.py':self.assertEqual(env['RAHA_ASSUME_PARITY'],'1')
            with patch.object(sync,'installed',return_value=None),patch.object(sync,'run_script',side_effect=run):sync.sync_once()
            self.assertEqual(calls,['export_compact_android.py','install_android_assets.py'])
            self.assertEqual(sync.status()['state'],'synced')

    def test_manual_mismatch_is_reported_without_retry(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'experiments/web';root.mkdir(parents=True)
            checkpoint=root/'feedback';checkpoint.write_bytes(b'old')
            sync=ProjectSync(root,checkpoint)
            def run(name,env,*args):
                if name=='verify_android_export.py':
                    (Path(env['RAHA_EXPORT_DIR'])/'parity-report.json').write_text(json.dumps({'count':1,'failures':[{'text':'sample','expected':'A','actual':'B'}]}))
                    checkpoint.write_bytes(b'new')
                    raise RuntimeError('mismatch')
            with patch.object(sync,'run_script',side_effect=run):sync._verify()
            v=sync.status()['verification']
            self.assertEqual(v['state'],'mismatch');self.assertEqual(v['failure_count'],1)
            self.assertTrue(v['outdated']);self.assertFalse(sync.event.is_set())

    def test_double_click_does_not_start_two_jobs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);checkpoint=root/'feedback';checkpoint.write_bytes(b'new')
            sync=ProjectSync(root,checkpoint)
            with patch('project_sync.threading.Thread') as thread:
                self.assertTrue(sync.request_verification());self.assertFalse(sync.request_verification())
                thread.assert_called_once()

if __name__=='__main__':unittest.main()
